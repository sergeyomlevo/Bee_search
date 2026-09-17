package org.beesearch.app.ui.points

import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointsPresentationTest {
    @Test
    fun defaultUsesLatestAvailableYearAndSpecificYearFiltersOnce() {
        val old = summary(2025, 1)
        val newest = summary(2026, 2)

        val initial = buildPointsUiState(
            allPoints = listOf(newest, old),
            requestedFilter = null,
            viewMode = PointsViewMode.MAP,
            selectedPointId = null,
        )
        val oldYear = buildPointsUiState(
            allPoints = listOf(newest, old),
            requestedFilter = PointsYearFilter.Year(2025),
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
        val map = buildPointsUiState(points, PointsYearFilter.All, PointsViewMode.MAP, null)
        val table = buildPointsUiState(points, PointsYearFilter.All, PointsViewMode.TABLE, null)

        assertEquals(points.map { it.id }, map.points.map { it.id })
        assertEquals(map.points.map { it.id }, table.points.map { it.id })
        assertNull(table.selectedPoint)
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

    private fun summary(year: Int, number: Int) = ObservationPointSummary(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
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
