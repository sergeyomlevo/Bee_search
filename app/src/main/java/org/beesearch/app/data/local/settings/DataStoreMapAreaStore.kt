package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.beesearch.app.ui.map.CORRUPT_AREA_MESSAGE
import org.beesearch.app.ui.map.EMPTY_AREA_MESSAGE
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaSaveResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.migratedMapArea

/**
 * Stores the device-local Ареал of each Territory in the existing `map_coverage_<territoryId>`
 * entry.
 *
 * A readable pre-Ареал selection is migrated in place by the first read: the edit that rewrites the
 * value and the edit that returns the new id happen in one DataStore transaction, so the generated
 * id becomes stable immediately and a failed migration cannot leave a partial value behind. A
 * damaged value is never migrated over, never rewritten and never reported as "no Ареал".
 */
internal class DataStoreMapAreaStore(
    private val dataStore: DataStore<Preferences>,
) : MapAreaStore {
    override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult {
        val key = areaKey(territoryId)
        val decoded = MapAreaCodec.decode(readValue(key))
        // Only a readable legacy selection needs a write, so a plain read never touches the file.
        return if (decoded is MapAreaReadResult.Legacy) {
            migrate(key, territoryName)
        } else {
            decoded
        }
    }

    override suspend fun saveBounds(
        territoryId: UUID,
        bounds: List<MapGeoBounds>,
        territoryName: String?,
    ): MapAreaSaveResult {
        val key = areaKey(territoryId)
        var result: MapAreaSaveResult = MapAreaSaveResult.Refused(CORRUPT_AREA_MESSAGE)
        dataStore.edit { preferences ->
            result = when (val current = MapAreaCodec.decode(preferences[key])) {
                is MapAreaReadResult.Corrupt -> MapAreaSaveResult.Refused(CORRUPT_AREA_MESSAGE)
                is MapAreaReadResult.Present -> saveExistingArea(preferences, key, current.area, bounds)
                is MapAreaReadResult.Legacy -> when {
                    bounds.isEmpty() -> {
                        // PHASE B: unchanged legacy behaviour for a selection that is not yet an Ареал.
                        preferences[key] = MapAreaCodec.encodeLegacy(bounds)
                        MapAreaSaveResult.SavedLegacySelection
                    }

                    else -> {
                        val area = migratedMapArea(bounds, territoryName, UUID.randomUUID())
                        preferences[key] = MapAreaCodec.encode(area)
                        MapAreaSaveResult.Saved(area)
                    }
                }

                MapAreaReadResult.Absent -> {
                    // PHASE B: first creation gets a name dialog; until then this stays as before.
                    preferences[key] = MapAreaCodec.encodeLegacy(bounds)
                    MapAreaSaveResult.SavedLegacySelection
                }
            }
        }
        return result
    }

    override suspend fun clear(territoryId: UUID) {
        dataStore.edit { preferences -> preferences.remove(areaKey(territoryId)) }
    }

    override suspend fun snapshot(territoryId: UUID): String? = readValue(areaKey(territoryId))

    override suspend fun restore(territoryId: UUID, value: String?) {
        dataStore.edit { preferences ->
            val key = areaKey(territoryId)
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    private fun saveExistingArea(
        preferences: MutablePreferences,
        key: Preferences.Key<String>,
        area: MapArea,
        bounds: List<MapGeoBounds>,
    ): MapAreaSaveResult {
        if (bounds.isEmpty()) {
            // An existing Ареал always keeps at least one участок, so this edit changes nothing.
            return MapAreaSaveResult.Refused(EMPTY_AREA_MESSAGE)
        }
        val updated = area.copy(bounds = bounds)
        preferences[key] = MapAreaCodec.encode(updated)
        return MapAreaSaveResult.Saved(updated)
    }

    private suspend fun migrate(key: Preferences.Key<String>, territoryName: String?): MapAreaReadResult {
        var result: MapAreaReadResult = MapAreaReadResult.Absent
        dataStore.edit { preferences ->
            when (val current = MapAreaCodec.decode(preferences[key])) {
                is MapAreaReadResult.Legacy -> {
                    val area = migratedMapArea(current.bounds, territoryName, UUID.randomUUID())
                    preferences[key] = MapAreaCodec.encode(area)
                    result = MapAreaReadResult.Present(area)
                }

                // Another writer already migrated, or the value changed under us.
                is MapAreaReadResult.Present -> result = current
                is MapAreaReadResult.Corrupt -> result = current
                MapAreaReadResult.Absent -> result = MapAreaReadResult.Absent
            }
        }
        return result
    }

    private suspend fun readValue(key: Preferences.Key<String>): String? = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()[key]

    private fun areaKey(territoryId: UUID) = stringPreferencesKey("map_coverage_$territoryId")
}
