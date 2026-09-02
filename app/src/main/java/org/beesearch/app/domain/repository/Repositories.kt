package org.beesearch.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
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

interface ObservationRepository : ObservationPointCreator {
    fun observeActivePoint(): Flow<ObservationPoint?>
    fun observeBees(pointId: UUID): Flow<List<Bee>>
    fun observeFlightCyclesForPoint(pointId: UUID): Flow<List<FlightCycle>>
    fun observeFlightCycles(beeId: UUID): Flow<List<FlightCycle>>

    suspend fun addBee(pointId: UUID, markColor: String, markPosition: MarkPosition): Bee
    suspend fun removePreparedBee(beeId: UUID)
    suspend fun startInitialGroupRelease(pointId: UUID): List<FlightCycle>
    suspend fun registerBeeReturn(beeId: UUID): FlightCycle
    suspend fun startNextFlight(beeId: UUID): FlightCycle
    suspend fun captureFlightAzimuth(flightCycleId: UUID, azimuthDeg: Double): FlightCycle
    suspend fun setFlightAzimuth(flightCycleId: UUID, azimuthDeg: Double?): FlightCycle
    suspend fun completeObservationPoint(pointId: UUID): ObservationPoint
    suspend fun recordNoBeesFound(pointId: UUID): ObservationPoint
}
