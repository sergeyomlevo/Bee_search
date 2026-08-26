package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import java.util.UUID

sealed interface StartupDestination {
    data object Loading : StartupDestination
    data class ResumeObservation(val point: ObservationPoint) : StartupDestination
    data class CurrentTerritory(val territory: Territory) : StartupDestination
    data object TerritoryManagement : StartupDestination
}

object StartupRouter {
    fun decide(
        activePoint: ObservationPoint?,
        currentTerritoryId: UUID?,
        territories: List<Territory>,
    ): StartupDestination = when {
        activePoint != null -> StartupDestination.ResumeObservation(activePoint)
        currentTerritoryId != null -> territories
            .firstOrNull { it.id == currentTerritoryId }
            ?.let(StartupDestination::CurrentTerritory)
            ?: StartupDestination.TerritoryManagement
        else -> StartupDestination.TerritoryManagement
    }
}
