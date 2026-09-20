package org.beesearch.app.ui.points

import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointsPresentationTest {
    private val devTerritory = UUID.randomUUID()
    private val otherTerritory = UUID.randomUUID()

    @Test
    fun defaultUsesLatestAvailableYearAndSpecificYearFiltersOnce() {
        val old = summary(2025, 1)
        val newest = summary(2026, 2)

        val initial = buildPointsUiState(
            allPoints = listOf(newest, old),
            selectedTerritoryId = devTerritory,
            requestedYearFilter = null,
            viewMode = PointsViewMode.MAP,
            selectedPointId = null,
        )
        val oldYear = buildPointsUiState(
            allPoints = listOf(newest, old),
            selectedTerritoryId = devTerritory,
            requestedYearFilter = PointsYearFilter.Year(2025),
            viewMode = PointsViewMode.TABLE,
            selectedPointId = old.id,
        )

        assertEquals(listOf(2026, 2025), initial.availableYears)
        assertEquals(PointsYearFilter.Year(2026), initial.yearFilter)
        assertEquals(listOf(newest.id), initial.points.map { it.id })
        assertEquals(listOf(old.id), oldYear.points.map { it.id })
        assertEquals(old.id, oldYear.selectedPoint?.id)
    }

    @Test
    fun allYearsAndViewSwitchKeepExactlyTheSamePointIds() {
        val points = listOf(summary(2026, 3), summary(2025, 2), summary(2024, 1))
        val map = buildPointsUiState(points, devTerritory, PointsYearFilter.All, PointsViewMode.MAP, null)
        val table = buildPointsUiState(points, devTerritory, PointsYearFilter.All, PointsViewMode.TABLE, null)

        assertEquals(points.map { it.id }, map.points.map { it.id })
        assertEquals(map.points.map { it.id }, table.points.map { it.id })
        assertNull(table.selectedPoint)
    }

    /**
     * The browser observes one Territory at a time, so every point in the state belongs to the
     * Territory the user is viewing.
     */
    @Test
    fun stateOnlyCarriesPointsOfTheViewedTerritory() {
        val viewed = summary(2026, 1, territoryId = otherTerritory)

        val state = buildPointsUiState(
            allPoints = listOf(viewed),
            selectedTerritoryId = otherTerritory,
            requestedYearFilter = null,
            viewMode = PointsViewMode.MAP,
            selectedPointId = null,
        )

        assertEquals(otherTerritory, state.selectedTerritoryId)
        assertEquals(listOf(viewed.id), state.points.map { it.id })
        assertEquals(setOf(otherTerritory), state.points.map { it.territoryId }.toSet())
    }

    /**
     * The viewing selection is the only thing the year filter depends on: it never produces points
     * from another Territory, and it is not a settings value.
     */
    @Test
    fun yearFilterAppliesOnlyWithinTheViewedTerritory() {
        val otherYear = summary(2024, 9, territoryId = otherTerritory)

        val state = buildPointsUiState(
            allPoints = listOf(summary(2026, 1), summary(2025, 2), otherYear),
            selectedTerritoryId = devTerritory,
            requestedYearFilter = PointsYearFilter.Year(2025),
            viewMode = PointsViewMode.TABLE,
            selectedPointId = null,
        )

        assertEquals(listOf(2025), state.points.map { it.observationYear })
        assertEquals(setOf(devTerritory), state.points.map { it.territoryId }.toSet())
    }

    @Test
    fun yearMissingFromTheViewedTerritoryFallsBackToItsNewestYear() {
        val state = buildPointsUiState(
            allPoints = listOf(summary(2024, 1), summary(2023, 2)),
            selectedTerritoryId = devTerritory,
            requestedYearFilter = PointsYearFilter.Year(2026),
            viewMode = PointsViewMode.MAP,
            selectedPointId = null,
        )

        assertEquals(PointsYearFilter.Year(2024), state.yearFilter)
        assertEquals(listOf(2024), state.points.map { it.observationYear })
    }

    @Test
    fun emptyTerritoryProducesAnEmptyResultInsteadOfFailing() {
        val state = buildPointsUiState(
            allPoints = emptyList(),
            selectedTerritoryId = devTerritory,
            requestedYearFilter = PointsYearFilter.Year(2026),
            viewMode = PointsViewMode.MAP,
            selectedPointId = UUID.randomUUID(),
        )

        assertEquals(emptyList<UUID>(), state.points.map { it.id })
        assertEquals(emptyList<Int>(), state.availableYears)
        assertEquals(PointsYearFilter.All, state.yearFilter)
        assertNull(state.selectedPoint)
    }

    @Test
    fun resultLabelsKeepNoBeesAndUnresolvedDistinct() {
        assertEquals("Пчёлы найдены", pointResultLabel(BeePresenceResult.BEES_FOUND))
        assertEquals("Пчёлы отсутствуют", pointResultLabel(BeePresenceResult.NO_BEES_FOUND))
        assertEquals("Результат не зафиксирован", pointResultLabel(null))
    }

    @Test
    fun completedDurationUsesPersistedDepartureAndReturn() {
        val departure = Instant.parse("2026-09-17T06:00:00Z")
        assertEquals(
            "01:02:03",
            formatCompletedFlightDuration(departure, departure.plusSeconds(3_723)),
        )
    }

    private fun summary(
        year: Int,
        number: Int,
        territoryId: UUID = devTerritory,
    ) = ObservationPointSummary(
        id = UUID.randomUUID(),
        territoryId = territoryId,
        observationYear = year,
        pointNumber = number,
        code = null,
        beePresenceResult = BeePresenceResult.BEES_FOUND,
        latitude = 56.0,
        longitude = 43.0,
        gpsAccuracyM = 4.0,
        createdAt = Instant.parse("$year-01-01T00:00:00Z"),
        completedAt = Instant.parse("$year-01-01T01:00:00Z"),
        beeCount = 1,
        completedFlightCycleCount = 1,
    )
}
