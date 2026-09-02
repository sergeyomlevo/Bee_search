package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.beesearch.app.ui.map.MapCoverageCodec
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapCoverageStore
import java.io.IOException
import java.util.UUID

internal class DataStoreMapCoverageStore(
    private val dataStore: DataStore<Preferences>,
) : MapCoverageStore {
    override suspend fun load(territoryId: UUID): List<MapCoverageFragment> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()[coverageKey(territoryId)]
        .let(MapCoverageCodec::decode)

    override suspend fun replace(territoryId: UUID, fragments: List<MapCoverageFragment>) {
        dataStore.edit { preferences ->
            preferences[coverageKey(territoryId)] = MapCoverageCodec.encode(fragments)
        }
    }

    override suspend fun clear(territoryId: UUID) {
        dataStore.edit { preferences ->
            preferences.remove(coverageKey(territoryId))
        }
    }

    private fun coverageKey(territoryId: UUID) = stringPreferencesKey("map_coverage_$territoryId")
}
