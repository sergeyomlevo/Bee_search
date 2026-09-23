package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.model.PhysicalObjectType
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
    val region: String,
    val district: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "observers",
    indices = [Index(value = ["code"], unique = true)],
)
internal data class ObserverEntity(
    @PrimaryKey val id: UUID,
    val code: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "middle_name") val middleName: String?,
    val contact: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "physical_objects",
    foreignKeys = [ForeignKey(
        entity = TerritoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["territory_id"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.NO_ACTION,
    )],
    indices = [
        Index(value = ["territory_id"]),
        Index(value = ["territory_id", "object_type", "sequence_number"], unique = true),
    ],
)
internal data class PhysicalObjectEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "object_type") val objectType: PhysicalObjectType,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "apiaries",
    foreignKeys = [ForeignKey(
        entity = PhysicalObjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["physical_object_id"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.NO_ACTION,
    )],
)
internal data class ApiaryEntity(
    @PrimaryKey @ColumnInfo(name = "physical_object_id") val physicalObjectId: UUID,
    val name: String?,
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
        ForeignKey(
            entity = ObserverEntity::class,
            parentColumns = ["id"],
            childColumns = ["observer_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["territory_id"]),
        Index(value = ["observer_id"]),
        Index(
            value = ["territory_id", "observation_year", "observer_id", "point_number"],
            unique = true,
        ),
    ],
)
internal data class ObservationPointEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "observer_id") val observerId: UUID,
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
    @ColumnInfo(name = "initial_group_release_at") val initialGroupReleaseAt: Instant?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    val description: String? = null,
)

@Entity(
    tableName = "observation_point_attachments",
    foreignKeys = [ForeignKey(
        entity = ObservationPointEntity::class,
        parentColumns = ["id"], childColumns = ["observation_point_id"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.NO_ACTION,
    )],
    indices = [Index(value = ["observation_point_id"])],
)
internal data class ObservationPointAttachmentEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "observation_point_id") val observationPointId: UUID,
    @ColumnInfo(name = "attachment_type") val type: AttachmentType,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "original_file_name") val originalFileName: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "observation_point_weather",
    foreignKeys = [ForeignKey(
        entity = ObservationPointEntity::class,
        parentColumns = ["id"], childColumns = ["observation_point_id"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.NO_ACTION,
    )],
)
internal data class ObservationPointWeatherEntity(
    @PrimaryKey @ColumnInfo(name = "observation_point_id") val observationPointId: UUID,
    val status: WeatherStatus,
    @ColumnInfo(name = "temperature_c") val temperatureC: Double?,
    @ColumnInfo(name = "wind_speed_mps") val windSpeedMps: Double?,
    @ColumnInfo(name = "wind_direction_deg") val windDirectionDeg: Double?,
    @ColumnInfo(name = "sample_at") val sampleAt: Instant?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant?,
    val source: String?,
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
        ForeignKey(
            entity = PhysicalObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_object_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["observation_point_id"]),
        Index(value = ["source_object_id"]),
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
    @ColumnInfo(name = "source_object_id") val sourceObjectId: UUID? = null,
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
    @ColumnInfo(name = "initial_group_launch", defaultValue = "0")
    val isInitialGroupLaunch: Boolean,
    @ColumnInfo(name = "initial_group_launch_correction_eligible", defaultValue = "0")
    val isFirstDepartureCancellationEligible: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
