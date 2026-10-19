package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.beesearch.app.ui.map.GpxVisibilityStore

/** Explicit selected IDs: a future/new import cannot become visible by absence from a hidden list. */
internal class DataStoreGpxVisibilityStore(private val dataStore: DataStore<Preferences>) : GpxVisibilityStore {
    override suspend fun load(territoryId: UUID): Set<UUID> =
        dataStore.data.first()[key(territoryId)].orEmpty().map { UUID.fromString(it) }.toSet()

    override suspend fun setVisible(territoryId: UUID, importId: UUID, visible: Boolean) {
        dataStore.edit { preferences ->
            val key = key(territoryId)
            val selected = preferences[key].orEmpty().toMutableSet()
            if (visible) selected.add(importId.toString()) else selected.remove(importId.toString())
            if (selected.isEmpty()) preferences.remove(key) else preferences[key] = selected.toSet()
        }
    }
    private fun key(territoryId: UUID) = stringSetPreferencesKey("gpx_visible_imports_$territoryId")
}
