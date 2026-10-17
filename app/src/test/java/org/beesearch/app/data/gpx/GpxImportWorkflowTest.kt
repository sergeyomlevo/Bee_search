package org.beesearch.app.data.gpx

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.settings.SettingsSelectionGuard
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxImportWorkflowTest {
    private val territory = UUID(0, 11)
    private val observer = UUID(0, 12)
    private val requestId = UUID(0, 13)
    private val instant = Instant.parse("2026-10-17T00:00:00Z")

    @Test
    fun captureSnapshotsContextAndUsesStableRequestMetadata() = runBlocking {
        val settings = FakeSettings(AppSettings(territory, observer))
        val workflow = workflow(settings)

        val captured = workflow.capture("captured.gpx")
        settings.setCurrentTerritoryId(UUID(0, 99))
        settings.setCurrentTerritoryId(territory)

        assertEquals(requestId, captured.request.requestUuid)
        assertEquals(territory, captured.request.territoryId)
        assertEquals(observer, captured.request.observerId)
        assertEquals("captured.gpx", captured.request.displayName)
        assertEquals(instant.toEpochMilli(), captured.request.importedAtEpochMs)
    }

    @Test
    fun captureRequiresCurrentTerritory(): Unit = runBlocking {
        val workflow = workflow(FakeSettings(AppSettings(null, observer)))

        assertThrows(TerritoryRequiredException::class.java) { runBlocking { workflow.capture() } }
    }

    @Test
    fun userImportIsAuthorizedByApprovedUi() {
        assertTrue(workflow(FakeSettings(AppSettings(territory, observer))).userImportAvailable)
    }

    private fun workflow(settings: FakeSettings) = GpxImportWorkflow(
        createRuntime = { error("capture tests must not initialize Room") },
        settings = settings,
        selection = settings.selection,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        newRequestId = { requestId },
    )

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        val selection = SettingsSelectionGuard()
        val value = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = value
        override suspend fun getSettings(): AppSettings = value.value
        override suspend fun setCurrentTerritoryId(territoryId: UUID?) {
            selection.withSelection {
                if (value.value.currentTerritoryId != territoryId) selection.invalidate()
                value.value = value.value.copy(currentTerritoryId = territoryId)
            }
        }
        override suspend fun setCurrentObserverId(observerId: UUID?) {
            selection.withSelection {
                if (value.value.currentObserverId != observerId) selection.invalidate()
                value.value = value.value.copy(currentObserverId = observerId)
            }
        }
        override suspend fun setInitialSetupOfferHandled(handled: Boolean) {
            value.value = value.value.copy(initialSetupOfferHandled = handled)
        }
    }
}
