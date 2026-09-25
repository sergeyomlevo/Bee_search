package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.BeePresenceResult
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.WeatherStatus

internal data class CompletedObservationPointRow(
    @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "observation_year") val observationYear: Int,
    @ColumnInfo(name = "point_number") val pointNumber: Int,
    @ColumnInfo(name = "territory_code") val territoryCode: String,
    @ColumnInfo(name = "territory_name") val territoryName: String,
    @ColumnInfo(name = "bee_count") val beeCount: Int,
)

internal data class ObservationPointSummaryRow(
    @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "observation_year") val observationYear: Int,
    @ColumnInfo(name = "point_number") val pointNumber: Int,
    @ColumnInfo(name = "code") val code: String?,
    @ColumnInfo(name = "bee_presence_result") val beePresenceResult: BeePresenceResult?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "gps_accuracy_m") val gpsAccuracyM: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "bee_count") val beeCount: Int,
    @ColumnInfo(name = "completed_flight_cycle_count") val completedFlightCycleCount: Int,
)

@Dao
internal abstract class BackupDao {
    @Query("SELECT * FROM territories ORDER BY id") abstract suspend fun territories(): List<TerritoryEntity>
    @Query("SELECT * FROM observers ORDER BY id") abstract suspend fun observers(): List<ObserverEntity>
    @Query("SELECT * FROM observation_points ORDER BY id") abstract suspend fun observationPoints(): List<ObservationPointEntity>
    @Query("SELECT * FROM physical_objects ORDER BY id") abstract suspend fun physicalObjects(): List<PhysicalObjectEntity>
    @Query("SELECT * FROM hollows ORDER BY physical_object_id") abstract suspend fun hollows(): List<HollowEntity>
    @Query("SELECT * FROM log_hives ORDER BY physical_object_id") abstract suspend fun logHives(): List<LogHiveEntity>
    @Query("SELECT * FROM physical_object_media ORDER BY id") abstract suspend fun physicalObjectMedia(): List<PhysicalObjectMediaEntity>
    @Query("SELECT * FROM apiaries ORDER BY physical_object_id") abstract suspend fun apiaries(): List<ApiaryEntity>
    @Query("SELECT * FROM bees ORDER BY id") abstract suspend fun bees(): List<BeeEntity>
    @Query("SELECT * FROM flight_cycles ORDER BY id") abstract suspend fun flightCycles(): List<FlightCycleEntity>
    @Query("SELECT * FROM observation_point_attachments ORDER BY id") abstract suspend fun observationPointAttachments(): List<ObservationPointAttachmentEntity>
    @Query("SELECT * FROM observation_point_weather ORDER BY observation_point_id") abstract suspend fun observationPointWeather(): List<ObservationPointWeatherEntity>
    @Query("SELECT COUNT(*) FROM territories") abstract suspend fun territoryCount(): Int
    @Query("SELECT COUNT(*) FROM observers") abstract suspend fun observerCount(): Int
    @Query("SELECT COUNT(*) FROM observation_points") abstract suspend fun observationPointCount(): Int
    @Query("SELECT COUNT(*) FROM physical_objects") abstract suspend fun physicalObjectCount(): Int
    @Query("SELECT COUNT(*) FROM bees") abstract suspend fun beeCount(): Int
    @Query("SELECT COUNT(*) FROM flight_cycles") abstract suspend fun flightCycleCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertTerritories(value: List<TerritoryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservers(value: List<ObserverEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservationPoints(value: List<ObservationPointEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertPhysicalObjects(value: List<PhysicalObjectEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertHollows(value: List<HollowEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertLogHives(value: List<LogHiveEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertPhysicalObjectMedia(value: List<PhysicalObjectMediaEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertApiaries(value: List<ApiaryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertBees(value: List<BeeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFlightCycles(value: List<FlightCycleEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservationPointAttachments(value: List<ObservationPointAttachmentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservationPointWeather(value: List<ObservationPointWeatherEntity>)
}

@Dao
internal interface TerritoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(territory: TerritoryEntity)

    @Query("SELECT * FROM territories WHERE id = :id")
    suspend fun getById(id: UUID): TerritoryEntity?

    @Query("SELECT * FROM territories ORDER BY code COLLATE NOCASE, id")
    fun observeAll(): Flow<List<TerritoryEntity>>

    @Query("UPDATE territories SET code = :code, name = :name, region = :region, district = :district, updated_at = :updatedAt WHERE id = :id")
    suspend fun update(id: UUID, code: String, name: String, region: String, district: String, updatedAt: Instant): Int

    @Query("DELETE FROM territories WHERE id = :id")
    suspend fun deleteById(id: UUID): Int

    @Query("SELECT COUNT(*) FROM observation_points WHERE territory_id = :id")
    suspend fun countObservationPoints(id: UUID): Int

    @Query("SELECT COUNT(*) FROM physical_objects WHERE territory_id = :id")
    suspend fun countPhysicalObjects(id: UUID): Int
}

@Dao
internal interface PhysicalObjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObject(value: PhysicalObjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApiary(value: ApiaryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHollow(value: HollowEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLogHive(value: LogHiveEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedia(values: List<PhysicalObjectMediaEntity>)

    @Query("SELECT * FROM physical_objects WHERE id = :id")
    suspend fun getById(id: UUID): PhysicalObjectEntity?

    @Query("SELECT * FROM physical_objects WHERE territory_id = :territoryId ORDER BY object_type, sequence_number, id")
    suspend fun getForTerritory(territoryId: UUID): List<PhysicalObjectEntity>

    @Query("SELECT * FROM apiaries WHERE physical_object_id = :id")
    suspend fun getApiary(id: UUID): ApiaryEntity?

    @Query("SELECT * FROM hollows WHERE physical_object_id = :id")
    suspend fun getHollow(id: UUID): HollowEntity?

    @Query("SELECT * FROM log_hives WHERE physical_object_id = :id")
    suspend fun getLogHive(id: UUID): LogHiveEntity?

    @Query("SELECT * FROM hollows WHERE physical_object_id IN (:ids) ORDER BY physical_object_id")
    suspend fun getHollows(ids: Collection<UUID>): List<HollowEntity>

    @Query("SELECT * FROM log_hives WHERE physical_object_id IN (:ids) ORDER BY physical_object_id")
    suspend fun getLogHives(ids: Collection<UUID>): List<LogHiveEntity>

    @Query("SELECT * FROM physical_object_media WHERE physical_object_id = :id ORDER BY created_at, id")
    suspend fun getMedia(id: UUID): List<PhysicalObjectMediaEntity>

    @Query("SELECT * FROM physical_object_media WHERE physical_object_id IN (:ids) ORDER BY created_at, id")
    suspend fun getMediaForObjects(ids: Collection<UUID>): List<PhysicalObjectMediaEntity>

    @Query("UPDATE hollows SET tree = :tree, entrance_height_cm = :entranceHeightCm, entrance_azimuth_deg = :entranceAzimuthDeg, outer_diameter_cm = :outerDiameterCm, internal_diameter_cm = :internalDiameterCm, notes = :notes WHERE physical_object_id = :id")
    suspend fun updateHollow(id: UUID, tree: String, entranceHeightCm: Double, entranceAzimuthDeg: Int, outerDiameterCm: Double, internalDiameterCm: Double?, notes: String?): Int

    @Query("UPDATE log_hives SET tree = :tree, entrance_height_cm = :entranceHeightCm, entrance_azimuth_deg = :entranceAzimuthDeg, outer_diameter_cm = :outerDiameterCm, material = :material, internal_diameter_cm = :internalDiameterCm, internal_height_cm = :internalHeightCm, notes = :notes WHERE physical_object_id = :id")
    suspend fun updateLogHive(id: UUID, tree: String, entranceHeightCm: Double, entranceAzimuthDeg: Int, outerDiameterCm: Double, material: String, internalDiameterCm: Double, internalHeightCm: Double, notes: String?): Int

    @Query("SELECT * FROM apiaries WHERE physical_object_id IN (:ids) ORDER BY physical_object_id")
    suspend fun getApiaries(ids: Collection<UUID>): List<ApiaryEntity>

    @Query(
        """
        SELECT COALESCE(MAX(sequence_number), 0) + 1
        FROM physical_objects
        WHERE territory_id = :territoryId AND object_type = :objectType
        """,
    )
    suspend fun getNextSequenceNumber(territoryId: UUID, objectType: org.beesearch.app.domain.model.PhysicalObjectType): Int
}

@Dao
internal interface ObserverDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observer: ObserverEntity)

    @Query("SELECT * FROM observers WHERE id = :id")
    suspend fun getById(id: UUID): ObserverEntity?

    @Query("SELECT * FROM observers ORDER BY last_name COLLATE NOCASE, first_name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ObserverEntity>>

    @Query("UPDATE observers SET code = :code, last_name = :lastName, first_name = :firstName, middle_name = :middleName, contact = :contact, updated_at = :updatedAt WHERE id = :id")
    suspend fun update(id: UUID, code: String, lastName: String, firstName: String, middleName: String?, contact: String?, updatedAt: Instant): Int

    @Query("DELETE FROM observers WHERE id = :id")
    suspend fun deleteById(id: UUID): Int

    @Query("SELECT COUNT(*) FROM observation_points WHERE observer_id = :id")
    suspend fun countObservationPoints(id: UUID): Int

    @Query("SELECT COUNT(*) FROM physical_objects WHERE creator_observer_id = :id")
    suspend fun countCreatedPhysicalObjects(id: UUID): Int
}

@Dao
internal interface ObservationPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: ObservationPointEntity)

    @Query("SELECT * FROM observation_points WHERE id = :id")
    suspend fun getById(id: UUID): ObservationPointEntity?

    @Query("SELECT * FROM observation_points WHERE id = :id")
    fun observeById(id: UUID): Flow<ObservationPointEntity?>

    @Query("UPDATE observation_points SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: UUID, description: String?): Int

    @Query(
        """
        SELECT p.id, p.territory_id, p.observation_year, p.point_number, p.code,
               p.bee_presence_result, p.latitude, p.longitude, p.gps_accuracy_m,
               p.created_at, p.completed_at,
               COUNT(DISTINCT b.id) AS bee_count,
               COUNT(CASE WHEN c.return_time IS NOT NULL THEN 1 END) AS completed_flight_cycle_count
        FROM observation_points AS p
        LEFT JOIN bees AS b ON b.observation_point_id = p.id
        LEFT JOIN flight_cycles AS c ON c.bee_id = b.id
        WHERE p.territory_id = :territoryId
          AND (:observationYear IS NULL OR p.observation_year = :observationYear)
        GROUP BY p.id
        ORDER BY p.created_at DESC, p.id
        """,
    )
    fun observeSummaries(
        territoryId: UUID,
        observationYear: Int?,
    ): Flow<List<ObservationPointSummaryRow>>

    @Query("SELECT COUNT(*) FROM observation_points WHERE completed_at IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM observation_points")
    suspend fun countAll(): Int

    @Query("DELETE FROM observation_points")
    suspend fun deleteAll(): Int

    @Query(
        """
        SELECT observation_points.id,
               observation_points.created_at,
               observation_points.observation_year,
               observation_points.point_number,
               territories.code AS territory_code,
               territories.name AS territory_name,
               COUNT(bees.id) AS bee_count
        FROM observation_points
        INNER JOIN territories ON territories.id = observation_points.territory_id
        LEFT JOIN bees ON bees.observation_point_id = observation_points.id
        WHERE observation_points.completed_at IS NOT NULL
        GROUP BY observation_points.id
        ORDER BY observation_points.created_at DESC, observation_points.id
        """,
    )
    suspend fun getCompletedSummaries(): List<CompletedObservationPointRow>

    @Query("DELETE FROM observation_points WHERE id = :id AND completed_at IS NOT NULL")
    suspend fun deleteCompletedById(id: UUID): Int

    @Query(
        """
        SELECT COALESCE(MAX(point_number), 0) + 1
        FROM observation_points
        WHERE territory_id = :territoryId
          AND observation_year = :observationYear
          AND observer_id = :observerId
        """,
    )
    suspend fun getNextPointNumber(
        territoryId: UUID,
        observationYear: Int,
        observerId: UUID,
    ): Int

    @Query("SELECT * FROM observation_points WHERE completed_at IS NULL LIMIT 1")
    fun observeActive(): Flow<ObservationPointEntity?>

    @Query(
        """
        UPDATE observation_points
        SET completed_at = :completedAt
        WHERE id = :id AND completed_at IS NULL
        """,
    )
    suspend fun complete(id: UUID, completedAt: Instant): Int

    @Query(
        """
        UPDATE observation_points
        SET bee_presence_result = :result
        WHERE id = :id AND completed_at IS NULL
        """,
    )
    suspend fun setBeePresenceResult(id: UUID, result: BeePresenceResult?): Int

    @Query(
        """
        UPDATE observation_points
        SET initial_group_release_at = :releaseAt
        WHERE id = :pointId
          AND completed_at IS NULL
          AND initial_group_release_at IS NULL
        """,
    )
    suspend fun setInitialGroupReleaseAt(pointId: UUID, releaseAt: Instant): Int

    @Query(
        """
        UPDATE observation_points
        SET bee_presence_result = :result, completed_at = :completedAt
        WHERE id = :id AND completed_at IS NULL
        """,
    )
    suspend fun recordNoBeesAndComplete(
        id: UUID,
        result: BeePresenceResult,
        completedAt: Instant,
    ): Int
}

@Dao
internal interface ObservationPointAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attachment: ObservationPointAttachmentEntity)
    @Query("SELECT * FROM observation_point_attachments WHERE observation_point_id = :pointId ORDER BY created_at, id")
    suspend fun getForPoint(pointId: UUID): List<ObservationPointAttachmentEntity>
    @Query("SELECT * FROM observation_point_attachments ORDER BY created_at, id")
    suspend fun getAll(): List<ObservationPointAttachmentEntity>

    @Query("SELECT * FROM observation_point_attachments WHERE observation_point_id = :pointId ORDER BY created_at, id")
    fun observeForPoint(pointId: UUID): Flow<List<ObservationPointAttachmentEntity>>
    @Query("SELECT * FROM observation_point_attachments WHERE id = :id")
    suspend fun getById(id: UUID): ObservationPointAttachmentEntity?
    @Query("DELETE FROM observation_point_attachments WHERE id = :id")
    suspend fun deleteById(id: UUID): Int
    @Query("DELETE FROM observation_point_attachments WHERE observation_point_id = :pointId")
    suspend fun deleteForPoint(pointId: UUID): Int
    @Query("DELETE FROM observation_point_attachments")
    suspend fun deleteAll(): Int
}

@Dao
internal interface ObservationPointWeatherDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(weather: ObservationPointWeatherEntity)
    @Query("SELECT * FROM observation_point_weather WHERE observation_point_id = :pointId")
    suspend fun getByPointId(pointId: UUID): ObservationPointWeatherEntity?

    @Query("SELECT * FROM observation_point_weather WHERE observation_point_id = :pointId")
    fun observeByPointId(pointId: UUID): Flow<ObservationPointWeatherEntity?>
    @Query("SELECT * FROM observation_point_weather WHERE status = 'PENDING'")
    suspend fun getPending(): List<ObservationPointWeatherEntity>
    @Query("UPDATE observation_point_weather SET status = :status, temperature_c = :temperatureC, wind_speed_mps = :windSpeedMps, wind_direction_deg = :windDirectionDeg, sample_at = :sampleAt, fetched_at = :fetchedAt, source = :source WHERE observation_point_id = :pointId AND status != 'LOADED'")
    suspend fun storeLoaded(pointId: UUID, status: WeatherStatus, temperatureC: Double, windSpeedMps: Double, windDirectionDeg: Double, sampleAt: Instant, fetchedAt: Instant, source: String): Int
    @Query("UPDATE observation_point_weather SET status = 'UNAVAILABLE' WHERE observation_point_id = :pointId AND status != 'LOADED'")
    suspend fun markUnavailable(pointId: UUID): Int
    @Query("UPDATE observation_point_weather SET status = 'PENDING' WHERE observation_point_id = :pointId AND status != 'LOADED'")
    suspend fun resetPending(pointId: UUID): Int
    @Query("DELETE FROM observation_point_weather WHERE observation_point_id = :pointId")
    suspend fun deleteForPoint(pointId: UUID): Int
    @Query("DELETE FROM observation_point_weather")
    suspend fun deleteAll(): Int
}

@Dao
internal interface BeeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bee: BeeEntity)

    @Delete
    suspend fun delete(bee: BeeEntity): Int

    @Query("SELECT * FROM bees WHERE id = :id")
    suspend fun getById(id: UUID): BeeEntity?

    @Query("UPDATE bees SET source_object_id = :sourceObjectId WHERE id = :beeId")
    suspend fun setSourceObject(beeId: UUID, sourceObjectId: UUID?): Int

    @Query("SELECT * FROM bees WHERE observation_point_id = :pointId ORDER BY created_at, id")
    suspend fun getForPoint(pointId: UUID): List<BeeEntity>

    @Query("SELECT COUNT(*) FROM bees WHERE observation_point_id = :pointId")
    suspend fun countForPoint(pointId: UUID): Int

    @Query("SELECT COUNT(*) FROM bees")
    suspend fun countAll(): Int

    @Query("DELETE FROM bees")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM bees WHERE observation_point_id = :pointId")
    suspend fun deleteForObservationPoint(pointId: UUID): Int

    @Query("SELECT * FROM bees WHERE observation_point_id = :pointId ORDER BY created_at, id")
    fun observeForPoint(pointId: UUID): Flow<List<BeeEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM bees
        WHERE observation_point_id = :pointId
          AND mark_color = :markColor
          AND mark_position IN (:markPositionTokens)
        """,
    )
    suspend fun countByMark(
        pointId: UUID,
        markColor: String,
        markPositionTokens: Collection<String>,
    ): Int
}

@Dao
internal interface FlightCycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(cycle: FlightCycleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(cycles: List<FlightCycleEntity>)

    @Query("SELECT * FROM flight_cycles WHERE id = :id")
    suspend fun getById(id: UUID): FlightCycleEntity?

    @Query(
        """
        SELECT * FROM flight_cycles
        WHERE bee_id = :beeId AND return_time IS NULL
        ORDER BY sequence_number DESC
        LIMIT 1
        """,
    )
    suspend fun getOpenForBee(beeId: UUID): FlightCycleEntity?

    @Query(
        "SELECT * FROM flight_cycles WHERE bee_id = :beeId ORDER BY sequence_number DESC LIMIT 1",
    )
    suspend fun getLatestForBee(beeId: UUID): FlightCycleEntity?

    @Query("SELECT MAX(sequence_number) FROM flight_cycles WHERE bee_id = :beeId")
    suspend fun getMaximumSequenceNumber(beeId: UUID): Int?

    @Query("SELECT COUNT(*) FROM flight_cycles WHERE bee_id = :beeId")
    suspend fun countForBee(beeId: UUID): Int

    @Query("SELECT COUNT(*) FROM flight_cycles")
    suspend fun countAll(): Int

    @Query("DELETE FROM flight_cycles")
    suspend fun deleteAll(): Int

    @Query(
        """
        DELETE FROM flight_cycles
        WHERE bee_id IN (
            SELECT id FROM bees WHERE observation_point_id = :pointId
        )
        """,
    )
    suspend fun deleteForObservationPoint(pointId: UUID): Int

    @Query(
        """
        SELECT COUNT(*) FROM flight_cycles
        WHERE bee_id IN (
            SELECT id FROM bees WHERE observation_point_id = :pointId
        )
        """,
    )
    suspend fun countForObservationPoint(pointId: UUID): Int

    @Query(
        """
        SELECT flight_cycles.*
        FROM flight_cycles
        INNER JOIN bees ON bees.id = flight_cycles.bee_id
        WHERE bees.observation_point_id = :pointId
        ORDER BY bees.created_at, bees.id, flight_cycles.sequence_number
        """,
    )
    fun observeForObservationPoint(pointId: UUID): Flow<List<FlightCycleEntity>>

    @Query(
        """
        SELECT flight_cycles.*
        FROM flight_cycles
        INNER JOIN bees ON bees.id = flight_cycles.bee_id
        WHERE bees.observation_point_id = :pointId
        ORDER BY bees.created_at, bees.id, flight_cycles.sequence_number
        """,
    )
    suspend fun getForObservationPoint(pointId: UUID): List<FlightCycleEntity>

    @Query(
        """
        UPDATE flight_cycles
        SET return_time = :returnTime,
            initial_group_launch_correction_eligible = 0,
            updated_at = :updatedAt
        WHERE id = :id AND return_time IS NULL
        """,
    )
    suspend fun registerReturn(id: UUID, returnTime: Instant, updatedAt: Instant): Int

    @Query(
        "UPDATE flight_cycles SET return_time = NULL, updated_at = :updatedAt WHERE id = :id AND return_time IS NOT NULL",
    )
    suspend fun clearReturn(id: UUID, updatedAt: Instant): Int

    @Query("DELETE FROM flight_cycles WHERE id = :id")
    suspend fun deleteById(id: UUID): Int

    @Query(
        """
        UPDATE flight_cycles
        SET azimuth_deg = :azimuthDeg,
            azimuth_capture_consumed = 1,
            updated_at = :updatedAt
        WHERE id = :id
          AND return_time IS NULL
          AND azimuth_capture_consumed = 0
          AND azimuth_deg IS NULL
        """,
    )
    suspend fun captureAzimuth(id: UUID, azimuthDeg: Double, updatedAt: Instant): Int

    @Query(
        """
        UPDATE flight_cycles
        SET azimuth_deg = :azimuthDeg, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setAzimuth(id: UUID, azimuthDeg: Double?, updatedAt: Instant): Int

    @Query("SELECT * FROM flight_cycles WHERE bee_id = :beeId ORDER BY sequence_number")
    fun observeForBee(beeId: UUID): Flow<List<FlightCycleEntity>>
}
