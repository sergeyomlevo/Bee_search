package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class StartupRouterTest {
    private val territory = Territory(UUID.randomUUID(), "A01", "Лес", "Область", "Район", Instant.EPOCH, Instant.EPOCH)
    private val observer = Observer(UUID.randomUUID(), "SP01", "Иванов", "Сергей", null, null, Instant.EPOCH, Instant.EPOCH)

    @Test fun `opens map when observer missing`() {
        assertEquals(StartupDestination.ReadyForMap, StartupRouter.decide(null, territory.id, listOf(territory), null, listOf(observer)))
    }

    @Test fun `opens map when territory missing`() {
        assertEquals(StartupDestination.ReadyForMap, StartupRouter.decide(null, null, listOf(territory), observer.id, listOf(observer)))
    }

    @Test fun `opens map when saved ids no longer exist`() {
        assertEquals(
            StartupDestination.ReadyForMap,
            StartupRouter.decide(null, UUID.randomUUID(), listOf(territory), UUID.randomUUID(), listOf(observer)),
        )
    }

    @Test fun `active observation recovery has priority over invalid selections`() {
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
            StartupRouter.decide(activePoint, null, emptyList(), null, emptyList()),
        )
    }

    @Test fun `opens map with valid selections`() {
        assertEquals(StartupDestination.ReadyForMap, StartupRouter.decide(null, territory.id, listOf(territory), observer.id, listOf(observer)))
    }

    @Test fun `clean first run opens the checklist instead of the territory blocker`() {
        val destination = StartupRouter.decide(
            activePoint = null,
            currentTerritoryId = null,
            territories = emptyList(),
            currentObserverId = null,
            observers = emptyList(),
            setupComplete = false,
            offerHandled = false,
        )
        assertEquals(StartupDestination.InitialSetup, destination)
        assertNotEquals(StartupDestination.ReadyForMap, destination)
    }

    @Test fun `handled offer does not reopen checklist automatically`() {
        assertEquals(StartupDestination.ReadyForMap,
            StartupRouter.decide(null, null, emptyList(), null, emptyList(),
                setupComplete = false, offerHandled = true))
    }

    @Test fun `completed setup needs no initial offer`() {
        assertEquals(StartupDestination.ReadyForMap,
            StartupRouter.decide(null, territory.id, listOf(territory), observer.id, listOf(observer),
                setupComplete = true, offerHandled = false))
    }
}
