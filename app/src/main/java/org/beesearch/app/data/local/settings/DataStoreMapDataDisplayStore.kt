package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.beesearch.app.ui.map.DEFAULT_MAP_DATA_DISPLAY
import org.beesearch.app.ui.map.MapDataDisplayCodec
import org.beesearch.app.ui.map.MapDataDisplayState
import org.beesearch.app.ui.map.MapDataDisplayStore

/**
 * Stores the «Данные на карте» state of each Territory in one `map_data_display_<territoryId>`
 * entry of the existing settings DataStore.
 *
 * Reading a missing or unreadable value yields the approved default state: display state is
 * presentation only, so a damaged entry can never hide research data or make it look deleted. The
 * default state is not stored at all — an entry exists exactly when the user has changed something,
 * which keeps the file free of one entry per visited Territory.
 */
internal class DataStoreMapDataDisplayStore(
    private val dataStore: DataStore<Preferences>,
) : MapDataDisplayStore {
    override suspend fun load(territoryId: UUID): MapDataDisplayState {
        val stored = dataStore.data.first()[key(territoryId)] ?: return DEFAULT_MAP_DATA_DISPLAY
        return MapDataDisplayCodec.decode(stored) ?: DEFAULT_MAP_DATA_DISPLAY
    }

    override suspend fun save(territoryId: UUID, state: MapDataDisplayState) {
        val entry = key(territoryId)
        dataStore.edit { preferences ->
            if (state.isDefault) {
                preferences.remove(entry)
            } else {
                preferences[entry] = MapDataDisplayCodec.encode(state)
            }
        }
    }

    private fun key(territoryId: UUID) = stringPreferencesKey("map_data_display_$territoryId")
}
