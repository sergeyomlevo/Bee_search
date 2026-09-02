package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObserverRequiredException
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.repository.ObservationPointCreator
import org.beesearch.app.domain.repository.SettingsRepository

class CreateObservationPoint(
    private val settingsRepository: SettingsRepository,
    private val pointCreator: ObservationPointCreator,
) {
    suspend fun create(point: NewObservationPoint): ObservationPoint {
        val settings = settingsRepository.getSettings()
        if (settings.currentTerritoryId != point.territoryId) throw TerritoryRequiredException()
        if (settings.currentObserverId != point.observerId) throw ObserverRequiredException()
        return pointCreator.createObservationPoint(point)
    }
}
