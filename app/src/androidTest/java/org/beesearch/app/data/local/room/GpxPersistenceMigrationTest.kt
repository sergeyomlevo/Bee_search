package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migration evidence: legacy content and physical schema are compared independently. */
@RunWith(AndroidJUnit4::class)
class GpxPersistenceMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BeeSearchDatabase::class.java)

    @Test fun migration14To15PreservesLegacyRowsRelationsAndSchema() {
        val name = "gpx_migration_stage1d"
        context.deleteDatabase(name)
        val ids = seedLegacy14(name)
        val migrated = helper.runMigrationsAndValidate(name, 15, true, MIGRATION_14_15)
        assertLegacyRows(migrated, ids)
        migrated.query("PRAGMA foreign_key_check").use { assertTrue(!it.moveToNext()) }
        assertEquals(setOf("gpx_imports", "gpx_source_tracks", "gpx_track_segments", "gpx_track_samples", "gpx_import_jobs"), tableNames(migrated).filter { it.startsWith("gpx_") }.toSet())
        GpxPersistenceSchema.verify(migrated)
        migrated.close(); context.deleteDatabase(name)
    }

    @Test fun freshAndMigratedSchemaParityIncludesTablesForeignKeysIndicesAndTriggers() {
        val name = "gpx_migration_parity"
        context.deleteDatabase(name)
        seedLegacy14(name)
        val migrated = helper.runMigrationsAndValidate(name, 16, true, MIGRATION_14_15, MIGRATION_15_16)
        val fresh = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).addCallback(GpxPersistenceSchema.callback).build()
        val migratedSnapshot = schemaSnapshot(migrated)
        val freshSnapshot = schemaSnapshot(fresh.openHelper.writableDatabase)
        assertEquals(freshSnapshot.tables, migratedSnapshot.tables)
        assertEquals(freshSnapshot.foreignKeys, migratedSnapshot.foreignKeys)
        assertEquals(freshSnapshot.indices, migratedSnapshot.indices)
        assertEquals(freshSnapshot.triggers, migratedSnapshot.triggers)
        fresh.close(); migrated.close(); context.deleteDatabase(name)
    }

    private fun seedLegacy14(name: String): LegacyIds {
        val territory = "00000000-0000-0000-0000-000000000001"
        val observer = "00000000-0000-0000-0000-000000000002"
        val point = "00000000-0000-0000-0000-000000000003"
        val bee = "00000000-0000-0000-0000-000000000004"
        val flight = "00000000-0000-0000-0000-000000000005"
        val now = 1_760_000_000_000L
        helper.createDatabase(name, 14).apply {
            execSQL("INSERT INTO territories VALUES(?,?,?,?,?,?,?)", arrayOf(territory, "M14", "Legacy", "R", "D", now, now))
            execSQL("INSERT INTO observers VALUES(?,?,?,?,?,?,?,?)", arrayOf(observer, "OBS14", "Legacy", "Observer", null, null, now, now))
            execSQL("INSERT INTO observation_points VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf("2026-10-15", point, territory, observer, 2026, 1, "BEES_FOUND", "P14", 56.1, 42.7, 56.1, 42.7, 4.0, now, null, null, "legacy"))
            execSQL("INSERT INTO physical_object_sequences VALUES(?,?,?)", arrayOf(territory, "HOLLOW", 7))
            execSQL("INSERT INTO bees VALUES(?,?,?,?,?,?)", arrayOf(bee, point, "WHITE", "THORAX", now, null))
            execSQL("INSERT INTO flight_cycles VALUES(?,?,?,?,?,?,?,?,?,?,?)", arrayOf(flight, bee, 1, now, now + 1000, null, 0, 1, 1, now, now))
            val snapshot = tableNames(this).filterNot { it == "room_master_table" }.associateWith { table -> rows(this, table) }
            close()
            return LegacyIds(territory, observer, point, bee, flight, snapshot)
        }
        error("legacy database was not created")
    }

    private fun assertLegacyRows(db: SupportSQLiteDatabase, ids: LegacyIds) {
        ids.snapshot.forEach { (table, expected) -> assertEquals(expected, rows(db, table)) }
        db.query("SELECT observer_id FROM observation_points WHERE id = ?", arrayOf(ids.point)).use { assertTrue(it.moveToFirst()); assertEquals(ids.observer, it.getString(0)) }
        db.query("SELECT observation_point_id FROM bees WHERE id = ?", arrayOf(ids.bee)).use { assertTrue(it.moveToFirst()); assertEquals(ids.point, it.getString(0)) }
        db.query("SELECT bee_id FROM flight_cycles WHERE id = ?", arrayOf(ids.flight)).use { assertTrue(it.moveToFirst()); assertEquals(ids.bee, it.getString(0)) }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<String> = buildList { db.query("SELECT * FROM $table ORDER BY rowid").use { while (it.moveToNext()) add((0 until it.columnCount).joinToString("|") { n -> it.getString(n) ?: "NULL" }) } }
    private fun tableNames(db: SupportSQLiteDatabase): List<String> = buildList { db.query("SELECT name FROM sqlite_master WHERE type='table' AND name != 'room_master_table'").use { while (it.moveToNext()) add(it.getString(0)) } }
    private data class SchemaSnapshot(val tables: Map<String, List<String>>, val foreignKeys: Map<String, List<String>>, val indices: Map<String, List<String>>, val triggers: Map<String, String>)
    private fun schemaSnapshot(db: SupportSQLiteDatabase): SchemaSnapshot {
        val tables = tableNames(db).associateWith { pragmaRows(db, "table_info('$it')") }
        val foreignKeys = tables.keys.associateWith { pragmaRows(db, "foreign_key_list('$it')") }
        val indices = tables.keys.associateWith { table ->
            pragmaRows(db, "index_list('$table')").flatMap { row ->
                val indexName = row.split('|').getOrNull(1) ?: return@flatMap listOf(row)
                listOf(row) + pragmaRows(db, "index_info('$indexName')")
            }
        }
        return SchemaSnapshot(tables, foreignKeys, indices, triggerRows(db))
    }
    private fun pragmaRows(db: SupportSQLiteDatabase, pragma: String): List<String> = buildList { db.query("PRAGMA $pragma").use { while (it.moveToNext()) add((0 until it.columnCount).joinToString("|") { n -> it.getString(n) ?: "NULL" }) } }
    private fun triggerRows(db: SupportSQLiteDatabase): Map<String, String> = buildMap { db.query("SELECT name, sql FROM sqlite_master WHERE type='trigger' AND name LIKE 'gpx_validate_%'").use { while (it.moveToNext()) put(it.getString(0), it.getString(1)) } }
    private data class LegacyIds(val territory: String, val observer: String, val point: String, val bee: String, val flight: String, val snapshot: Map<String, List<String>>)
}
