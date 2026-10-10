package org.beesearch.app.ui.map

import java.time.LocalDate
import java.time.YearMonth
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Approved calendar model of the `Период` filter.
 *
 * One rule on every precision — one unit or one contiguous range — and the summary must always name
 * the same interval regardless of the precision the editor happens to show.
 */
class MapPeriodSelectionTest {
    private val january2026 = LocalDate.of(2026, 1, 1)

    @Test
    fun summaryUsesTheCoarsestPrecisionThatExpressesTheIntervalExactly() {
        val cases = mapOf(
            LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31) to "2026",
            LocalDate.of(2024, 1, 1) to LocalDate.of(2026, 12, 31) to "2024–2026",
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31) to "авг 2026",
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 9, 30) to "авг–сен 2026",
            LocalDate.of(2025, 12, 1) to LocalDate.of(2026, 2, 28) to "дек 2025 – фев 2026",
            LocalDate.of(2026, 8, 17) to LocalDate.of(2026, 8, 17) to "17.08.2026",
            LocalDate.of(2026, 8, 10) to LocalDate.of(2026, 9, 1) to "10.08.2026 — 01.09.2026",
        )
        cases.forEach { (range, expected) ->
            val (from, to) = range
            assertEquals(expected, periodSummary(ResearchDateInterval(from, to)))
        }
        assertEquals(ALL_TIME_SUMMARY, periodSummary(null))
    }

    @Test
    fun summaryDoesNotDependOnTheEditorPrecision() {
        val interval = ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
        // A precision can fail to express the interval, but it can never change what it means: the
        // projection is the editor's view, the interval is the filter.
        val projections = PeriodPrecision.entries.associateWith { projectPeriod(interval, it) }
        assertNull(projections.getValue(PeriodPrecision.YEAR))
        assertTrue(projections.getValue(PeriodPrecision.MONTH) != null)
        assertTrue(projections.getValue(PeriodPrecision.DAY) != null)
        // Each projection reproduces the same interval wherever it exists.
        projections.forEach { (_, selection) ->
            selection?.let { assertEquals(interval, it.interval) }
        }
        assertEquals("авг–сен 2026", periodSummary(interval))
    }

    @Test
    fun aSingleDayIsOneInclusiveUnitTap() {
        val day = dayUnit(LocalDate.of(2026, 8, 17))
        val selection = applyUnitTap(null, day)
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17)),
            selection?.interval,
        )
        assertEquals("17.08.2026", periodSummary(selection?.interval))
    }

    @Test
    fun tappingTheSelectedUnitAgainClearsThePeriod() {
        val unit = yearUnit(2026)
        val selected = applyUnitTap(null, unit)
        assertNull(applyUnitTap(selected, unit))
    }

    @Test
    fun twoUnitsBuildOneContiguousNormalizedRange() {
        val january = monthUnit(2026, 1)
        val october = monthUnit(2026, 10)
        val forward = applyUnitTap(applyUnitTap(null, january), october)
        val backward = applyUnitTap(applyUnitTap(null, october), january)
        assertEquals(forward?.interval, backward?.interval)
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31)),
            forward?.interval,
        )
        assertEquals("янв–окт 2026", periodSummary(forward?.interval))
    }

    @Test
    fun aNewTapAfterARangeStartsANewSelectionInsteadOfExtendingIt() {
        val range = applyUnitTap(applyUnitTap(null, monthUnit(2026, 1)), monthUnit(2026, 3))
        val restarted = applyUnitTap(range, monthUnit(2026, 9))
        assertTrue(restarted?.isSingleUnit == true)
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            restarted?.interval,
        )
    }

    @Test
    fun rangeCanCrossTheYearBoundaryAtMonthAndDayPrecision() {
        val december = applyUnitTap(null, monthUnit(2025, 12))
        val february = applyUnitTap(december, monthUnit(2026, 2))
        assertEquals(
            ResearchDateInterval(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28)),
            february?.interval,
        )
        val days = applyUnitTap(applyUnitTap(null, dayUnit(LocalDate.of(2026, 8, 10))), dayUnit(LocalDate.of(2026, 9, 1)))
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 1)),
            days?.interval,
        )
    }

    @Test
    fun precisionProjectionShowsTheIntervalOrNoSelectionAtAll() {
        val yearSelection = projectPeriod(ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), PeriodPrecision.MONTH)
        assertEquals(monthUnit(2026, 1), yearSelection?.first)
        assertEquals(monthUnit(2026, 12), yearSelection?.second)

        val months = ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
        val days = projectPeriod(months, PeriodPrecision.DAY)
        assertEquals(dayUnit(LocalDate.of(2026, 8, 1)), days?.first)
        assertEquals(dayUnit(LocalDate.of(2026, 9, 30)), days?.second)

        // An interval that a precision cannot express shows no selection and is never changed.
        val partialMonth = ResearchDateInterval(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 1))
        assertNull(projectPeriod(partialMonth, PeriodPrecision.MONTH))
        assertNull(projectPeriod(partialMonth, PeriodPrecision.YEAR))
        assertNull(projectPeriod(months, PeriodPrecision.YEAR))
        assertNull(projectPeriod(null, PeriodPrecision.MONTH))
        assertEquals("10.08.2026 — 01.09.2026", periodSummary(partialMonth))
    }

    @Test
    fun entryPrecisionIsTheCoarsestOneThatExpressesTheInterval() {
        assertEquals(PeriodPrecision.YEAR, coarsestPrecision(null))
        assertEquals(
            PeriodPrecision.YEAR,
            coarsestPrecision(ResearchDateInterval(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31))),
        )
        assertEquals(
            PeriodPrecision.MONTH,
            coarsestPrecision(ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))),
        )
        assertEquals(
            PeriodPrecision.DAY,
            coarsestPrecision(ResearchDateInterval(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17))),
        )
        assertEquals(
            PeriodPrecision.DAY,
            coarsestPrecision(ResearchDateInterval(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 1))),
        )
    }

    @Test
    fun availableUnitsComeFromRecordsOnly() {
        val dates = listOf(
            LocalDate.of(2026, 8, 2), LocalDate.of(2024, 5, 9), LocalDate.of(2026, 3, 1),
        )
        assertEquals(listOf(2024, 2026), availableYearsOf(dates))
        assertEquals(listOf(YearMonth.of(2024, 5), YearMonth.of(2026, 3), YearMonth.of(2026, 8)), availableMonthsOf(dates))
        assertEquals(emptyList<Int>(), availableYearsOf(emptyList()))
    }

    @Test
    fun entryPositionUsesSelectionThenNewestRecordThenToday() {
        val years = listOf(2024, 2026)
        assertEquals(2026, initialMonthLevelYear(null, years, LocalDate.of(2027, 1, 5)))
        assertEquals(2024, initialMonthLevelYear(applyUnitTap(null, yearUnit(2024)), years, LocalDate.of(2027, 1, 5)))
        assertEquals(2027, initialMonthLevelYear(null, emptyList(), LocalDate.of(2027, 1, 5)))

        val months = listOf(YearMonth.of(2024, 5), YearMonth.of(2026, 8))
        assertEquals(YearMonth.of(2026, 8), initialDayLevelMonth(null, months, LocalDate.of(2027, 1, 5)))
        assertEquals(
            YearMonth.of(2024, 5),
            initialDayLevelMonth(applyUnitTap(null, monthUnit(2024, 5)), months, LocalDate.of(2027, 1, 5)),
        )
        assertEquals(YearMonth.of(2027, 1), initialDayLevelMonth(null, emptyList(), LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun aYearSelectionNormalizesToWholeCalendarBounds() {
        val unit = yearUnit(2025)
        assertEquals(LocalDate.of(2025, 1, 1), unit.start)
        assertEquals(LocalDate.of(2025, 12, 31), unit.end)
        assertEquals("2025", unit.label)
        assertEquals(january2026, yearUnit(2026).start)
    }
}
