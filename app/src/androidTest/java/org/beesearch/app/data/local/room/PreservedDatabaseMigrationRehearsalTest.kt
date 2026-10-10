package org.beesearch.app.data.local.room

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Supply DB/WAL/SHM under cache/migration-rehearsal-source-<fixture UUID> externally.
 * Requires -e migrationRehearsal true -e migrationFixtureId <fixture UUID>.
 * Only a fresh working copy is opened; no private fixture is bundled with this test.
 */
@RunWith(AndroidJUnit4::class)
class PreservedDatabaseMigrationRehearsalTest {
    @Test fun preservedRoomTwelveOpensThroughProductionBuilderAndMigratesToThirteen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("opt-in preserved database migration rehearsal", args.getString("migrationRehearsal") == "true")
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals(29, Build.VERSION.SDK_INT)
        assertEquals("Android SDK built for x86_64", Build.MODEL)
        val qemu = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        assertEquals("1", qemu.inputStream.bufferedReader().use { it.readText().trim() })
        assertEquals(0, qemu.waitFor())
        val fixtureId = UUID.fromString(args.getString("migrationFixtureId")!!).toString()
        // Android may alias /data/user/0 to /data/data; resolve the sandbox root first.
        val cacheRoot = context.cacheDir.canonicalFile
        val source = File(cacheRoot, "migration-rehearsal-source-$fixtureId")
        assertEquals(source.absoluteFile, source.canonicalFile)
        val names = listOf("bee_search.db", "bee_search.db-wal", "bee_search.db-shm")
        val originals = names.map { File(source, it).also { file ->
            assertTrue(file.isFile)
            assertEquals(file.absoluteFile, file.canonicalFile)
        } }
        val originalHashes = originals.map(::fileHash)
        val directory = File(cacheRoot, "migration-rehearsal-work-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        originals.forEach { it.copyTo(File(directory, it.name), overwrite = false) }
        assertEquals(originalHashes, names.map { fileHash(File(directory, it)) })
        val file = File(directory, "bee_search.db")
        val baseline = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(12, db.version)
            db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
            snapshot({ sql -> db.rawQuery(sql, null) })
        }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File {
                check(name == "bee_search.db")
                return file
            }
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                context.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                context.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, errorHandler)
        }
        val room = BeeSearchDatabase.create(isolated)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(file.absolutePath, db.path)
            assertEquals(14, db.version)
            db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.query("SELECT COUNT(*) FROM physical_objects WHERE fixation_date IS NOT NULL").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            val after = snapshot({ sql -> db.query(sql) }, baseline.mapValues { it.value.columns })
            assertEquals("Original domain rows, IDs and relationship columns changed", baseline, after)
            println("MIGRATION REHEARSAL PASS version=12->${db.version} zone=${ZoneId.systemDefault()} integrity=ok foreignKeys=ok fixationDates=NULL domainBaseline=preserved source=unchanged")
        } finally {
            room.close()
            assertEquals("Supplied source DB/WAL/SHM changed", originalHashes, originals.map(::fileHash))
        }
    }

    private data class TableBaseline(val columns: List<String>, val rows: Int, val digest: String)

    private fun snapshot(query: (String) -> Cursor, originalColumns: Map<String, List<String>>? = null): Map<String, TableBaseline> {
        // Room's schema identity and Android's device-locale metadata may legitimately change.
        val tables = query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata') ORDER BY name").use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
        return tables.associateWith { table ->
            fun quote(name: String) = "\"${name.replace("\"", "\"\"")}\""
            val columns = originalColumns?.getValue(table) ?: query("PRAGMA table_info(${quote(table)})").use {
                buildList { while (it.moveToNext()) add(it.getString(1)) }
            }
            val rowHashes = query("SELECT ${columns.joinToString(",", transform = ::quote)} FROM ${quote(table)}").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val hash = MessageDigest.getInstance("SHA-256")
                        columns.indices.forEach { i ->
                            val type = cursor.getType(i)
                            val value = when (type) {
                                Cursor.FIELD_TYPE_NULL -> byteArrayOf()
                                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                                else -> cursor.getString(i).toByteArray(Charsets.UTF_8)
                            }
                            hash.update("$type:${value.size}:".toByteArray(Charsets.UTF_8))
                            hash.update(value)
                        }
                        add(hash.digest().joinToString("") { "%02x".format(it) })
                    }
                }
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(rowHashes.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
            TableBaseline(columns, rowHashes.size, hash.joinToString("") { "%02x".format(it) })
        }
    }

    private fun fileHash(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val length = stream.read(buffer)
                if (length < 0) break
                hash.update(buffer, 0, length)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
