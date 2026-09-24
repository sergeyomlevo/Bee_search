package org.beesearch.app

import java.io.File
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.ActiveMapPackage
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.beesearch.app.domain.usecase.StartupDestination
import org.junit.Test

class InitialSetupStateTest {
    private val territory = Territory(UUID.randomUUID(), "T", "Территория", "Регион", "Район",
        Instant.EPOCH, Instant.EPOCH)
    private val observer = Observer(UUID.randomUUID(), "O", "Иванов", "Иван", null, null,
        Instant.EPOCH, Instant.EPOCH)
    private val area = MapArea(UUID.randomUUID(), "Ареал", listOf(
        MapGeoBounds(57.0, 39.0, 56.0, 38.0)))
    private val readyMap = MapPackageAvailability.Ready(ActiveMapPackage(
        MapPackageManifest(1, "test", "test", "test", "v1", "v1", emptyList(),
            0, 1, "test.pmtiles", 1, "0".repeat(64)), File("test.pmtiles")))

    private fun state(
        selectedObserver: Observer? = observer,
        selectedTerritory: Territory? = territory,
        selectedArea: MapAreaReadResult = MapAreaReadResult.Present(area),
        map: MapPackageAvailability? = readyMap,
    ) = InitialSetupState.Ready(null, selectedObserver, selectedTerritory, selectedArea, map, false)

    @Test fun `all four actual values are needed for completion`() {
        assertTrue(state().complete)
        assertFalse(state(selectedObserver = null).complete)
        assertFalse(state(selectedTerritory = null).complete)
        assertFalse(state(selectedArea = MapAreaReadResult.Absent, map = null).complete)
        assertFalse(state(map = MapPackageAvailability.Missing).complete)
        assertFalse(state(map = MapPackageAvailability.Unavailable("invalid")).complete)
        assertFalse(state(selectedArea = MapAreaReadResult.Corrupt("invalid"), map = null).complete)
    }

    @Test fun `the device local offer flag is not readiness`() {
        assertFalse(state(map = MapPackageAvailability.Missing).copy(offerHandled = true).complete)
        assertTrue(state().copy(offerHandled = false).complete)
    }

    @Test fun `startup waits for facts and recovery takes priority`() {
        assertEquals(StartupDestination.Loading, startupDestinationFor(InitialSetupState.Loading()))
        assertEquals(StartupDestination.InitialSetup,
            startupDestinationFor(state(selectedObserver = null)))
        assertEquals(StartupDestination.ReadyForMap,
            startupDestinationFor(state(selectedObserver = null).copy(offerHandled = true)))
    }

    @Test fun `late previous territory result is hidden during new read`() {
        val oldTerritoryReady = state()
        assertTrue(visibleInitialSetupFor(oldTerritoryReady, 1) is InitialSetupState.Loading)
        assertTrue(visibleInitialSetupFor(oldTerritoryReady.copy(generation = 1), 1) is InitialSetupState.Ready)
    }

    private fun cleanFirstRun() = state(
        selectedObserver = null,
        selectedTerritory = null,
        selectedArea = MapAreaReadResult.Absent,
        map = null,
    ).copy(offerHandled = false)

    @Test fun `clean first run opens the checklist rather than the map blocker`() {
        assertEquals(StartupDestination.InitialSetup, startupDestinationFor(cleanFirstRun()))
    }

    @Test fun `leaving through continue does not force the checklist again`() {
        assertEquals(
            StartupDestination.ReadyForMap,
            startupDestinationFor(cleanFirstRun().copy(offerHandled = true)),
        )
    }

    @Test fun `active observation recovery keeps priority over the checklist`() {
        val activePoint = ObservationPoint(
            id = UUID.randomUUID(),
            territoryId = territory.id,
            observerId = observer.id,
            observationYear = 2026,
            pointNumber = 1,
            beePresenceResult = null,
            code = null,
            latitude = 56.1,
            longitude = 42.7,
            gpsLatitude = null,
            gpsLongitude = null,
            gpsAccuracyM = null,
            createdAt = Instant.EPOCH,
            completedAt = null,
        )
        assertEquals(
            StartupDestination.ResumeObservation(activePoint),
            startupDestinationFor(InitialSetupState.Loading(activePoint = activePoint)),
        )
        assertEquals(
            StartupDestination.ResumeObservation(activePoint),
            startupDestinationFor(cleanFirstRun().copy(activePoint = activePoint)),
        )
    }

    @Test fun `a checklist step returns to the checklist it was opened from`() {
        assertEquals(AppRoute.InitialSetup, setupStepReturnRoute(setupStepPending = true))
        assertNull(setupStepReturnRoute(setupStepPending = false))
        assertEquals(AppRoute.InitialSetup, setupDestinationReturnRoute(setupStepPending = true))
        assertEquals(AppRoute.Objects, setupDestinationReturnRoute(setupStepPending = false))
    }
}
