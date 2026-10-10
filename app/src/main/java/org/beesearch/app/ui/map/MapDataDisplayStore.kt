package org.beesearch.app.ui.map

import java.util.UUID

/**
 * Persistence of the «Данные на карте» state.
 *
 * The approved contract is per-Territory and persistent: every Territory owns its own visibility and
 * period values, and closing or restarting the app must not require setting them again. A Territory
 * that never had a saved state uses the approved default (everything visible, no period).
 */
internal interface MapDataDisplayStore {
    suspend fun load(territoryId: UUID): MapDataDisplayState

    suspend fun save(territoryId: UUID, state: MapDataDisplayState)
}
