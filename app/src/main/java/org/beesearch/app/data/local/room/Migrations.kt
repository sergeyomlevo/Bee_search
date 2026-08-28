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
