package org.beesearch.app.data.local.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.BeePresenceResult
import java.time.Instant
import java.util.UUID

@Dao
internal abstract class BackupDao {
    @Query("SELECT * FROM territories ORDER BY id") abstract suspend fun territories(): List<TerritoryEntity>
    @Query("SELECT * FROM observers ORDER BY id") abstract suspend fun observers(): List<ObserverEntity>
    @Query("SELECT * FROM observation_points ORDER BY id") abstract suspend fun observationPoints(): List<ObservationPointEntity>
    @Query("SELECT * FROM bees ORDER BY id") abstract suspend fun bees(): List<BeeEntity>
    @Query("SELECT * FROM flight_cycles ORDER BY id") abstract suspend fun flightCycles(): List<FlightCycleEntity>
    @Query("SELECT COUNT(*) FROM territories") abstract suspend fun territoryCount(): Int
    @Query("SELECT COUNT(*) FROM observers") abstract suspend fun observerCount(): Int
    @Query("SELECT COUNT(*) FROM observation_points") abstract suspend fun observationPointCount(): Int
    @Query("SELECT COUNT(*) FROM bees") abstract suspend fun beeCount(): Int
    @Query("SELECT COUNT(*) FROM flight_cycles") abstract suspend fun flightCycleCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertTerritories(value: List<TerritoryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservers(value: List<ObserverEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertObservationPoints(value: List<ObservationPointEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertBees(value: List<BeeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFlightCycles(value: List<FlightCycleEntity>)
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
}

@Dao
internal interface ObservationPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: ObservationPointEntity)

    @Query("SELECT * FROM observation_points WHERE id = :id")
    suspend fun getById(id: UUID): ObservationPointEntity?

    @Query("SELECT COUNT(*) FROM observation_points WHERE completed_at IS NULL")
    suspend fun countActive(): Int

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
internal interface BeeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bee: BeeEntity)

    @Delete
    suspend fun delete(bee: BeeEntity): Int

    @Query("SELECT * FROM bees WHERE id = :id")
    suspend fun getById(id: UUID): BeeEntity?

    @Query("SELECT * FROM bees WHERE observation_point_id = :pointId ORDER BY created_at, id")
    suspend fun getForPoint(pointId: UUID): List<BeeEntity>

    @Query("SELECT COUNT(*) FROM bees WHERE observation_point_id = :pointId")
    suspend fun countForPoint(pointId: UUID): Int

    @Query("SELECT * FROM bees WHERE observation_point_id = :pointId ORDER BY created_at, id")
    fun observeForPoint(pointId: UUID): Flow<List<BeeEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM bees
        WHERE observation_point_id = :pointId
          AND mark_color = :markColor
          AND mark_position = :markPosition
        """,
    )
    suspend fun countByMark(
        pointId: UUID,
        markColor: String,
        markPosition: org.beesearch.app.domain.model.MarkPosition,
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
