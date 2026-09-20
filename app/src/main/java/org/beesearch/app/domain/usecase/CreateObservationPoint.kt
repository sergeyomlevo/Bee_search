package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObserverRequiredException
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.repository.ObservationPointPreparationCreator
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.weather.WeatherSyncScheduler

class CreateObservationPoint(
    private val settingsRepository: SettingsRepository,
    private val pointCreator: ObservationPointPreparationCreator,
    private val weatherSyncScheduler: WeatherSyncScheduler = WeatherSyncScheduler { },
) {
    suspend fun create(point: NewObservationPoint): ObservationPoint {
        requireCurrentSelection(point)
        return pointCreator.createObservationPoint(point).also { runCatching { weatherSyncScheduler.enqueue() } }
    }

    suspend fun createWithNoBeesFound(point: NewObservationPoint): ObservationPoint {
        requireCurrentSelection(point)
        return pointCreator.createObservationPointWithNoBeesFound(point).also {
            runCatching { weatherSyncScheduler.enqueue() }
        }
    }

    private suspend fun requireCurrentSelection(point: NewObservationPoint) {
        val settings = settingsRepository.getSettings()
        if (settings.currentTerritoryId != point.territoryId) throw TerritoryRequiredException()
        if (settings.currentObserverId != point.observerId) throw ObserverRequiredException()
    }
}
