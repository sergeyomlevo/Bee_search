package org.beesearch.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.BeeDao
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.FlightCycleDao
import org.beesearch.app.data.local.room.FlightCycleEntity
import org.beesearch.app.data.local.room.ObservationPointDao
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverDao
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeLimitReachedException
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeUndoAction
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.AzimuthCaptureAlreadyConsumedException
import org.beesearch.app.domain.model.AzimuthCaptureRequiresOpenFlightCycleException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.InvalidEventTimeException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.NoReversibleBeeActionException
import org.beesearch.app.domain.model.NoBeesFoundAlreadyRecordedException
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.beesearch.app.domain.model.ObservationPointNotCompletedException
import org.beesearch.app.domain.model.StartedBeeFlight
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.repository.ObservationRepository
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

internal class RoomObservationRepository(
    private val database: BeeSearchDatabase,
    private val territoryDao: TerritoryDao,
    private val pointDao: ObservationPointDao,
    private val observerDao: ObserverDao,
    private val beeDao: BeeDao,
    private val cycleDao: FlightCycleDao,
    private val clock: Clock,
    private val observationZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : ObservationRepository {
    override fun observeActivePoint(): Flow<ObservationPoint?> = pointDao.observeActive()
        .map { point -> point?.toDomain() }

    override fun observeBees(pointId: UUID): Flow<List<Bee>> = beeDao.observeForPoint(pointId)
        .map { bees -> bees.map(BeeEntity::toDomain) }

    override fun observeFlightCyclesForPoint(pointId: UUID): Flow<List<FlightCycle>> =
        cycleDao.observeForObservationPoint(pointId)
            .map { cycles -> cycles.map(FlightCycleEntity::toDomain) }

    override fun observeFlightCycles(beeId: UUID): Flow<List<FlightCycle>> =
        cycleDao.observeForBee(beeId).map { cycles -> cycles.map(FlightCycleEntity::toDomain) }

    override suspend fun getObservationDataCounts(): ObservationDataCounts =
        database.withTransaction { observationDataCounts() }

    override suspend fun getCompletedObservationPoints(): List<CompletedObservationPointSummary> =
        pointDao.getCompletedSummaries().map { point ->
            CompletedObservationPointSummary(
                id = point.id,
                createdAt = point.createdAt,
                observationYear = point.observationYear,
                pointNumber = point.pointNumber,
                territoryCode = point.territoryCode,
                territoryName = point.territoryName,
                beeCount = point.beeCount,
            )
        }

    override suspend fun deleteCompletedObservationPoint(pointId: UUID): ObservationDataCounts =
        database.withTransaction {
            val point = pointDao.getById(pointId)
                ?: throw EntityNotFoundException("ObservationPoint")
            if (point.completedAt == null) {
                throw ObservationPointNotCompletedException()
            }

            cycleDao.deleteForObservationPoint(pointId)
            beeDao.deleteForObservationPoint(pointId)
            if (pointDao.deleteCompletedById(pointId) != 1) {
                throw EntityNotFoundException("ObservationPoint")
            }
            observationDataCounts()
        }

    override suspend fun clearObservationData(): ObservationDataCounts =
        database.withTransaction {
            val counts = observationDataCounts()
            cycleDao.deleteAll()
            beeDao.deleteAll()
            pointDao.deleteAll()
            counts
        }

    override suspend fun createObservationPoint(
        point: NewObservationPoint,
    ): ObservationPoint = database.withTransaction {
        createActivePoint(point).toDomain()
    }

    override suspend fun createObservationPointWithNoBeesFound(
        point: NewObservationPoint,
    ): ObservationPoint = database.withTransaction {
        val createdPoint = createActivePoint(point)
        val completedAt = clock.instant()
        if (
            pointDao.recordNoBeesAndComplete(
                id = createdPoint.id,
                result = BeePresenceResult.NO_BEES_FOUND,
                completedAt = completedAt,
            ) != 1
        ) {
            throw ObservationPointNotActiveException()
        }
        createdPoint.copy(
            beePresenceResult = BeePresenceResult.NO_BEES_FOUND,
            completedAt = completedAt,
        ).toDomain()
    }

    private suspend fun createActivePoint(point: NewObservationPoint): ObservationPointEntity {
        if (territoryDao.getById(point.territoryId) == null) {
            throw EntityNotFoundException("Territory")
        }
        if (observerDao.getById(point.observerId) == null) {
            throw EntityNotFoundException("Observer")
        }
        if (pointDao.countActive() != 0) {
            throw ObservationPointAlreadyActiveException()
        }

        val createdAt = clock.instant()
        val observationYear = createdAt.atZone(observationZoneIdProvider()).year
        val pointNumber = pointDao.getNextPointNumber(
            territoryId = point.territoryId,
            observationYear = observationYear,
            observerId = point.observerId,
        )
        val entity = ObservationPointEntity(
            id = UUID.randomUUID(),
            territoryId = point.territoryId,
            observerId = point.observerId,
            observationYear = observationYear,
            pointNumber = pointNumber,
            beePresenceResult = null,
            code = point.code,
            latitude = point.latitude,
            longitude = point.longitude,
            gpsLatitude = point.gpsLatitude,
            gpsLongitude = point.gpsLongitude,
            gpsAccuracyM = point.gpsAccuracyM,
            createdAt = createdAt,
            initialGroupReleaseAt = null,
            completedAt = null,
        )
        pointDao.insert(entity)
        return entity
    }

    override suspend fun startFirstFlight(
        pointId: UUID,
        markColor: String,
        markPosition: MarkPosition,
    ): StartedBeeFlight = database.withTransaction {
        val point = requireActivePoint(pointId)
        if (point.beePresenceResult == BeePresenceResult.NO_BEES_FOUND) {
            throw NoBeesFoundAlreadyRecordedException()
        }
        if (beeDao.countForPoint(pointId) >= BeeMarkCatalog.MAX_BEES_PER_OBSERVATION_POINT) {
            throw BeeLimitReachedException()
        }
        if (beeDao.countByMark(pointId, markColor, markPosition) != 0) {
            throw DuplicateBeeMarkException()
        }

        val departureTime = clock.instant()
        val bee = BeeEntity(
            id = UUID.randomUUID(),
            observationPointId = pointId,
            markColor = markColor,
            markPosition = markPosition,
            createdAt = departureTime,
        )
        beeDao.insert(bee)
        val cycle = FlightCycleEntity(
            id = UUID.randomUUID(),
            beeId = bee.id,
            sequenceNumber = 1,
            departureTime = departureTime,
            returnTime = null,
            azimuthDeg = null,
            azimuthCaptureConsumed = false,
            isInitialGroupLaunch = false,
            isFirstDepartureCancellationEligible = true,
            createdAt = departureTime,
            updatedAt = departureTime,
        )
        cycleDao.insert(cycle)
        if (point.beePresenceResult != BeePresenceResult.BEES_FOUND) {
            if (
                pointDao.setBeePresenceResult(pointId, BeePresenceResult.BEES_FOUND) != 1
            ) {
                throw ObservationPointNotActiveException()
            }
        }
        StartedBeeFlight(bee = bee.toDomain(), flightCycle = cycle.toDomain())
    }

    override suspend fun registerBeeReturn(beeId: UUID): FlightCycle = database.withTransaction {
        val bee = requireBee(beeId)
        requireActivePoint(bee.observationPointId)
        val openCycle = cycleDao.getOpenForBee(beeId) ?: throw OpenFlightCycleNotFoundException()
        val returnTime = clock.instant()
        if (returnTime < openCycle.departureTime) {
            throw InvalidEventTimeException()
        }
        if (cycleDao.registerReturn(openCycle.id, returnTime, returnTime) != 1) {
            throw OpenFlightCycleNotFoundException()
        }
        openCycle.copy(
            returnTime = returnTime,
            isFirstDepartureCancellationEligible = false,
            updatedAt = returnTime,
        ).toDomain()
    }

    override suspend fun startNextFlight(beeId: UUID): FlightCycle = database.withTransaction {
        val bee = requireBee(beeId)
        requireActivePoint(bee.observationPointId)
        if (cycleDao.getOpenForBee(beeId) != null) {
            throw OpenFlightCycleExistsException()
        }
        val previousSequence = cycleDao.getMaximumSequenceNumber(beeId)
        val departureTime = clock.instant()
        val entity = FlightCycleEntity(
            id = UUID.randomUUID(),
            beeId = beeId,
            sequenceNumber = (previousSequence ?: 0) + 1,
            departureTime = departureTime,
            returnTime = null,
            azimuthDeg = null,
            azimuthCaptureConsumed = false,
            isInitialGroupLaunch = false,
            isFirstDepartureCancellationEligible = false,
            createdAt = departureTime,
            updatedAt = departureTime,
        )
        cycleDao.insert(entity)
        entity.toDomain()
    }

    override suspend fun captureFlightAzimuth(
        flightCycleId: UUID,
        azimuthDeg: Double,
    ): FlightCycle = database.withTransaction {
        if (azimuthDeg < 0.0 || azimuthDeg >= 360.0) {
            throw InvalidAzimuthException()
        }
        val cycle = cycleDao.getById(flightCycleId)
            ?: throw EntityNotFoundException("FlightCycle")
        val bee = requireBee(cycle.beeId)
        requireActivePoint(bee.observationPointId)
        if (cycle.returnTime != null) {
            throw AzimuthCaptureRequiresOpenFlightCycleException()
        }
        if (cycle.azimuthCaptureConsumed || cycle.azimuthDeg != null) {
            throw AzimuthCaptureAlreadyConsumedException()
        }
        val updatedAt = clock.instant()
        if (cycleDao.captureAzimuth(flightCycleId, azimuthDeg, updatedAt) != 1) {
            throw AzimuthCaptureAlreadyConsumedException()
        }
        cycle.copy(
            azimuthDeg = azimuthDeg,
            azimuthCaptureConsumed = true,
            updatedAt = updatedAt,
        ).toDomain()
    }

    override suspend fun setFlightAzimuth(
        flightCycleId: UUID,
        azimuthDeg: Double?,
    ): FlightCycle = database.withTransaction {
        if (azimuthDeg != null && (azimuthDeg < 0.0 || azimuthDeg >= 360.0)) {
            throw InvalidAzimuthException()
        }
        val cycle = cycleDao.getById(flightCycleId) ?: throw EntityNotFoundException("FlightCycle")
        val bee = requireBee(cycle.beeId)
        requireActivePoint(bee.observationPointId)
        val updatedAt = clock.instant()
        if (cycleDao.setAzimuth(flightCycleId, azimuthDeg, updatedAt) != 1) {
            throw EntityNotFoundException("FlightCycle")
        }
        cycle.copy(azimuthDeg = azimuthDeg, updatedAt = updatedAt).toDomain()
    }

    override suspend fun undoLastBeeAction(beeId: UUID): BeeUndoAction = database.withTransaction {
        val bee = requireBee(beeId)
        requireActivePoint(bee.observationPointId)
        val latest = cycleDao.getLatestForBee(beeId) ?: throw NoReversibleBeeActionException()
        val updatedAt = clock.instant()

        when {
            // A recorded return happened after any azimuth captured during the flight.
            // It is therefore the latest reversible event for this Bee.
            latest.returnTime != null -> {
                if (cycleDao.clearReturn(latest.id, updatedAt) != 1) {
                    throw NoReversibleBeeActionException()
                }
                BeeUndoAction.RETURN
            }
            latest.azimuthDeg != null -> {
                if (cycleDao.setAzimuth(latest.id, null, updatedAt) != 1) {
                    throw NoReversibleBeeActionException()
                }
                BeeUndoAction.AZIMUTH
            }
            latest.sequenceNumber > 1 -> {
                if (cycleDao.deleteById(latest.id) != 1) {
                    throw NoReversibleBeeActionException()
                }
                BeeUndoAction.NEXT_FLIGHT
            }
            latest.sequenceNumber == 1 && latest.isFirstDepartureCancellationEligible -> {
                if (cycleDao.deleteById(latest.id) != 1) {
                    throw NoReversibleBeeActionException()
                }
                if (beeDao.delete(bee) != 1) {
                    throw NoReversibleBeeActionException()
                }
                if (beeDao.countForPoint(bee.observationPointId) == 0) {
                    if (pointDao.setBeePresenceResult(bee.observationPointId, null) != 1) {
                        throw ObservationPointNotActiveException()
                    }
                }
                BeeUndoAction.FIRST_DEPARTURE
            }
            else -> throw NoReversibleBeeActionException()
        }
    }

    override suspend fun completeObservationPoint(pointId: UUID): ObservationPoint =
        database.withTransaction {
            val point = requireActivePoint(pointId)
            if (point.beePresenceResult == null) {
                throw BeePresenceResultRequiredException()
            }
            val completedAt = clock.instant()
            if (completedAt < point.createdAt) {
                throw InvalidEventTimeException()
            }
            if (pointDao.complete(pointId, completedAt) != 1) {
                throw ObservationPointNotActiveException()
            }
            point.copy(completedAt = completedAt).toDomain()
        }

    override suspend fun recordNoBeesFound(pointId: UUID): ObservationPoint =
        database.withTransaction {
            val point = requireActivePoint(pointId)
            if (
                point.beePresenceResult == BeePresenceResult.BEES_FOUND ||
                beeDao.countForPoint(pointId) != 0
            ) {
                throw BeesAlreadyFoundException()
            }
            val completedAt = clock.instant()
            if (completedAt < point.createdAt) {
                throw InvalidEventTimeException()
            }
            if (
                pointDao.recordNoBeesAndComplete(
                    id = pointId,
                    result = BeePresenceResult.NO_BEES_FOUND,
                    completedAt = completedAt,
                ) != 1
            ) {
                throw ObservationPointNotActiveException()
            }
            point.copy(
                beePresenceResult = BeePresenceResult.NO_BEES_FOUND,
                completedAt = completedAt,
            ).toDomain()
        }

    private suspend fun requireActivePoint(pointId: UUID): ObservationPointEntity {
        val point = pointDao.getById(pointId) ?: throw EntityNotFoundException("ObservationPoint")
        if (point.completedAt != null) {
            throw ObservationPointNotActiveException()
        }
        return point
    }

    private suspend fun requireBee(beeId: UUID): BeeEntity =
        beeDao.getById(beeId) ?: throw EntityNotFoundException("Bee")

    private suspend fun observationDataCounts() = ObservationDataCounts(
        observationPoints = pointDao.countAll(),
        bees = beeDao.countAll(),
        flightCycles = cycleDao.countAll(),
    )

}
