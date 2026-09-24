package org.beesearch.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.repository.SettingsRepository
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

    override suspend fun setCurrentTerritoryId(territoryId: UUID?) {
        dataStore.edit { preferences ->
            if (territoryId == null) {
                preferences.remove(CURRENT_TERRITORY_ID)
            } else {
                preferences[CURRENT_TERRITORY_ID] = territoryId.toString()
            }
        }
    }

    override suspend fun setCurrentObserverId(observerId: UUID?) {
        dataStore.edit { preferences ->
            if (observerId == null) {
                preferences.remove(CURRENT_OBSERVER_ID)
            } else {
                preferences[CURRENT_OBSERVER_ID] = observerId.toString()
            }
            // Legacy v4 state is deliberately ignored after the approved v5 reset.
            preferences.remove(LEGACY_OBSERVER_CODE)
        }
    }

    override suspend fun setInitialSetupOfferHandled(handled: Boolean) {
        dataStore.edit { it[INITIAL_SETUP_OFFER_HANDLED] = handled }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        currentTerritoryId = preferences[CURRENT_TERRITORY_ID]?.let(::parseUuidOrNull),
        currentObserverId = preferences[CURRENT_OBSERVER_ID]?.let(::parseUuidOrNull),
        initialSetupOfferHandled = preferences[INITIAL_SETUP_OFFER_HANDLED] ?: false,
    )

    private fun parseUuidOrNull(value: String): UUID? = runCatching {
        UUID.fromString(value)
    }.getOrNull()

    private companion object {
        val CURRENT_TERRITORY_ID = stringPreferencesKey("current_territory_id")
        val CURRENT_OBSERVER_ID = stringPreferencesKey("current_observer_id")
        val INITIAL_SETUP_OFFER_HANDLED = booleanPreferencesKey("initial_setup_offer_handled")
        val LEGACY_OBSERVER_CODE = stringPreferencesKey("observer_code")
    }
}
