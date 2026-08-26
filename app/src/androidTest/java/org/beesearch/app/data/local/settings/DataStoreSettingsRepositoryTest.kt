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
    fun cleanInstallHasNoObserverCodeAndValuesRoundTrip() = runBlocking {
        assertNull(repository.getSettings().observerCode)

        val territoryId = UUID.randomUUID()
        assertEquals("Сергей-01", repository.saveObserverCode("  Сергей-01 "))
        repository.setCurrentTerritoryId(territoryId)

        val settings = repository.getSettings()
        assertEquals("Сергей-01", settings.observerCode)
        assertEquals(territoryId, settings.currentTerritoryId)
    }
}
