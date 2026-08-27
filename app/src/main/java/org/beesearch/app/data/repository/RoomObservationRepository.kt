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
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeHasFlightHistoryException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.InitialFlightCycleRequiredException
import org.beesearch.app.domain.model.InitialReleaseAlreadyStartedException
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.InvalidEventTimeException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.NoPreparedBeesException
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.validation.ObserverCode
import java.time.Clock
import java.util.UUID

internal class RoomObservationRepository(
    private val database: BeeSearchDatabase,
    private val territoryDao: TerritoryDao,
    private val pointDao: ObservationPointDao,
    private val beeDao: BeeDao,
    private val cycleDao: FlightCycleDao,
    private val clock: Clock,
) : ObservationRepository {
    override fun observeActivePoint(): Flow<ObservationPoint?> = pointDao.observeActive()
        .map { point -> point?.toDomain() }

    override fun observeBees(pointId: UUID): Flow<List<Bee>> = beeDao.observeForPoint(pointId)
        .map { bees -> bees.map(BeeEntity::toDomain) }

    override fun observeHasFlightCycles(pointId: UUID): Flow<Boolean> =
        cycleDao.observeCountForObservationPoint(pointId).map { count -> count > 0 }

    override fun observeFlightCycles(beeId: UUID): Flow<List<FlightCycle>> =
        cycleDao.observeForBee(beeId).map { cycles -> cycles.map(FlightCycleEntity::toDomain) }

    override suspend fun createObservationPoint(
        point: NewObservationPoint,
        observerCode: String,
    ): ObservationPoint = database.withTransaction {
        if (territoryDao.getById(point.territoryId) == null) {
            throw EntityNotFoundException("Territory")
        }
        if (pointDao.countActive() != 0) {
            throw ObservationPointAlreadyActiveException()
        }

        val entity = ObservationPointEntity(
            id = UUID.randomUUID(),
            territoryId = point.territoryId,
            observerCode = ObserverCode.normalize(observerCode),
            code = point.code,
            latitude = point.latitude,
            longitude = point.longitude,
            gpsLatitude = point.gpsLatitude,
            gpsLongitude = point.gpsLongitude,
            gpsAccuracyM = point.gpsAccuracyM,
            createdAt = clock.instant(),
            completedAt = null,
        )
        pointDao.insert(entity)
        entity.toDomain()
    }

    override suspend fun addBee(
        pointId: UUID,
        markColor: String,
        markPosition: MarkPosition,
    ): Bee = database.withTransaction {
        requireActivePoint(pointId)
        if (cycleDao.countForObservationPoint(pointId) != 0) {
            throw InitialReleaseAlreadyStartedException()
        }
        if (beeDao.countByMark(pointId, markColor, markPosition) != 0) {
            throw DuplicateBeeMarkException()
        }

        val entity = BeeEntity(
            id = UUID.randomUUID(),
            observationPointId = pointId,
            markColor = markColor,
            markPosition = markPosition,
            createdAt = clock.instant(),
        )
        beeDao.insert(entity)
        entity.toDomain()
    }

    override suspend fun removePreparedBee(beeId: UUID) {
        database.withTransaction {
            val bee = requireBee(beeId)
            requireActivePoint(bee.observationPointId)
            if (cycleDao.countForObservationPoint(bee.observationPointId) != 0) {
                throw InitialReleaseAlreadyStartedException()
            }
            if (cycleDao.countForBee(beeId) != 0) {
                throw BeeHasFlightHistoryException()
            }
            beeDao.delete(bee)
        }
    }

    override suspend fun startInitialGroupRelease(pointId: UUID): List<FlightCycle> =
        database.withTransaction {
            requireActivePoint(pointId)
            val bees = beeDao.getForPoint(pointId)
            if (bees.isEmpty()) {
                throw NoPreparedBeesException()
            }
            if (cycleDao.countForObservationPoint(pointId) != 0) {
                throw InitialReleaseAlreadyStartedException()
            }

            val departureTime = clock.instant()
            val cycles = bees.map { bee ->
                FlightCycleEntity(
                    id = UUID.randomUUID(),
                    beeId = bee.id,
                    sequenceNumber = 1,
                    departureTime = departureTime,
                    returnTime = null,
                    azimuthDeg = null,
                    createdAt = departureTime,
                    updatedAt = departureTime,
                )
            }
            cycleDao.insertAll(cycles)
            cycles.map(FlightCycleEntity::toDomain)
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
        openCycle.copy(returnTime = returnTime, updatedAt = returnTime).toDomain()
    }

    override suspend fun startNextFlight(beeId: UUID): FlightCycle = database.withTransaction {
        val bee = requireBee(beeId)
        requireActivePoint(bee.observationPointId)
        if (cycleDao.getOpenForBee(beeId) != null) {
            throw OpenFlightCycleExistsException()
        }
        val previousSequence = cycleDao.getMaximumSequenceNumber(beeId)
            ?: throw InitialFlightCycleRequiredException()
        val departureTime = clock.instant()
        val entity = FlightCycleEntity(
            id = UUID.randomUUID(),
            beeId = beeId,
            sequenceNumber = previousSequence + 1,
            departureTime = departureTime,
            returnTime = null,
            azimuthDeg = null,
            createdAt = departureTime,
            updatedAt = departureTime,
        )
        cycleDao.insert(entity)
        entity.toDomain()
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

    override suspend fun completeObservationPoint(pointId: UUID): ObservationPoint =
        database.withTransaction {
            val point = requireActivePoint(pointId)
            val completedAt = clock.instant()
            if (completedAt < point.createdAt) {
                throw InvalidEventTimeException()
            }
            if (pointDao.complete(pointId, completedAt) != 1) {
                throw ObservationPointNotActiveException()
            }
            point.copy(completedAt = completedAt).toDomain()
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
}
