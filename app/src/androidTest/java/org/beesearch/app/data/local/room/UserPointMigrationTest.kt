package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPointMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BeeSearchDatabase::class.java)

    @Test
    fun migration16To17PreservesAllLegacyRowsAndGpxGuards() {
        val name = "user_point_migration_${UUID.randomUUID()}"
        val seed = seedLegacy16(name)
        val before = seed.before

        helper.runMigrationsAndValidate(name, 17, true, MIGRATION_16_17).use { db ->
            val after = snapshotTables(db)
            before.forEach { (table, rows) -> assertEquals("legacy table $table changed", rows, after[table]) }
            GpxPersistenceSchema.verify(db)
            GpxDeletionSchema.verify(db)
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('user_points','user_point_photos') ORDER BY name").use {
                assertTrue(it.moveToFirst())
                assertEquals("user_point_photos", it.getString(0))
                assertTrue(it.moveToNext())
                assertEquals("user_points", it.getString(0))
            }
            db.query("SELECT id, display_name, publication_state FROM gpx_imports WHERE id = ?", arrayOf(seed.ids.gpxImport)).use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy.gpx", it.getString(1))
                assertEquals("READY", it.getString(2))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        context.deleteDatabase(name)
    }

    private fun seedLegacy16(name: String): LegacySeed {
        val territory = UUID.randomUUID().toString()
        val observer = UUID.randomUUID().toString()
        val physical = UUID.randomUUID().toString()
        val importId = UUID.randomUUID().toString()
        val original = UUID.randomUUID().toString()
        val request = UUID.randomUUID().toString()
        val before = helper.createDatabase(name, 16).use { db ->
            GpxPersistenceSchema.installTriggers(db)
            GpxDeletionSchema.install(db)
            db.execSQL("INSERT INTO territories VALUES(?,?,?,?,?,?,?)", arrayOf(territory, "T16", "Legacy", "R", "D", 1L, 1L))
            db.execSQL("INSERT INTO observers VALUES(?,?,?,?,?,?,?,?)", arrayOf(observer, "O16", "Legacy", "Observer", null, null, 1L, 1L))
            db.execSQL("INSERT INTO physical_objects (id, territory_id, object_type, sequence_number, latitude, longitude, created_at, creator_observer_id) VALUES(?,?,?,?,?,?,?,?)", arrayOf(physical, territory, "HOLLOW", 1, 56.8, 60.6, 1L, observer))
            db.execSQL("INSERT INTO gpx_imports (id, territory_id, importing_observer_id, original_object_id, original_sha256, original_byte_length, display_name, gpx_version, parser_profile_version, imported_at_epoch_ms, creator, publication_state, request_uuid, unsupported_content_count, unsupported_time_count) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(importId, territory, observer, original, "a".repeat(64), 12L, "legacy.gpx", "1.1", "GPX_1_1_STAGE_1", 1L, "fixture", "READY", request, 0L, 0L))
            snapshotTables(db)
        }
        return LegacySeed(LegacyIds(territory, observer, physical, importId), before)
    }

    private fun snapshotTables(db: SupportSQLiteDatabase): Map<String, List<List<Any?>>> {
        val names = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('room_master_table') ORDER BY name").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names.associateWith { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                val rows = mutableListOf<List<Any?>>()
                while (cursor.moveToNext()) rows += (0 until cursor.columnCount).map { index ->
                    when (cursor.getType(index)) {
                        0 -> null
                        1 -> cursor.getLong(index)
                        2 -> cursor.getDouble(index)
                        3 -> cursor.getString(index)
                        else -> cursor.getBlob(index).toList()
                    }
                }
                rows
            }
        }
    }

    private data class LegacyIds(val territory: String, val observer: String, val physical: String, val gpxImport: String)
    private data class LegacySeed(val ids: LegacyIds, val before: Map<String, List<List<Any?>>>)
}
