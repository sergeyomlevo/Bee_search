package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class StartupRouterTest {
    private val territory = Territory(
        id = UUID.randomUUID(),
        code = "KLYAZMA-01",
        name = "Клязьма",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `active observation takes precedence over current territory`() {
        val point = ObservationPoint(
            id = UUID.randomUUID(),
            territoryId = territory.id,
            observerCode = "SV",
            code = null,
            latitude = 56.1,
            longitude = 42.7,
            gpsLatitude = null,
            gpsLongitude = null,
            gpsAccuracyM = null,
            createdAt = Instant.EPOCH,
            completedAt = null,
        )

        val destination = StartupRouter.decide(point, territory.id, listOf(territory))

        assertEquals(StartupDestination.ResumeObservation(point), destination)
    }

    @Test
    fun `valid current territory opens territory flow`() {
        val destination = StartupRouter.decide(null, territory.id, listOf(territory))

        assertEquals(StartupDestination.CurrentTerritory(territory), destination)
    }

    @Test
    fun `missing or stale current territory opens management`() {
        assertTrue(StartupRouter.decide(null, null, listOf(territory)) is StartupDestination.TerritoryManagement)
        assertTrue(
            StartupRouter.decide(null, UUID.randomUUID(), listOf(territory)) is
                StartupDestination.TerritoryManagement,
        )
    }
}
