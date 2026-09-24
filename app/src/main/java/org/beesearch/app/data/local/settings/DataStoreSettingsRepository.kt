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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.repository.SettingsRepository
import java.io.IOException
import java.util.UUID

private const val SETTINGS_NAME = "bee_search_settings"

/**
 * Settings that belong to the device and are worth restoring.
 *
 * The Initial Setup offer flag is deliberately absent here: it is install-local state living in
 * [installStateDataStore], which no backup mode carries.
 */
internal val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_NAME)

internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val installStateDataStore: DataStore<Preferences>,
) : SettingsRepository {
    /**
     * Portable device settings combined with the install-local offer state.
     *
     * Callers keep seeing one [AppSettings]; which file a value is stored in stays an implementation
     * detail of this repository.
     */
    override val settings: Flow<AppSettings> = combine(
        dataStore.readablePreferences(),
        installStateDataStore.readablePreferences(),
    ) { portable, installState ->
        portable.toPortableSettings().copy(
            initialSetupOfferHandled = installState[INITIAL_SETUP_OFFER_HANDLED] ?: false,
        )
    }

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

    /**
     * Writes the offer state only into the install-local file.
     *
     * The legacy key of the same name in the settings file is never written and never read back: a
     * restored settings file cannot be told apart from a locally written one, so importing that value
     * could restore a state that belongs to a different installation.
     */
    override suspend fun setInitialSetupOfferHandled(handled: Boolean) {
        installStateDataStore.edit { it[INITIAL_SETUP_OFFER_HANDLED] = handled }
    }

    private fun DataStore<Preferences>.readablePreferences(): Flow<Preferences> = data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    private fun Preferences.toPortableSettings(): AppSettings = AppSettings(
        currentTerritoryId = this[CURRENT_TERRITORY_ID]?.let(::parseUuidOrNull),
        currentObserverId = this[CURRENT_OBSERVER_ID]?.let(::parseUuidOrNull),
    )

    private fun parseUuidOrNull(value: String): UUID? = runCatching {
        UUID.fromString(value)
    }.getOrNull()

    private companion object {
        val CURRENT_TERRITORY_ID = stringPreferencesKey("current_territory_id")
        val CURRENT_OBSERVER_ID = stringPreferencesKey("current_observer_id")

        /** Read and written in the install-local file only; the same-named legacy key is inert. */
        val INITIAL_SETUP_OFFER_HANDLED = booleanPreferencesKey("initial_setup_offer_handled")
        val LEGACY_OBSERVER_CODE = stringPreferencesKey("observer_code")
    }
}
