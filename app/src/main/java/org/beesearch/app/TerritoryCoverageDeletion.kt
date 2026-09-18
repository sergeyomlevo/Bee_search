package org.beesearch.app

import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.ui.map.MapAreaStore
import java.util.UUID

/** Coordinates deletion of an unused Territory with its device-local map infrastructure. */
internal class TerritoryCoverageDeletion(
    private val territoryRepository: TerritoryRepository,
    private val areaStore: MapAreaStore,
) {
    suspend fun delete(territoryId: UUID) {
        territoryRepository.ensureTerritoryCanBeDeleted(territoryId)
        // The exact stored value is preserved, so even a damaged one survives a failed deletion.
        val savedValue = areaStore.snapshot(territoryId)
        areaStore.clear(territoryId)
        try {
            territoryRepository.deleteTerritory(territoryId)
        } catch (deleteError: Exception) {
            try {
                areaStore.restore(territoryId, savedValue)
            } catch (restoreError: Exception) {
                deleteError.addSuppressed(restoreError)
            }
            throw deleteError
        }
    }
}
