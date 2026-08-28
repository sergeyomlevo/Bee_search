package org.beesearch.app.data.local.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.BeePresenceResult
import java.time.Instant
import java.util.UUID

@Dao
internal interface TerritoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(territory: TerritoryEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(territory: TerritoryEntity): Int

    @Query("SELECT * FROM territories WHERE id = :id")
    suspend fun getById(id: UUID): TerritoryEntity?

    @Query("SELECT * FROM territories ORDER BY code COLLATE NOCASE, id")
    fun observeAll(): Flow<List<TerritoryEntity>>
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
          AND observer_code = :observerCode
        """,
    )
    suspend fun getNextPointNumber(
        territoryId: UUID,
        observationYear: Int,
        observerCode: String,
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
        SET return_time = :returnTime, updated_at = :updatedAt
        WHERE id = :id AND return_time IS NULL
        """,
    )
    suspend fun registerReturn(id: UUID, returnTime: Instant, updatedAt: Instant): Int

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
