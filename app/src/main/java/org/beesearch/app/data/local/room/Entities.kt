package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.BeePresenceResult
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "territories",
    indices = [Index(value = ["code"], unique = true)],
)
internal data class TerritoryEntity(
    @PrimaryKey val id: UUID,
    val code: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "observation_points",
    foreignKeys = [
        ForeignKey(
            entity = TerritoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["territory_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["territory_id"]),
        Index(
            value = ["territory_id", "observation_year", "observer_code", "point_number"],
            unique = true,
        ),
    ],
)
internal data class ObservationPointEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "observer_code") val observerCode: String,
    @ColumnInfo(name = "observation_year", defaultValue = "0") val observationYear: Int,
    @ColumnInfo(name = "point_number", defaultValue = "0") val pointNumber: Int,
    @ColumnInfo(name = "bee_presence_result") val beePresenceResult: BeePresenceResult?,
    val code: String?,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "gps_latitude") val gpsLatitude: Double?,
    @ColumnInfo(name = "gps_longitude") val gpsLongitude: Double?,
    @ColumnInfo(name = "gps_accuracy_m") val gpsAccuracyM: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
)

@Entity(
    tableName = "bees",
    foreignKeys = [
        ForeignKey(
            entity = ObservationPointEntity::class,
            parentColumns = ["id"],
            childColumns = ["observation_point_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["observation_point_id"]),
        Index(
            value = ["observation_point_id", "mark_color", "mark_position"],
            unique = true,
        ),
    ],
)
internal data class BeeEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "observation_point_id") val observationPointId: UUID,
    @ColumnInfo(name = "mark_color") val markColor: String,
    @ColumnInfo(name = "mark_position") val markPosition: MarkPosition,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "flight_cycles",
    foreignKeys = [
        ForeignKey(
            entity = BeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["bee_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["bee_id"]),
        Index(value = ["bee_id", "sequence_number"], unique = true),
    ],
)
internal data class FlightCycleEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "bee_id") val beeId: UUID,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Int,
    @ColumnInfo(name = "departure_time") val departureTime: Instant,
    @ColumnInfo(name = "return_time") val returnTime: Instant?,
    @ColumnInfo(name = "azimuth_deg") val azimuthDeg: Double?,
    @ColumnInfo(name = "azimuth_capture_consumed", defaultValue = "0")
    val azimuthCaptureConsumed: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
