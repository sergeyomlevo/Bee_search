package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Version 15 -> 16 evidence: existing GPX rows survive the deletion journal migration. */
@RunWith(AndroidJUnit4::class)
class GpxDeletionMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), BeeSearchDatabase::class.java,
    )

    @Test
    fun migration15To16PreservesExistingGpxRowsAndInstallsDeletionGuards() {
        val name = "gpx_deletion_migration_${UUID.randomUUID()}"
        val territory = "00000000-0000-0000-0000-000000009901"
        val observer = "00000000-0000-0000-0000-000000009902"
        val importId = "00000000-0000-0000-0000-000000009903"
        val originalId = "00000000-0000-0000-0000-000000009904"
        val request = "00000000-0000-0000-0000-000000009905"
        context.deleteDatabase(name)
        helper.createDatabase(name, 15).use { db ->
            GpxPersistenceSchema.installTriggers(db)
            db.execSQL("INSERT INTO territories VALUES(?,?,?,?,?,?,?)", arrayOf(territory, "D16", "Delete", "R", "D", 1L, 1L))
            db.execSQL("INSERT INTO observers VALUES(?,?,?,?,?,?,?,?)", arrayOf(observer, "D16-O", "Delete", "Test", null, null, 1L, 1L))
            db.execSQL(
                "INSERT INTO gpx_imports VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(importId, territory, observer, originalId, "a".repeat(64), 12L, "legacy.gpx", "1.1", "GPX_1_1_STAGE_1", 1L, "fixture", null, null, null, "READY", request, 0L, null, null, null, null, 0L, null, null, null, null),
            )
        }
        helper.runMigrationsAndValidate(name, 16, true, MIGRATION_15_16).use { db ->
            db.query("SELECT display_name, publication_state FROM gpx_imports WHERE id = ?", arrayOf(importId)).use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy.gpx", it.getString(0))
                assertEquals("READY", it.getString(1))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='gpx_deletions'").use { assertTrue(it.moveToFirst()) }
            db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'gpx_deletion_%'").use {
                var count = 0
                while (it.moveToNext()) count++
                assertEquals(GpxDeletionSchema.triggers.size, count)
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            GpxPersistenceSchema.verify(db)
        }
        context.deleteDatabase(name)
    }
}
