package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.beesearch.app.domain.model.MarkPosition
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
    indices = [Index(value = ["territory_id"])],
)
internal data class ObservationPointEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "observer_code") val observerCode: String,
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
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
