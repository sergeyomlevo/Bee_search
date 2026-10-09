package org.beesearch.app.ui.map

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production saved-object marker presentation (D102 integration).
 *
 * The production flow may only expose object kinds that have a real map surface, and their
 * appearance must come from the approved marker catalogue instead of a second local definition.
 */
class SavedObjectMarkerPresentationTest {
    @Test
    fun onlyTheObjectKindWithAProductionMapSurfaceIsPresentable() {
        assertEquals(listOf(MapObjectType.OBSERVATION_POINT), MapObjectType.entries.toList())
    }

    @Test
    fun presentableKindsMapToTheApprovedMarkerTypeAndReservedKindsStayOut() {
        val presentable = MapObjectType.entries.map { it.researchMarkerType() }.toSet()
        assertEquals(setOf(ResearchMarkerType.OBSERVATION_POINT), presentable)
        assertEquals(ResearchMarkerShape.BEE, MapObjectType.OBSERVATION_POINT.researchMarkerType().shape)
        // Trap has no domain type at all and Apiary has no user-reachable lifecycle: reserved only.
        assertFalse(presentable.contains(ResearchMarkerType.TRAP))
        assertFalse(presentable.contains(ResearchMarkerType.APIARY))
    }

    @Test
    fun objectStateTravelsInTheAccessibilityLabel() {
        assertEquals("Точка 4, пчёлы найдены", marker(BeePresenceResult.BEES_FOUND, 4).label)
        assertEquals("Точка 5, пчёлы отсутствуют", marker(BeePresenceResult.NO_BEES_FOUND, 5).label)
        assertEquals("Точка 6, результат не зафиксирован", marker(null, 6).label)
        assertTrue(marker(BeePresenceResult.BEES_FOUND, 4).label != marker(null, 6).label)
    }

    private fun marker(result: BeePresenceResult?, number: Int) = observationPointMarkers(
        listOf(
            ObservationPointSummary(
                observationDate = LocalDate.of(2026, 9, 17),
                id = UUID.randomUUID(), territoryId = UUID.randomUUID(), observationYear = 2026,
                pointNumber = number, code = null, beePresenceResult = result,
                latitude = 56.0, longitude = 43.0, gpsAccuracyM = 4.0,
                createdAt = Instant.parse("2026-09-17T08:00:00Z"), completedAt = null,
                beeCount = 0, completedFlightCycleCount = 0,
            ),
        ),
    ).single()
}
