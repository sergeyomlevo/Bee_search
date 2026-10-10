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
 * The production flow may only expose object kinds that have a user-reachable lifecycle and a real
 * map surface, and their appearance must come from the approved marker catalogue instead of a second
 * local definition.
 */
class SavedObjectMarkerPresentationTest {
    @Test
    fun onlyKindsWithAUserReachableLifecycleArePresentable() {
        // Trap has no domain type at all; Apiary has no creation, card or list lifecycle, so neither
        // may appear as a displayable research type.
        assertEquals(
            listOf(
                MapObjectType.OBSERVATION_POINT,
                MapObjectType.HOLLOW,
                MapObjectType.LOG_HIVE,
            ),
            MapObjectType.entries.toList(),
        )
        assertEquals(
            listOf(
                MapDataType.OBSERVATION_POINT,
                MapDataType.HOLLOW,
                MapDataType.LOG_HIVE,
            ),
            MapDataType.entries.toList(),
        )
    }

    @Test
    fun presentableKindsMapToTheApprovedMarkerTypeAndReservedKindsStayOut() {
        val presentable = MapObjectType.entries.map { it.researchMarkerType() }.toSet()
        assertEquals(
            setOf(
                ResearchMarkerType.OBSERVATION_POINT,
                ResearchMarkerType.HOLLOW,
                ResearchMarkerType.LOG_HIVE,
            ),
            presentable,
        )
        assertEquals(ResearchMarkerShape.BEE, MapObjectType.OBSERVATION_POINT.researchMarkerType().shape)
        assertEquals(ResearchMarkerShape.TREE, MapObjectType.HOLLOW.researchMarkerType().shape)
        assertEquals(ResearchMarkerShape.LOG, MapObjectType.LOG_HIVE.researchMarkerType().shape)
        // Every displayable type resolves to its own marker through the same catalogue: the display
        // type, the object kind and the approved marker type describe one object, not three.
        MapDataType.entries.forEach { type ->
            assertEquals(type.name, type.objectType().name)
            assertEquals(ResearchMarkerType.valueOf(type.name), type.objectType().researchMarkerType())
        }
        assertFalse(presentable.contains(ResearchMarkerType.TRAP))
        assertFalse(presentable.contains(ResearchMarkerType.APIARY))
    }

    @Test
    fun realCountsReplacePresenceResultAndPreserveIdentity() {
        val point = point(BeePresenceResult.BEES_FOUND, 16).copy(beeCount = 4, totalFlightCycleCount = 12)
        val marker = observationPointMarkers(listOf(point)).single()
        assertEquals(point.id, marker.id)
        assertEquals("Точка 16, 4 пчелы, 12 циклов", marker.label)
        assertEquals("Точка 16, пчёлы не найдены", observationPointMarkers(listOf(point.copy(beeCount = 0, totalFlightCycleCount = 0))).single().label)
    }

    @Test
    fun russianQuantityUsesAllFormsIncludingTeensAndCompoundNumbers() {
        val expected = mapOf(0 to "пчёл", 1 to "пчела", 2 to "пчелы", 5 to "пчёл", 11 to "пчёл", 12 to "пчёл", 14 to "пчёл", 21 to "пчела", 22 to "пчелы", 25 to "пчёл", 111 to "пчёл")
        expected.forEach { (count, word) -> assertEquals("$count $word", russianQuantity(count, "пчела", "пчелы", "пчёл")) }
        assertEquals("1 цикл", russianQuantity(1, "цикл", "цикла", "циклов"))
        assertEquals("2 цикла", russianQuantity(2, "цикл", "цикла", "циклов"))
        assertEquals("5 циклов", russianQuantity(5, "цикл", "цикла", "циклов"))
    }

    private fun point(result: BeePresenceResult?, number: Int) = ObservationPointSummary(
        observationDate = LocalDate.of(2026, 9, 17),
        id = UUID.randomUUID(), territoryId = UUID.randomUUID(), observationYear = 2026,
        pointNumber = number, code = null, beePresenceResult = result,
        latitude = 56.0, longitude = 43.0, gpsAccuracyM = 4.0,
        createdAt = Instant.parse("2026-09-17T08:00:00Z"), completedAt = null,
        beeCount = 0, completedFlightCycleCount = 0, totalFlightCycleCount = 0,
    )
}
