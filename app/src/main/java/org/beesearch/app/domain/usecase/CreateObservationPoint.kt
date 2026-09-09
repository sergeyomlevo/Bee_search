package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObserverRequiredException
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.repository.ObservationPointPreparationCreator
import org.beesearch.app.domain.repository.SettingsRepository

class CreateObservationPoint(
    private val settingsRepository: SettingsRepository,
    private val pointCreator: ObservationPointPreparationCreator,
) {
    suspend fun create(point: NewObservationPoint): ObservationPoint {
        requireCurrentSelection(point)
        return pointCreator.createObservationPoint(point)
    }

    suspend fun createWithFirstBee(
        point: NewObservationPoint,
        markColor: String,
        markPosition: MarkPosition,
    ): ObservationPoint {
        requireCurrentSelection(point)
        return pointCreator.createObservationPointWithFirstBee(point, markColor, markPosition)
    }

    suspend fun createWithNoBeesFound(point: NewObservationPoint): ObservationPoint {
        requireCurrentSelection(point)
        return pointCreator.createObservationPointWithNoBeesFound(point)
    }

    private suspend fun requireCurrentSelection(point: NewObservationPoint) {
        val settings = settingsRepository.getSettings()
        if (settings.currentTerritoryId != point.territoryId) throw TerritoryRequiredException()
        if (settings.currentObserverId != point.observerId) throw ObserverRequiredException()
    }
}
