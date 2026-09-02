package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.Observer
import java.util.UUID

sealed interface StartupDestination {
    data object Loading : StartupDestination
    data class ResumeObservation(val point: ObservationPoint) : StartupDestination
    data object ReadyForMap : StartupDestination
    data object SettingsRequired : StartupDestination
}

object StartupRouter {
    fun decide(
        activePoint: ObservationPoint?,
        currentTerritoryId: UUID?,
        territories: List<Territory>,
        currentObserverId: UUID?,
        observers: List<Observer>,
    ): StartupDestination = when {
        activePoint != null -> StartupDestination.ResumeObservation(activePoint)
        else -> StartupDestination.ReadyForMap
    }
}
