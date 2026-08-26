package org.beesearch.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.validation.ObserverCode
import java.io.IOException
import java.util.UUID

private const val SETTINGS_NAME = "bee_search_settings"

internal val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_NAME)

internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::toSettings)

    override suspend fun getSettings(): AppSettings = settings.first()

    override suspend fun saveObserverCode(value: String): String {
        val normalized = ObserverCode.normalize(value)
        dataStore.edit { preferences ->
            preferences[OBSERVER_CODE] = normalized
        }
        return normalized
    }

    override suspend fun setCurrentTerritoryId(territoryId: UUID?) {
        dataStore.edit { preferences ->
            if (territoryId == null) {
                preferences.remove(CURRENT_TERRITORY_ID)
            } else {
                preferences[CURRENT_TERRITORY_ID] = territoryId.toString()
            }
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        currentTerritoryId = preferences[CURRENT_TERRITORY_ID]?.let(::parseUuidOrNull),
        observerCode = preferences[OBSERVER_CODE],
    )

    private fun parseUuidOrNull(value: String): UUID? = runCatching {
        UUID.fromString(value)
    }.getOrNull()

    private companion object {
        val CURRENT_TERRITORY_ID = stringPreferencesKey("current_territory_id")
        val OBSERVER_CODE = stringPreferencesKey("observer_code")
    }
}
