package org.beesearch.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.BeeDao
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.FlightCycleDao
import org.beesearch.app.data.local.room.FlightCycleEntity
import org.beesearch.app.data.local.room.ObservationPointDao
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObservationPointAttachmentDao
import org.beesearch.app.data.local.room.ObservationPointAttachmentEntity
import org.beesearch.app.data.local.room.ObservationPointWeatherDao
import org.beesearch.app.data.local.room.ObservationPointWeatherEntity
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
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.beesearch.app.domain.model.ObservationPointNotCompletedException
import org.beesearch.app.domain.model.StartedBeeFlight
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.PendingWeatherRequest
import org.beesearch.app.domain.model.WeatherStatus
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
    private val attachmentDao: ObservationPointAttachmentDao,
    private val weatherDao: ObservationPointWeatherDao,
    private val clock: Clock,
    private val observationZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : ObservationRepository {
    constructor(
        database: BeeSearchDatabase,
        territoryDao: TerritoryDao,
        pointDao: ObservationPointDao,
        observerDao: ObserverDao,
        beeDao: BeeDao,
        cycleDao: FlightCycleDao,
        clock: Clock,
        observationZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    ) : this(
        database = database,
        territoryDao = territoryDao,
        pointDao = pointDao,
        observerDao = observerDao,
        beeDao = beeDao,
        cycleDao = cycleDao,
        attachmentDao = database.observationPointAttachmentDao(),
        weatherDao = database.observationPointWeatherDao(),
        clock = clock,
        observationZoneIdProvider = observationZoneIdProvider,
    )

    override fun observeObservationPointSummaries(
        territoryId: UUID,
        observationYear: Int?,
    ): Flow<List<ObservationPointSummary>> = pointDao.observeSummaries(territoryId, observationYear)
        .map { rows ->
            rows.map { row ->
                ObservationPointSummary(
                    id = row.id,
                    territoryId = row.territoryId,
                    observationYear = row.observationYear,
                    pointNumber = row.pointNumber,
                    code = row.code,
                    beePresenceResult = row.beePresenceResult,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    gpsAccuracyM = row.gpsAccuracyM,
                    createdAt = row.createdAt,
                    completedAt = row.completedAt,
                    beeCount = row.beeCount,
                    completedFlightCycleCount = row.completedFlightCycleCount,
                )
            }
        }

    override suspend fun getObservationPointDetail(pointId: UUID): ObservationPointDetail? =
        database.withTransaction {
            val point = pointDao.getById(pointId) ?: return@withTransaction null
            val territory = territoryDao.getById(point.territoryId) ?: return@withTransaction null
            val observer = observerDao.getById(point.observerId) ?: return@withTransaction null
            val bees = beeDao.getForPoint(pointId)
            val cyclesByBee = cycleDao.getForObservationPoint(pointId).groupBy { it.beeId }
            ObservationPointDetail(
                point = point.toDomain(),
                territory = territory.toDomain(),
                observer = observer.toDomain(),
                beeHistories = bees.map { bee ->
                    BeeObservationHistory(
                        bee = bee.toDomain(),
                        flightCycles = cyclesByBee[bee.id].orEmpty().map(FlightCycleEntity::toDomain),
                    )
                },
                weather = weatherDao.getByPointId(pointId)?.toDomain(),
                attachments = attachmentDao.getForPoint(pointId).map(ObservationPointAttachmentEntity::toDomain),
            )
        }

    override fun observeObservationPointProperties(pointId: UUID): Flow<ObservationPointDetail?> =
        combine(
            pointDao.observeById(pointId),
            attachmentDao.observeForPoint(pointId),
            weatherDao.observeByPointId(pointId),
        ) { point, _, _ ->
            if (point == null) null else getObservationPointDetail(pointId)
        }

    override suspend fun updateObservationPointDescription(pointId: UUID, description: String?): ObservationPoint =
        database.withTransaction {
            val normalized = description?.takeUnless { it.isEmpty() }
            if (pointDao.updateDescription(pointId, normalized) != 1) throw EntityNotFoundException("ObservationPoint")
            pointDao.getById(pointId)!!.toDomain()
        }

    override suspend fun listObservationPointAttachments(pointId: UUID): List<ObservationPointAttachment> =
        attachmentDao.getForPoint(pointId).map(ObservationPointAttachmentEntity::toDomain)

    override suspend fun listAllObservationPointAttachments(): List<ObservationPointAttachment> =
        attachmentDao.getAll().map(ObservationPointAttachmentEntity::toDomain)

    override suspend fun insertObservationPointAttachment(attachment: ObservationPointAttachment) {
        require(!attachment.relativePath.startsWith("/") && !Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(attachment.relativePath))
        require(attachment.byteSize >= 0L && attachment.sha256.matches(Regex("[0-9a-f]{64}")))
        attachmentDao.insert(ObservationPointAttachmentEntity(
            attachment.id, attachment.observationPointId, attachment.type, attachment.relativePath,
            attachment.originalFileName, attachment.mimeType, attachment.byteSize, attachment.sha256, attachment.createdAt,
        ))
    }

    override suspend fun deleteObservationPointAttachment(attachmentId: UUID): ObservationPointAttachment? = database.withTransaction {
        val existing = attachmentDao.getById(attachmentId) ?: return@withTransaction null
        attachmentDao.deleteById(attachmentId)
        existing.toDomain()
    }

    override suspend fun getObservationPointWeather(pointId: UUID): ObservationPointWeather? =
        weatherDao.getByPointId(pointId)?.toDomain()

    override suspend fun getPendingWeatherRequests(): List<PendingWeatherRequest> =
        weatherDao.getPending().mapNotNull { weather ->
            pointDao.getById(weather.observationPointId)?.let { point ->
                PendingWeatherRequest(point.id, point.latitude, point.longitude, point.createdAt)
            }
        }

    override suspend fun storeLoadedWeather(pointId: UUID, weather: ObservationPointWeather): Boolean {
        require(weather.observationPointId == pointId && weather.status == WeatherStatus.LOADED)
        val temperature = requireNotNull(weather.temperatureC)
        val speed = requireNotNull(weather.windSpeedMps)
        val direction = requireNotNull(weather.windDirectionDeg)
        require(temperature.isFinite() && speed.isFinite() && speed >= 0.0 && direction.isFinite() && direction >= 0.0 && direction < 360.0)
        val sampleAt = requireNotNull(weather.sampleAt)
        val fetchedAt = requireNotNull(weather.fetchedAt)
        val source = requireNotNull(weather.source).takeIf { it.isNotBlank() } ?: error("weather source is required")
        return weatherDao.storeLoaded(pointId, WeatherStatus.LOADED, temperature, speed, direction, sampleAt, fetchedAt, source) == 1
    }

    override suspend fun markWeatherUnavailable(pointId: UUID): Boolean = weatherDao.markUnavailable(pointId) == 1
    override suspend fun resetWeatherPending(pointId: UUID): Boolean = weatherDao.resetPending(pointId) == 1

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
            attachmentDao.deleteForPoint(pointId)
            weatherDao.deleteForPoint(pointId)
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
            attachmentDao.deleteAll()
            weatherDao.deleteAll()
            pointDao.deleteAll()
            counts
        }

    override suspend fun createObservationPoint(
        point: NewObservationPoint,
    ): ObservationPoint = database.withTransaction {
        createActivePoint(point).also { insertCreationAttachments(point) }.toDomain()
    }

    override suspend fun createObservationPointWithNoBeesFound(
        point: NewObservationPoint,
    ): ObservationPoint = database.withTransaction {
        val createdPoint = createActivePoint(point)
        insertCreationAttachments(point)
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
            id = point.id,
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
            description = point.description?.trimEnd()?.ifBlank { null },
        )
        pointDao.insert(entity)
        weatherDao.insert(ObservationPointWeatherEntity(entity.id, WeatherStatus.PENDING, null, null, null, null, null, null))
        return entity
    }

    private suspend fun insertCreationAttachments(point: NewObservationPoint) {
        point.attachments.forEach { attachment ->
            require(attachment.observationPointId == point.id)
            require(
                attachment.relativePath ==
                    org.beesearch.app.data.media.ObservationAttachmentFileStore.relativePath(point.id, attachment.id),
            )
            require(attachment.byteSize > 0L && attachment.sha256.matches(Regex("[0-9a-f]{64}")))
            attachmentDao.insert(
                ObservationPointAttachmentEntity(
                    attachment.id,
                    attachment.observationPointId,
                    attachment.type,
                    attachment.relativePath,
                    attachment.originalFileName,
                    attachment.mimeType,
                    attachment.byteSize,
                    attachment.sha256,
                    attachment.createdAt,
                ),
            )
        }
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
        // Matching on every persisted token of the position keeps duplicate-mark
        // protection correct for rows written before the thorax/abdomen marking
        // system, where the same real position was stored under a legacy token.
        if (beeDao.countByMark(pointId, markColor, markPosition.persistedTokens) != 0) {
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
