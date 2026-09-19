package org.beesearch.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE observation_points " +
                "ADD COLUMN observation_year INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE observation_points " +
                "ADD COLUMN point_number INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE observation_points ADD COLUMN bee_presence_result TEXT",
        )

        val legacyPoints = db.query(
            """
            SELECT id, territory_id, observer_code, created_at
            FROM observation_points
            ORDER BY territory_id, observer_code, created_at, id
            """.trimIndent(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyObservationPoint(
                            id = cursor.getString(0),
                            territoryId = cursor.getString(1),
                            observerCode = cursor.getString(2),
                            createdAtEpochMillis = cursor.getLong(3),
                        ),
                    )
                }
            }
        }

        val zoneId = ZoneId.systemDefault()
        var previousScope: NumberingScope? = null
        var pointNumber = 0
        legacyPoints.forEach { point ->
            val observationYear = Instant.ofEpochMilli(point.createdAtEpochMillis)
                .atZone(zoneId)
                .year
            val scope = NumberingScope(
                territoryId = point.territoryId,
                observationYear = observationYear,
                observerCode = point.observerCode,
            )
            pointNumber = if (scope == previousScope) pointNumber + 1 else 1
            previousScope = scope

            db.execSQL(
                """
                UPDATE observation_points
                SET observation_year = ?, point_number = ?
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any>(observationYear, pointNumber, point.id),
            )
        }

        db.execSQL(
            """
            UPDATE observation_points
            SET bee_presence_result = 'BEES_FOUND'
            WHERE EXISTS (
                SELECT 1
                FROM bees
                WHERE bees.observation_point_id = observation_points.id
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_observation_points_territory_id_observation_year_observer_code_point_number
            ON observation_points (
                territory_id,
                observation_year,
                observer_code,
                point_number
            )
            """.trimIndent(),
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE flight_cycles " +
                "ADD COLUMN azimuth_capture_consumed INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            UPDATE flight_cycles
            SET azimuth_capture_consumed = 1
            WHERE azimuth_deg IS NOT NULL
            """.trimIndent(),
        )
    }
}

/**
 * Compatibility migration for devices that opened an intermediate v3 build.
 * The user schema already matches the final schema, so Room only needs to
 * validate it and persist the current identity hash for version 4.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

/**
 * Development-stage reset approved while all existing observation records are test data.
 * v4 cannot truthfully create required Territory fields or an Observer entity from legacy
 * code-only settings, so it clears the old test hierarchy and creates the target schema.
 * Later schema changes must add non-destructive migrations instead of extending this reset.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS flight_cycles")
        db.execSQL("DROP TABLE IF EXISTS bees")
        db.execSQL("DROP TABLE IF EXISTS observation_points")
        db.execSQL("DROP TABLE IF EXISTS territories")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS territories (
                id TEXT NOT NULL,
                code TEXT NOT NULL,
                name TEXT NOT NULL,
                region TEXT NOT NULL,
                district TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_territories_code ON territories(code)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS observers (
                id TEXT NOT NULL,
                code TEXT NOT NULL,
                last_name TEXT NOT NULL,
                first_name TEXT NOT NULL,
                middle_name TEXT,
                contact TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_observers_code ON observers(code)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS observation_points (
                id TEXT NOT NULL,
                territory_id TEXT NOT NULL,
                observer_id TEXT NOT NULL,
                observation_year INTEGER NOT NULL DEFAULT 0,
                point_number INTEGER NOT NULL DEFAULT 0,
                bee_presence_result TEXT,
                code TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                gps_latitude REAL,
                gps_longitude REAL,
                gps_accuracy_m REAL,
                created_at INTEGER NOT NULL,
                completed_at INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(territory_id) REFERENCES territories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(observer_id) REFERENCES observers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_observation_points_territory_id ON observation_points(territory_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_observation_points_observer_id ON observation_points(observer_id)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_observation_points_territory_id_observation_year_observer_id_point_number
            ON observation_points(territory_id, observation_year, observer_id, point_number)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bees (
                id TEXT NOT NULL,
                observation_point_id TEXT NOT NULL,
                mark_color TEXT NOT NULL,
                mark_position TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(observation_point_id) REFERENCES observation_points(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bees_observation_point_id ON bees(observation_point_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bees_observation_point_id_mark_color_mark_position ON bees(observation_point_id, mark_color, mark_position)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS flight_cycles (
                id TEXT NOT NULL,
                bee_id TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                departure_time INTEGER NOT NULL,
                return_time INTEGER,
                azimuth_deg REAL,
                azimuth_capture_consumed INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(bee_id) REFERENCES bees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_flight_cycles_bee_id ON flight_cycles(bee_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_flight_cycles_bee_id_sequence_number ON flight_cycles(bee_id, sequence_number)")
    }
}

/**
 * Non-destructive provenance migration for the per-Bee correction of an
 * initial group launch. Existing v5 data already defines sequence 1 as the
 * group release, so the marker is restored from that established invariant.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE observation_points ADD COLUMN initial_group_release_at INTEGER",
        )
        db.execSQL(
            "ALTER TABLE flight_cycles ADD COLUMN initial_group_launch INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE flight_cycles ADD COLUMN initial_group_launch_correction_eligible INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "UPDATE flight_cycles SET initial_group_launch = 1 WHERE sequence_number = 1",
        )
        db.execSQL(
            """
            UPDATE flight_cycles
            SET initial_group_launch_correction_eligible = 1
            WHERE sequence_number = 1
              AND return_time IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE observation_points
            SET initial_group_release_at = (
                SELECT MIN(flight_cycles.departure_time)
                FROM flight_cycles
                INNER JOIN bees ON bees.id = flight_cycles.bee_id
                WHERE bees.observation_point_id = observation_points.id
                  AND flight_cycles.sequence_number = 1
            )
            WHERE EXISTS (
                SELECT 1
                FROM flight_cycles
                INNER JOIN bees ON bees.id = flight_cycles.bee_id
                WHERE bees.observation_point_id = observation_points.id
                  AND flight_cycles.sequence_number = 1
            )
            """.trimIndent(),
        )
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE observation_points ADD COLUMN description TEXT")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS observation_point_attachments (
                id TEXT NOT NULL,
                observation_point_id TEXT NOT NULL,
                attachment_type TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                original_file_name TEXT,
                mime_type TEXT,
                byte_size INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(observation_point_id) REFERENCES observation_points(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_observation_point_attachments_observation_point_id ON observation_point_attachments(observation_point_id)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS observation_point_weather (
                observation_point_id TEXT NOT NULL,
                status TEXT NOT NULL,
                temperature_c REAL,
                wind_speed_mps REAL,
                wind_direction_deg REAL,
                sample_at INTEGER,
                fetched_at INTEGER,
                source TEXT,
                PRIMARY KEY(observation_point_id),
                FOREIGN KEY(observation_point_id) REFERENCES observation_points(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("INSERT INTO observation_point_weather (observation_point_id, status) SELECT id, 'PENDING' FROM observation_points")
    }
}

private data class LegacyObservationPoint(
    val id: String,
    val territoryId: String,
    val observerCode: String,
    val createdAtEpochMillis: Long,
)

private data class NumberingScope(
    val territoryId: String,
    val observationYear: Int,
    val observerCode: String,
)
