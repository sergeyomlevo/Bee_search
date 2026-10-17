package org.beesearch.app.data.gpx

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.local.settings.SettingsSelectionGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real DataStore selection writes share the same transient guard as final GPX completion. */
class GpxSelectionGuardDeviceTest {
    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var guard: SettingsSelectionGuard
    private lateinit var repository: DataStoreSettingsRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.filesDir, "gpx-selection-${UUID.randomUUID()}").apply { check(mkdir()) }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        guard = SettingsSelectionGuard()
        repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(root, "settings.preferences_pb") },
            PreferenceDataStoreFactory.create(scope = scope) { File(root, "install.preferences_pb") }, guard)
    }

    @After fun cleanup() { scope.cancel(); root.deleteRecursively() }

    @Test fun actualSelectionWritesInvalidateEvenWhenSelectionReturnsToOriginal(): Unit = runBlocking {
        val a = UUID(0, 1)
        val b = UUID(0, 2)
        repository.setCurrentTerritoryId(a)
        val captured = guard.revision
        repository.setCurrentTerritoryId(a)
        assertEquals(captured, guard.revision)
        repository.setInitialSetupOfferHandled(true)
        assertEquals(captured, guard.revision)
        repository.setCurrentTerritoryId(b)
        repository.setCurrentTerritoryId(a)
        assertNotEquals(captured, guard.revision)
        assertEquals(a, repository.getSettings().currentTerritoryId)
        val beforeObserver = guard.revision
        repository.setCurrentObserverId(b)
        assertNotEquals(beforeObserver, guard.revision)
        assertEquals(b, repository.getSettings().currentObserverId)
    }

    @Test fun selectionWriteWaitsForPublicationBoundaryWithoutBlockingMainThread(): Unit = runBlocking {
        withTimeout(10_000) {
            val a = UUID(0, 1)
            val b = UUID(0, 2)
            repository.setCurrentTerritoryId(a)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val publication = async(Dispatchers.IO) {
                guard.withSelection {
                    entered.complete(Unit)
                    release.await()
                    assertEquals(a, repository.getSettings().currentTerritoryId)
                }
            }
            entered.await()
            val started = CompletableDeferred<Unit>()
            val write = async(Dispatchers.Main) {
                started.complete(Unit)
                repository.setCurrentTerritoryId(b)
            }
            started.await()
            assertFalse(write.isCompleted)
            release.complete(Unit)
            publication.await()
            write.await()
            assertEquals(b, repository.getSettings().currentTerritoryId)
        }
    }
}
