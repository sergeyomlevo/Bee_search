package org.beesearch.app.ui.map

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unified data map: what each visible type contributes, and which filter each type asks for.
 *
 * These tests pin the two approved invariants of «Данные на карте»: visibility only decides whether a
 * type is shown, and a period is always passed to that type's own temporal query.
 */
class MapResearchObjectsTest {
    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
    private val year2025 = ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))

    @Test
    fun eachTypeAsksForItsOwnIntervalAndStaysUnboundedOtherwise() {
        val display = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.LOG_HIVE, year2025)

        val filters = display.objectFilters()
        assertEquals(may2026, filters.observationPoint.dateInterval)
        assertEquals(year2025, filters.logHive.dateInterval)
        // Hollow keeps its own value — here «Всё время», which is not another type's period.
        assertNull(filters.hollow.dateInterval)

        val other = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, may2026).objectFilters()
        assertEquals(may2026, other.hollow.dateInterval)
        assertNull(other.observationPoint.dateInterval)
        assertNull(other.logHive.dateInterval)
    }

    @Test
    fun hiddenTypeContributesNoMarkerWhileVisibleTypesKeepTheirs() {
        val point = point(15)
        val hollow = hollow(4)
        val logHive = logHive(2)

        val all = researchObjectMarkers(DEFAULT_MAP_DATA_DISPLAY, listOf(point), listOf(hollow), listOf(logHive))
        assertEquals(
            listOf(MapObjectType.OBSERVATION_POINT, MapObjectType.HOLLOW, MapObjectType.LOG_HIVE),
            all.map(MapObjectMarker::type),
        )

        // A hidden type with its own filter keeps that filter while its markers disappear.
        val hiddenHollow = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.HOLLOW, may2026)
            .withVisibility(MapDataType.HOLLOW, false)
        val withoutHollow = researchObjectMarkers(hiddenHollow, listOf(point), listOf(hollow), listOf(logHive))
        assertEquals(
            listOf(MapObjectType.OBSERVATION_POINT, MapObjectType.LOG_HIVE),
            withoutHollow.map(MapObjectMarker::type),
        )
        assertEquals(may2026, hiddenHollow.period(MapDataType.HOLLOW))
        assertEquals("май 2026", hiddenHollow.summary(MapDataType.HOLLOW))
        // Showing it again returns the same filter and the same markers.
        val shownAgain = hiddenHollow.withVisibility(MapDataType.HOLLOW, true)
        assertEquals(
            listOf(MapObjectType.OBSERVATION_POINT, MapObjectType.HOLLOW, MapObjectType.LOG_HIVE),
            researchObjectMarkers(shownAgain, listOf(point), listOf(hollow), listOf(logHive)).map(MapObjectMarker::type),
        )
        assertEquals(may2026, shownAgain.period(MapDataType.HOLLOW))
        assertEquals("Точка 15, 2 пчелы, 1 цикл", withoutHollow.first().label)

        val allHidden = MapDataType.entries.fold(DEFAULT_MAP_DATA_DISPLAY) { state, type ->
            state.withVisibility(type, false)
        }
        assertTrue(
            researchObjectMarkers(allHidden, listOf(point), listOf(hollow), listOf(logHive)).isEmpty(),
        )
    }

    @Test
    fun eachTypeMapsToItsOwnApprovedD102MarkerAndTestTag() {
        val point = observationPointMarkers(listOf(point(7))).single()
        val tree = hollowMarkers(listOf(hollow(3))).single()
        val log = logHiveMarkers(listOf(logHive(9))).single()

        assertEquals(ResearchMarkerType.OBSERVATION_POINT, point.type.researchMarkerType())
        assertEquals(ResearchMarkerType.HOLLOW, tree.type.researchMarkerType())
        assertEquals(ResearchMarkerType.LOG_HIVE, log.type.researchMarkerType())
        assertEquals(ResearchMarkerShape.BEE, point.type.researchMarkerType().shape)
        assertEquals(ResearchMarkerShape.TREE, tree.type.researchMarkerType().shape)
        assertEquals(ResearchMarkerShape.LOG, log.type.researchMarkerType().shape)
        // The catalogue stays the single definition of family colours.
        assertEquals(ResearchMarkerType.OBSERVATION_POINT.familyColor, point.type.researchMarkerType().familyColor)
        assertEquals(ResearchMarkerType.HOLLOW.familyColor, tree.type.researchMarkerType().familyColor)
        assertEquals(ResearchMarkerType.LOG_HIVE.familyColor, log.type.researchMarkerType().familyColor)

        assertEquals("point-marker-${point.id}", markerTestTag(point))
        assertEquals("hollow-marker-${tree.id}", markerTestTag(tree))
        assertEquals("log-hive-marker-${log.id}", markerTestTag(log))
    }

    @Test
    fun physicalObjectLabelUsesFixationMomentForHollowAndLogHive() {
        val fixationAt = Instant.parse("2026-08-05T08:34:56Z")
        val expected = fixationAt.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val hollow = hollowMarkers(
            listOf(hollow(4, fixationDate = LocalDate.of(2026, 8, 5), fixationAt = fixationAt)),
        ).single()
        val logHive = logHiveMarkers(
            listOf(logHive(6, fixationDate = LocalDate.of(2026, 8, 5), fixationAt = fixationAt)),
        ).single()
        assertEquals("Дупло 4, $expected", hollow.label)
        assertEquals("Колода 6, $expected", logHive.label)
    }

    @Test
    fun physicalObjectLabelKeepsDatePrecisionAndNeverInventsMoment() {
        val dateOnly = hollowMarkers(listOf(hollow(4, fixationDate = LocalDate.of(2026, 8, 5)))).single()
        assertEquals("Дупло 4, 05.08.2026, время фиксации неизвестно", dateOnly.label)

        // A legacy record has no canonical fixation date; created_at must not stand in for it.
        val unknown = hollowMarkers(listOf(hollow(5, fixationDate = null))).single()
        assertEquals("Дупло 5, момент фиксации неизвестен", unknown.label)

        val named = logHiveMarkers(
            listOf(logHive(2, LocalDate.of(2025, 4, 1), name = "Сосна у моста")),
        ).single()
        assertEquals("Колода 2, Сосна у моста, 01.04.2025, время фиксации неизвестно", named.label)
    }

    @Test
    fun coordinatesAndIdentityTravelUnchangedForEveryType() {
        val pointMarker = researchObjectMarkers(DEFAULT_MAP_DATA_DISPLAY, listOf(point(1)), emptyList(), emptyList()).single()
        assertEquals(56.0, pointMarker.latitude, 1e-9)
        assertEquals(43.0, pointMarker.longitude, 1e-9)

        val hollowMarker = researchObjectMarkers(DEFAULT_MAP_DATA_DISPLAY, emptyList(), listOf(hollow(1)), emptyList()).single()
        assertEquals(56.5, hollowMarker.latitude, 1e-9)
        assertEquals(43.5, hollowMarker.longitude, 1e-9)
    }

    private fun point(number: Int) = ObservationPointSummary(
        observationDate = LocalDate.of(2026, 5, 17),
        id = UUID.randomUUID(), territoryId = UUID.randomUUID(), observationYear = 2026,
        pointNumber = number, code = null, beePresenceResult = BeePresenceResult.BEES_FOUND,
        latitude = 56.0, longitude = 43.0, gpsAccuracyM = 4.0,
        createdAt = Instant.parse("2026-05-17T08:00:00Z"), completedAt = null,
        beeCount = 2, completedFlightCycleCount = 1, totalFlightCycleCount = 1,
    )

    private fun hollow(
        sequenceNumber: Int,
        fixationDate: LocalDate? = LocalDate.of(2026, 8, 5),
        fixationAt: Instant? = null,
    ) = Hollow(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
        sequenceNumber = sequenceNumber,
        latitude = 56.5,
        longitude = 43.5,
        createdAt = Instant.parse("2026-08-05T08:00:00Z"),
        creatorObserverId = null,
        properties = null,
        fixationDate = fixationDate,
        fixationAt = fixationAt,
    )

    private fun logHive(
        sequenceNumber: Int,
        fixationDate: LocalDate? = LocalDate.of(2025, 4, 1),
        name: String? = null,
        fixationAt: Instant? = null,
    ) = LogHive(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
        sequenceNumber = sequenceNumber,
        latitude = 56.6,
        longitude = 43.6,
        createdAt = Instant.parse("2025-04-01T08:00:00Z"),
        creatorObserverId = null,
        properties = null,
        name = name,
        fixationDate = fixationDate,
        fixationAt = fixationAt,
    )
}
