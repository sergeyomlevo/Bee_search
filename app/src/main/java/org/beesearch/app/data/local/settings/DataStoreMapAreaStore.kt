package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.beesearch.app.ui.map.BLANK_AREA_NAME_MESSAGE
import org.beesearch.app.ui.map.CORRUPT_AREA_MESSAGE
import org.beesearch.app.ui.map.EMPTY_AREA_MESSAGE
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.UNMIGRATED_AREA_MESSAGE
import org.beesearch.app.ui.map.migratedMapArea
import org.beesearch.app.ui.map.normalizedAreaName

/**
 * Stores the device-local Ареал of each Territory in the existing `map_coverage_<territoryId>`
 * entry.
 *
 * A readable pre-Ареал selection is migrated in place by the first read: the edit that rewrites the
 * value and the edit that returns the new id happen in one DataStore transaction, so the generated
 * id becomes stable immediately and a failed migration cannot leave a partial value behind. A
 * damaged value is never migrated over, never rewritten and never reported as "no Ареал".
 *
 * Every user-facing change writes a complete `v2` Ареал; the legacy `v1` encoding is only ever read.
 */
internal class DataStoreMapAreaStore(
    private val dataStore: DataStore<Preferences>,
) : MapAreaStore {
    override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult {
        val key = areaKey(territoryId)
        val decoded = MapAreaCodec.decode(readValue(key))
        // Only a readable legacy selection needs a write, so a plain read never touches the file.
        return if (decoded is MapAreaReadResult.Legacy) migrate(key, territoryName) else decoded
    }

    override suspend fun create(
        territoryId: UUID,
        name: String,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult {
        val normalized = normalizedAreaName(name)
            ?: return MapAreaChangeResult.Refused(BLANK_AREA_NAME_MESSAGE)
        if (bounds.isEmpty()) return MapAreaChangeResult.Refused(EMPTY_AREA_MESSAGE)
        return editArea(territoryId) { current ->
            when (current) {
                MapAreaReadResult.Absent ->
                    MapAreaChangeResult.Saved(MapArea(id = UUID.randomUUID(), name = normalized, bounds = bounds))

                is MapAreaReadResult.Present -> MapAreaChangeResult.Refused(ALREADY_EXISTS_MESSAGE)
                is MapAreaReadResult.Corrupt -> MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
                is MapAreaReadResult.Legacy -> MapAreaChangeResult.Refused(UNMIGRATED_AREA_MESSAGE)
            }
        }
    }

    override suspend fun updateBounds(
        territoryId: UUID,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult {
        if (bounds.isEmpty()) return MapAreaChangeResult.Refused(EMPTY_AREA_MESSAGE)
        return editArea(territoryId) { current ->
            when (current) {
                is MapAreaReadResult.Present -> MapAreaChangeResult.Saved(current.area.copy(bounds = bounds))
                MapAreaReadResult.Absent -> MapAreaChangeResult.Refused(NO_AREA_MESSAGE)
                is MapAreaReadResult.Corrupt -> MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
                is MapAreaReadResult.Legacy -> MapAreaChangeResult.Refused(UNMIGRATED_AREA_MESSAGE)
            }
        }
    }

    override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult {
        val normalized = normalizedAreaName(name)
            ?: return MapAreaChangeResult.Refused(BLANK_AREA_NAME_MESSAGE)
        return editArea(territoryId) { current ->
            when (current) {
                is MapAreaReadResult.Present -> MapAreaChangeResult.Saved(current.area.copy(name = normalized))
                MapAreaReadResult.Absent -> MapAreaChangeResult.Refused(NO_AREA_MESSAGE)
                is MapAreaReadResult.Corrupt -> MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
                is MapAreaReadResult.Legacy -> MapAreaChangeResult.Refused(UNMIGRATED_AREA_MESSAGE)
            }
        }
    }

    override suspend fun delete(territoryId: UUID): MapAreaChangeResult = editArea(territoryId) { current ->
        when (current) {
            // Idempotent: there is nothing left to delete.
            MapAreaReadResult.Absent -> MapAreaChangeResult.Deleted
            is MapAreaReadResult.Present -> MapAreaChangeResult.Deleted
            is MapAreaReadResult.Corrupt -> MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
            is MapAreaReadResult.Legacy -> MapAreaChangeResult.Refused(UNMIGRATED_AREA_MESSAGE)
        }
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

    /**
     * Applies one change atomically: the decision and the write happen inside a single DataStore
     * transaction, so a refused change provably leaves the stored value untouched.
     */
    private suspend fun editArea(
        territoryId: UUID,
        decide: (MapAreaReadResult) -> MapAreaChangeResult,
    ): MapAreaChangeResult {
        val key = areaKey(territoryId)
        var result: MapAreaChangeResult = MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
        dataStore.edit { preferences ->
            result = decide(MapAreaCodec.decode(preferences[key]))
            when (val outcome = result) {
                is MapAreaChangeResult.Saved -> preferences[key] = MapAreaCodec.encode(outcome.area)
                MapAreaChangeResult.Deleted -> preferences.remove(key)
                is MapAreaChangeResult.Refused -> Unit
            }
        }
        return result
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

    private companion object {
        const val ALREADY_EXISTS_MESSAGE = "Ареал уже создан"
        const val NO_AREA_MESSAGE = "Ареал не создан"
    }
}
