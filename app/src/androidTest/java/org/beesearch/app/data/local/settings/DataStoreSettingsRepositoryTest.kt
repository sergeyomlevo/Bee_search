package org.beesearch.app.data.local.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun cleanInstallHasNoCurrentSelectionAndValuesRoundTrip() = runBlocking {
        assertNull(repository.getSettings().currentObserverId)
        assertFalse(repository.getSettings().initialSetupOfferHandled)

        val territoryId = UUID.randomUUID()
        val observerId = UUID.randomUUID()
        repository.setCurrentTerritoryId(territoryId)
        repository.setCurrentObserverId(observerId)

        val settings = repository.getSettings()
        assertEquals(observerId, settings.currentObserverId)
        assertEquals(territoryId, settings.currentTerritoryId)
        repository.setInitialSetupOfferHandled(true)
        assertTrue(repository.getSettings().initialSetupOfferHandled)
    }
}
