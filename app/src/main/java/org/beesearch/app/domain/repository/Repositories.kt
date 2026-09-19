package org.beesearch.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeUndoAction
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.StartedBeeFlight
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.PendingWeatherRequest
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.ObservationPointDetail
import java.util.UUID

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun getSettings(): AppSettings
    suspend fun setCurrentTerritoryId(territoryId: UUID?)
    suspend fun setCurrentObserverId(observerId: UUID?)
}

interface TerritoryRepository {
    fun observeTerritories(): Flow<List<Territory>>
    suspend fun getTerritory(id: UUID): Territory?
    suspend fun createTerritory(code: String, name: String, region: String, district: String): Territory
    suspend fun updateTerritory(territory: Territory): Territory
    suspend fun ensureTerritoryCanBeDeleted(id: UUID)
    suspend fun deleteTerritory(id: UUID)
}

interface ObserverRepository {
    fun observeObservers(): Flow<List<Observer>>
    suspend fun getObserver(id: UUID): Observer?
    suspend fun createObserver(
        code: String,
        lastName: String,
        firstName: String,
        middleName: String?,
        contact: String?,
    ): Observer
    suspend fun updateObserver(observer: Observer): Observer
    suspend fun deleteObserver(id: UUID)
}

interface ObservationPointCreator {
    suspend fun createObservationPoint(
        point: NewObservationPoint,
    ): ObservationPoint
}

interface ObservationPointPreparationCreator : ObservationPointCreator {
    suspend fun createObservationPointWithNoBeesFound(
        point: NewObservationPoint,
    ): ObservationPoint
}

interface ObservationDataMaintenance {
    suspend fun getObservationDataCounts(): ObservationDataCounts
    suspend fun getCompletedObservationPoints(): List<CompletedObservationPointSummary>
    suspend fun deleteCompletedObservationPoint(pointId: UUID): ObservationDataCounts
    suspend fun clearObservationData(): ObservationDataCounts
}

interface WeatherBackfillStore {
    suspend fun getPendingWeatherRequests(): List<PendingWeatherRequest>
    suspend fun storeLoadedWeather(pointId: UUID, weather: ObservationPointWeather): Boolean
    suspend fun markWeatherUnavailable(pointId: UUID): Boolean
}

interface ObservationRepository : ObservationPointPreparationCreator, ObservationDataMaintenance, WeatherBackfillStore {
    fun observeObservationPointSummaries(
        territoryId: UUID,
        observationYear: Int? = null,
    ): Flow<List<ObservationPointSummary>>
    suspend fun getObservationPointDetail(pointId: UUID): ObservationPointDetail?
    fun observeActivePoint(): Flow<ObservationPoint?>
    fun observeBees(pointId: UUID): Flow<List<Bee>>
    fun observeFlightCyclesForPoint(pointId: UUID): Flow<List<FlightCycle>>
    fun observeFlightCycles(beeId: UUID): Flow<List<FlightCycle>>

    suspend fun startFirstFlight(
        pointId: UUID,
        markColor: String,
        markPosition: MarkPosition,
    ): StartedBeeFlight
    suspend fun registerBeeReturn(beeId: UUID): FlightCycle
    suspend fun startNextFlight(beeId: UUID): FlightCycle
    suspend fun captureFlightAzimuth(flightCycleId: UUID, azimuthDeg: Double): FlightCycle
    suspend fun setFlightAzimuth(flightCycleId: UUID, azimuthDeg: Double?): FlightCycle
    suspend fun undoLastBeeAction(beeId: UUID): BeeUndoAction
    suspend fun completeObservationPoint(pointId: UUID): ObservationPoint
    suspend fun recordNoBeesFound(pointId: UUID): ObservationPoint

    fun observeObservationPointProperties(pointId: UUID): Flow<ObservationPointDetail?>
    suspend fun updateObservationPointDescription(pointId: UUID, description: String?): ObservationPoint
    suspend fun listObservationPointAttachments(pointId: UUID): List<ObservationPointAttachment>
    suspend fun listAllObservationPointAttachments(): List<ObservationPointAttachment>
    suspend fun insertObservationPointAttachment(attachment: ObservationPointAttachment)
    suspend fun deleteObservationPointAttachment(attachmentId: UUID): ObservationPointAttachment?
    suspend fun getObservationPointWeather(pointId: UUID): ObservationPointWeather?
    suspend fun resetWeatherPending(pointId: UUID): Boolean
}
