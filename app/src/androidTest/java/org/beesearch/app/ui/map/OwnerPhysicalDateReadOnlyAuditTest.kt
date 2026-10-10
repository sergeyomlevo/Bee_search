package org.beesearch.app.ui.map

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit owner-requested audit: only two physical dates and two point aggregates, no writes. */
@RunWith(AndroidJUnit4::class)
class OwnerPhysicalDateReadOnlyAuditTest {
    @Test
    fun readOnlyRequestedRecords() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ownerPhysicalDateAudit") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = instrumentation.targetContext.getDatabasePath("bee_search.db")
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT id, object_type, sequence_number, created_at, fixation_date, fixation_at, updated_at FROM physical_objects " +
                    "WHERE (object_type = 'HOLLOW' AND sequence_number = 10) " +
                    "OR (object_type = 'LOG_HIVE' AND sequence_number = 1)", null,
            ).use { rows ->
                while (rows.moveToNext()) {
                    val record = (0 until rows.columnCount).joinToString("; ") { index ->
                        "${rows.getColumnName(index)}=${if (rows.isNull(index)) "NULL" else rows.getString(index)}"
                    }
                    instrumentation.sendStatus(0, Bundle().apply { putString("owner_date_audit", record) })
                }
            }
            db.rawQuery(
                "SELECT p.id, p.point_number, COUNT(DISTINCT b.id) bee_count, COUNT(c.id) total_cycle_count " +
                    "FROM observation_points p LEFT JOIN bees b ON b.observation_point_id = p.id " +
                    "LEFT JOIN flight_cycles c ON c.bee_id = b.id " +
                    "WHERE p.point_number IN (16, 22) GROUP BY p.id", null,
            ).use { rows ->
                while (rows.moveToNext()) {
                    val record = (0 until rows.columnCount).joinToString("; ") { index ->
                        "${rows.getColumnName(index)}=${rows.getString(index)}"
                    }
                    instrumentation.sendStatus(0, Bundle().apply { putString("owner_point_audit", record) })
                }
            }
        }
    }
}
