package org.beesearch.app

import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.ui.map.MapCoverageStore
import java.util.UUID

/** Coordinates deletion of an unused Territory with its device-local map infrastructure. */
internal class TerritoryCoverageDeletion(
    private val territoryRepository: TerritoryRepository,
    private val coverageStore: MapCoverageStore,
) {
    suspend fun delete(territoryId: UUID) {
        territoryRepository.ensureTerritoryCanBeDeleted(territoryId)
        val savedCoverage = coverageStore.load(territoryId)
        coverageStore.clear(territoryId)
        try {
            territoryRepository.deleteTerritory(territoryId)
        } catch (deleteError: Exception) {
            try {
                coverageStore.replace(territoryId, savedCoverage)
            } catch (restoreError: Exception) {
                deleteError.addSuppressed(restoreError)
            }
            throw deleteError
        }
    }
}
