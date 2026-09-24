package org.beesearch.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * The offer flag lives in the install-local file; the persisted key names are asserted here by name
 * because they are a storage contract, not an implementation detail of one class.
 */
class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var settingsFile: File
    private lateinit var installStateFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var installStateDataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsFile = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        installStateFile = File(context.cacheDir, "install-state-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile })
        installStateDataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { installStateFile })
        repository = DataStoreSettingsRepository(dataStore, installStateDataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        settingsFile.delete()
        installStateFile.delete()
    }

    @Test
    fun cleanInstallationStartsWithNoSelectionAndNoHandledOffer() = runBlocking {
        assertNull(repository.getSettings().currentObserverId)
        assertNull(repository.getSettings().currentTerritoryId)
        assertFalse(repository.getSettings().initialSetupOfferHandled)
    }

    @Test
    fun selectionsRoundTripThroughThePublicApi() = runBlocking {
        val territoryId = UUID.randomUUID()
        val observerId = UUID.randomUUID()
        repository.setCurrentTerritoryId(territoryId)
        repository.setCurrentObserverId(observerId)

        val settings = repository.getSettings()
        assertEquals(observerId, settings.currentObserverId)
        assertEquals(territoryId, settings.currentTerritoryId)

        repository.setCurrentTerritoryId(null)
        repository.setCurrentObserverId(null)
        assertNull(repository.getSettings().currentTerritoryId)
        assertNull(repository.getSettings().currentObserverId)
    }

    @Test
    fun theOfferStateIsWrittenOnlyIntoTheInstallLocalFile() = runBlocking {
        repository.setInitialSetupOfferHandled(true)

        assertTrue(installStateDataStore.data.first()[OFFER_KEY] == true)
        assertFalse(dataStore.data.first().asMap().containsKey(OFFER_KEY))
        assertTrue(repository.getSettings().initialSetupOfferHandled)
    }

    @Test
    fun aRestoredLegacyOfferValueInTheSettingsFileIsIgnored() = runBlocking {
        // Android restore can bring the old key back; the install-local file of this installation is empty.
        dataStore.edit { it[OFFER_KEY] = true }

        assertFalse(
            "the legacy key of the backed-up settings file is not an authoritative or fallback value",
            repository.getSettings().initialSetupOfferHandled,
        )
        assertFalse(installStateDataStore.data.first()[OFFER_KEY] == true)
    }

    @Test
    fun theOfferStateSurvivesTheNextLaunchOfTheSameInstallation() = runBlocking {
        repository.setInitialSetupOfferHandled(true)
        assertTrue(installStateFile.length() > 0)
        // Ending the process scope models an ordinary restart; the install-local file stays on the device.
        scope.cancel()

        val relaunchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val relaunched = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = relaunchScope, produceFile = { settingsFile }),
                PreferenceDataStoreFactory.create(scope = relaunchScope, produceFile = { installStateFile }),
            )
            assertTrue(relaunched.getSettings().initialSetupOfferHandled)
        } finally {
            relaunchScope.cancel()
        }
    }

    @Test
    fun portableSettingsAndMapStateStayInTheSettingsFile() = runBlocking {
        val territoryId = UUID.randomUUID()
        val observerId = UUID.randomUUID()
        repository.setCurrentTerritoryId(territoryId)
        repository.setCurrentObserverId(observerId)
        repository.setInitialSetupOfferHandled(true)

        // The map stores own their keys in the settings file; this asserts they are not moved by the split.
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("map_coverage_$territoryId")] = "coverage-v2"
            preferences[stringPreferencesKey("map_package_active_$territoryId")] = "package-pointer"
        }

        val portable = dataStore.data.first()
        assertEquals(territoryId.toString(), portable[TERRITORY_KEY])
        assertEquals(observerId.toString(), portable[OBSERVER_KEY])
        assertEquals("coverage-v2", portable[stringPreferencesKey("map_coverage_$territoryId")])
        assertEquals("package-pointer", portable[stringPreferencesKey("map_package_active_$territoryId")])

        assertEquals(
            "the install-local file holds the offer state and nothing else",
            setOf("initial_setup_offer_handled"),
            installStateDataStore.data.first().asMap().keys.map { it.name }.toSet(),
        )
    }

    private companion object {
        val OFFER_KEY = booleanPreferencesKey("initial_setup_offer_handled")
        val TERRITORY_KEY = stringPreferencesKey("current_territory_id")
        val OBSERVER_KEY = stringPreferencesKey("current_observer_id")
    }
}
