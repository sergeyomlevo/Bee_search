package org.beesearch.app.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migrationFromSixToSevenPreservesPointAndInitializesProperties() {
        val territoryId = UUID.randomUUID().toString()
        val observerId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val timestamp = Instant.parse("2026-09-12T10:15:00Z").toEpochMilli()
        migrationHelper.createDatabase(DATABASE_NAME + "-v7", 6).apply {
            execSQL("INSERT INTO territories (id, code, name, region, district, created_at, updated_at) VALUES (?, 'T', 'Territory', 'R', 'D', ?, ?)", arrayOf<Any>(territoryId, timestamp, timestamp))
            execSQL("INSERT INTO observers (id, code, last_name, first_name, middle_name, contact, created_at, updated_at) VALUES (?, 'O', 'Last', 'First', NULL, NULL, ?, ?)", arrayOf<Any>(observerId, timestamp, timestamp))
            execSQL("INSERT INTO observation_points (id, territory_id, observer_id, observation_year, point_number, bee_presence_result, code, latitude, longitude, gps_latitude, gps_longitude, gps_accuracy_m, created_at, initial_group_release_at, completed_at) VALUES (?, ?, ?, 2026, 4, 'NO_BEES_FOUND', 'P4', 56.1, 42.7, NULL, NULL, NULL, ?, NULL, ?)", arrayOf<Any?>(pointId, territoryId, observerId, timestamp, timestamp))
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(DATABASE_NAME + "-v7", 7, true, MIGRATION_6_7)
        migrated.query("SELECT observation_year, point_number, bee_presence_result, code, latitude, longitude, description FROM observation_points WHERE id = ?", arrayOf(pointId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2026, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1))
            assertEquals("NO_BEES_FOUND", cursor.getString(2))
            assertEquals("P4", cursor.getString(3))
            assertEquals(56.1, cursor.getDouble(4), 0.0)
            assertEquals(42.7, cursor.getDouble(5), 0.0)
            assertTrue(cursor.isNull(6))
        }
        migrated.query("SELECT status, temperature_c, wind_speed_mps, wind_direction_deg, sample_at, fetched_at, source FROM observation_point_weather WHERE observation_point_id = ?", arrayOf(pointId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("PENDING", cursor.getString(0))
            for (index in 1..6) assertTrue(cursor.isNull(index))
        }
        migrated.close()
    }

    @Test
    fun migrationFromTwoToThreeBackfillsCaptureConsumptionAndPreservesCycles() {
        val databaseName = "$DATABASE_NAME-2-3"
        val territoryId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val beeId = UUID.randomUUID().toString()
        val nullCycleId = UUID.randomUUID().toString()
        val measuredCycleId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-08-28T08:00:00Z").toEpochMilli()

        migrationHelper.createDatabase(databaseName, 2).apply {
            insertVersionTwoParents(territoryId, pointId, beeId, createdAt)
            insertVersionTwoCycle(
                id = nullCycleId,
                beeId = beeId,
                sequenceNumber = 1,
                departureTime = createdAt,
                returnTime = createdAt + 30_000,
                azimuthDeg = null,
            )
            insertVersionTwoCycle(
                id = measuredCycleId,
                beeId = beeId,
                sequenceNumber = 2,
                departureTime = createdAt + 40_000,
                returnTime = null,
                azimuthDeg = 247.0,
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MIGRATION_2_3,
        )

        migrated.query(
            """
            SELECT id, bee_id, sequence_number, departure_time, return_time,
                   azimuth_deg, azimuth_capture_consumed, created_at, updated_at
            FROM flight_cycles
            ORDER BY sequence_number
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToNext()
            assertEquals(nullCycleId, cursor.getString(0))
            assertEquals(beeId, cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(createdAt, cursor.getLong(3))
            assertEquals(createdAt + 30_000, cursor.getLong(4))
            assertNull(cursor.getString(5))
            assertFalse(cursor.getInt(6) != 0)
            assertEquals(createdAt, cursor.getLong(7))
            assertEquals(createdAt, cursor.getLong(8))

            cursor.moveToNext()
            assertEquals(measuredCycleId, cursor.getString(0))
            assertEquals(beeId, cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals(createdAt + 40_000, cursor.getLong(3))
            assertTrue(cursor.isNull(4))
            assertEquals(247.0, cursor.getDouble(5), 0.0)
            assertTrue(cursor.getInt(6) != 0)
            assertEquals(createdAt + 40_000, cursor.getLong(7))
            assertEquals(createdAt + 40_000, cursor.getLong(8))
        }
        migrated.close()
    }

    @Test
    fun compatibilityMigrationFromThreeToFourPreservesDataAndRepairsLegacyIdentity() {
        val databaseName = "$DATABASE_NAME-3-4"
        val territoryId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val beeId = UUID.randomUUID().toString()
        val cycleId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-08-29T08:00:00Z").toEpochMilli()

        migrationHelper.createDatabase(databaseName, 3).apply {
            insertVersionTwoParents(territoryId, pointId, beeId, createdAt)
            insertVersionThreeCycle(
                id = cycleId,
                beeId = beeId,
                sequenceNumber = 3,
                departureTime = createdAt + 60_000,
                returnTime = createdAt + 120_000,
                azimuthDeg = 247.0,
                azimuthCaptureConsumed = true,
            )
            execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf(INTERMEDIATE_V3_IDENTITY_HASH),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MIGRATION_3_4,
        )
        migrated.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            assertEquals(4, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT bee_id, sequence_number, departure_time, return_time,
                   azimuth_deg, azimuth_capture_consumed, created_at, updated_at
            FROM flight_cycles
            WHERE id = ?
            """.trimIndent(),
            arrayOf(cycleId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(beeId, cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(createdAt + 60_000, cursor.getLong(2))
            assertEquals(createdAt + 120_000, cursor.getLong(3))
            assertEquals(247.0, cursor.getDouble(4), 0.0)
            assertTrue(cursor.getInt(5) != 0)
            assertEquals(createdAt + 60_000, cursor.getLong(6))
            assertEquals(createdAt + 60_000, cursor.getLong(7))
        }
        migrated.close()

        migrationHelper.runMigrationsAndValidate(databaseName, 4, true).close()
    }

    @Test
    fun fullMigrationPathFromOneToThreeAppliesBothMigrations() {
        val databaseName = "$DATABASE_NAME-1-3"
        val territoryId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val beeId = UUID.randomUUID().toString()
        val cycleId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-08-27T08:00:00Z").toEpochMilli()

        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO territories (id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(territoryId, "KLZ", "Клязьма", createdAt, createdAt),
            )
            insertLegacyPoint(pointId, territoryId, "GSE", createdAt)
            execSQL(
                """
                INSERT INTO bees (id, observation_point_id, mark_color, mark_position, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(beeId, pointId, "WHITE", "NONE", createdAt),
            )
            insertVersionTwoCycle(cycleId, beeId, 1, createdAt, null, 90.0)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
        )
        migrated.query(
            "SELECT azimuth_deg, azimuth_capture_consumed FROM flight_cycles WHERE id = ?",
            arrayOf(cycleId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(90.0, cursor.getDouble(0), 0.0)
            assertTrue(cursor.getInt(1) != 0)
        }
        migrated.close()
    }

    @Test
    fun fullMigrationPathFromOneToFourAppliesAllMigrations() {
        val databaseName = "$DATABASE_NAME-1-4"
        val territoryId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val beeId = UUID.randomUUID().toString()
        val cycleId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-08-27T09:00:00Z").toEpochMilli()

        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO territories (id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(territoryId, "KLZ", "Клязьма", createdAt, createdAt),
            )
            insertLegacyPoint(pointId, territoryId, "GSE", createdAt)
            execSQL(
                """
                INSERT INTO bees (id, observation_point_id, mark_color, mark_position, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(beeId, pointId, "WHITE", "NONE", createdAt),
            )
            insertVersionTwoCycle(cycleId, beeId, 1, createdAt, null, 90.0)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        migrated.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            assertEquals(4, cursor.getInt(0))
        }
        migrated.query(
            "SELECT azimuth_deg, azimuth_capture_consumed FROM flight_cycles WHERE id = ?",
            arrayOf(cycleId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(90.0, cursor.getDouble(0), 0.0)
            assertTrue(cursor.getInt(1) != 0)
        }
        migrated.close()
    }

    @Test
    fun migrationFromFourToFiveClearsApprovedTestHierarchyAndCreatesObserverSchema() {
        val databaseName = "$DATABASE_NAME-4-5"
        val territoryId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val createdAt = Instant.parse("2026-09-01T08:00:00Z").toEpochMilli()
        migrationHelper.createDatabase(databaseName, 4).apply {
            execSQL(
                "INSERT INTO territories (id, code, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(territoryId, "TEST", "Тест", createdAt, createdAt),
            )
            execSQL(
                """
                INSERT INTO observation_points (
                    id, territory_id, observer_code, observation_year, point_number,
                    bee_presence_result, code, latitude, longitude, gps_latitude,
                    gps_longitude, gps_accuracy_m, created_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, NULL, NULL, NULL, ?, NULL)
                """.trimIndent(),
                arrayOf<Any>(pointId, territoryId, "OLD", 2026, 1, 56.1, 42.7, createdAt),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 5, true, MIGRATION_4_5)
        migrated.query("SELECT COUNT(*) FROM territories").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM observation_points").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        migrated.query("PRAGMA table_info(observers)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertTrue(columns.containsAll(setOf("id", "code", "last_name", "first_name", "middle_name", "contact")))
        }
        migrated.query("PRAGMA foreign_key_list(observation_points)").use { cursor ->
            val referencedTables = mutableSetOf<String>()
            while (cursor.moveToNext()) referencedTables += cursor.getString(2)
            assertEquals(setOf("territories", "observers"), referencedTables)
        }
        migrated.query("PRAGMA index_list(observation_points)").use { cursor ->
            val indexNames = mutableSetOf<String>()
            while (cursor.moveToNext()) indexNames += cursor.getString(1)
            assertTrue(indexNames.contains("index_observation_points_observer_id"))
            assertTrue(indexNames.contains("index_observation_points_territory_id"))
        }
        migrated.close()
    }

    @Test
    fun migrationFromFiveToSixPreservesHistoryAndBackfillsInitialLaunchProvenance() {
        val databaseName = "$DATABASE_NAME-5-6"
        val territoryId = UUID.randomUUID().toString()
        val observerId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        val openBeeId = UUID.randomUUID().toString()
        val returnedBeeId = UUID.randomUUID().toString()
        val openCycleId = UUID.randomUUID().toString()
        val returnedCycleId = UUID.randomUUID().toString()
        val departureTime = Instant.parse("2026-09-08T08:00:00Z").toEpochMilli()
        val returnTime = departureTime + 45_000

        migrationHelper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO territories (id, code, name, region, district, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(territoryId, "KLZ", "Клязьма", "Область", "Район", departureTime, departureTime),
            )
            execSQL(
                """
                INSERT INTO observers (id, code, last_name, first_name, middle_name, contact, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(observerId, "GSE", "Иванов", "Иван", departureTime, departureTime),
            )
            execSQL(
                """
                INSERT INTO observation_points (
                    id, territory_id, observer_id, observation_year, point_number,
                    bee_presence_result, code, latitude, longitude, gps_latitude,
                    gps_longitude, gps_accuracy_m, created_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, NULL, NULL, ?, NULL)
                """.trimIndent(),
                arrayOf<Any>(pointId, territoryId, observerId, 2026, 1, "BEES_FOUND", 56.1, 42.7, departureTime),
            )
            listOf(openBeeId to "WHITE", returnedBeeId to "BLUE").forEach { (beeId, color) ->
                execSQL(
                    """
                    INSERT INTO bees (id, observation_point_id, mark_color, mark_position, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(beeId, pointId, color, "NONE", departureTime),
                )
            }
            execSQL(
                """
                INSERT INTO flight_cycles (
                    id, bee_id, sequence_number, departure_time, return_time,
                    azimuth_deg, azimuth_capture_consumed, created_at, updated_at
                ) VALUES (?, ?, 1, ?, NULL, NULL, 0, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(openCycleId, openBeeId, departureTime, departureTime, departureTime),
            )
            execSQL(
                """
                INSERT INTO flight_cycles (
                    id, bee_id, sequence_number, departure_time, return_time,
                    azimuth_deg, azimuth_capture_consumed, created_at, updated_at
                ) VALUES (?, ?, 1, ?, ?, NULL, 0, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(returnedCycleId, returnedBeeId, departureTime, returnTime, departureTime, returnTime),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 6, true, MIGRATION_5_6)
        migrated.query(
            "SELECT initial_group_release_at FROM observation_points WHERE id = ?",
            arrayOf(pointId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(departureTime, cursor.getLong(0))
        }
        migrated.query(
            "SELECT initial_group_launch, initial_group_launch_correction_eligible FROM flight_cycles WHERE id = ?",
            arrayOf(openCycleId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.getInt(0) != 0)
            assertTrue(cursor.getInt(1) != 0)
        }
        migrated.query(
            "SELECT initial_group_launch, initial_group_launch_correction_eligible, return_time FROM flight_cycles WHERE id = ?",
            arrayOf(returnedCycleId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.getInt(0) != 0)
            assertFalse(cursor.getInt(1) != 0)
            assertEquals(returnTime, cursor.getLong(2))
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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertVersionTwoParents(
        territoryId: String,
        pointId: String,
        beeId: String,
        createdAt: Long,
    ) {
        execSQL(
            """
            INSERT INTO territories (id, code, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(territoryId, "KLZ", "Клязьма", createdAt, createdAt),
        )
        execSQL(
            """
            INSERT INTO observation_points (
                id, territory_id, observer_code, observation_year, point_number,
                bee_presence_result, code, latitude, longitude, gps_latitude,
                gps_longitude, gps_accuracy_m, created_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, NULL, NULL, ?, NULL)
            """.trimIndent(),
            arrayOf<Any>(pointId, territoryId, "GSE", 2026, 1, "BEES_FOUND", 56.1, 42.7, createdAt),
        )
        execSQL(
            """
            INSERT INTO bees (id, observation_point_id, mark_color, mark_position, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(beeId, pointId, "WHITE", "NONE", createdAt),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertVersionTwoCycle(
        id: String,
        beeId: String,
        sequenceNumber: Int,
        departureTime: Long,
        returnTime: Long?,
        azimuthDeg: Double?,
    ) {
        execSQL(
            """
            INSERT INTO flight_cycles (
                id, bee_id, sequence_number, departure_time, return_time,
                azimuth_deg, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                beeId,
                sequenceNumber,
                departureTime,
                returnTime,
                azimuthDeg,
                departureTime,
                departureTime,
            ),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertVersionThreeCycle(
        id: String,
        beeId: String,
        sequenceNumber: Int,
        departureTime: Long,
        returnTime: Long?,
        azimuthDeg: Double?,
        azimuthCaptureConsumed: Boolean,
    ) {
        execSQL(
            """
            INSERT INTO flight_cycles (
                id, bee_id, sequence_number, departure_time, return_time,
                azimuth_deg, azimuth_capture_consumed, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                beeId,
                sequenceNumber,
                departureTime,
                returnTime,
                azimuthDeg,
                azimuthCaptureConsumed,
                departureTime,
                departureTime,
            ),
        )
    }

    private companion object {
        const val DATABASE_NAME = "bee-search-migration-test"
        const val INTERMEDIATE_V3_IDENTITY_HASH = "dfa1a6f3302e33c27f6513c2d9f702d4"
    }
}
