package org.beesearch.app.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BeeSearchMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeeSearchDatabase::class.java,
    )

    @Test
    fun migrationFromOneToTwoBackfillsYearNumberAndBeePresence() {
        val territoryId = UUID.randomUUID().toString()
        val pointWithBeeId = UUID.randomUUID().toString()
        val emptyPointId = UUID.randomUUID().toString()
        val otherObserverPointId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-08-27T08:00:00Z").toEpochMilli()

        migrationHelper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO territories (id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(territoryId, "KLZ", "Клязьма", createdAt, createdAt),
            )
            insertLegacyPoint(
                id = pointWithBeeId,
                territoryId = territoryId,
                observerCode = "GSE",
                createdAt = createdAt,
            )
            insertLegacyPoint(
                id = emptyPointId,
                territoryId = territoryId,
                observerCode = "GSE",
                createdAt = createdAt + 1_000,
            )
            insertLegacyPoint(
                id = otherObserverPointId,
                territoryId = territoryId,
                observerCode = "IVN",
                createdAt = createdAt + 2_000,
            )
            execSQL(
                """
                INSERT INTO bees (
                    id, observation_point_id, mark_color, mark_position, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    pointWithBeeId,
                    "WHITE",
                    "NONE",
                    createdAt,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query(
            """
            SELECT id, observation_year, point_number, bee_presence_result
            FROM observation_points
            ORDER BY observer_code, point_number
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToNext()
            assertEquals(pointWithBeeId, cursor.getString(0))
            assertEquals(2026, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("BEES_FOUND", cursor.getString(3))

            cursor.moveToNext()
            assertEquals(emptyPointId, cursor.getString(0))
            assertEquals(2026, cursor.getInt(1))
            assertEquals(2, cursor.getInt(2))
            assertNull(cursor.getString(3))

            cursor.moveToNext()
            assertEquals(otherObserverPointId, cursor.getString(0))
            assertEquals(2026, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertNull(cursor.getString(3))
        }
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLegacyPoint(
        id: String,
        territoryId: String,
        observerCode: String,
        createdAt: Long,
    ) {
        execSQL(
            """
            INSERT INTO observation_points (
                id,
                territory_id,
                observer_code,
                code,
                latitude,
                longitude,
                gps_latitude,
                gps_longitude,
                gps_accuracy_m,
                created_at,
                completed_at
            ) VALUES (?, ?, ?, NULL, ?, ?, NULL, NULL, NULL, ?, NULL)
            """.trimIndent(),
            arrayOf<Any>(id, territoryId, observerCode, 56.1, 42.7, createdAt),
        )
    }

    private companion object {
        const val DATABASE_NAME = "bee-search-migration-test"
    }
}
