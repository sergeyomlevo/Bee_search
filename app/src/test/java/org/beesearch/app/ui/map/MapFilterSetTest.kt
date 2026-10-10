package org.beesearch.app.ui.map

import java.time.LocalDate
import org.beesearch.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MapFilterSetTest {
    private val type = MapDataType.OBSERVATION_POINT
    private val year = ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
    private val filters = ObservationPointFilterSet(year, CountRange(min = 5), CountRange(min = 10))

    @Test fun visibilityAndPeriodResetPreserveCountsAndOtherTypes() {
        val state = DEFAULT_MAP_DATA_DISPLAY.withFilters(type, filters)
            .withPeriod(MapDataType.LOG_HIVE, year).withVisibility(type, false)
        assertEquals(filters, state.display(type).filters)
        val reset = state.withResetPeriod(type)
        assertEquals(filters.copy(dateInterval = null), reset.display(type).filters)
        assertEquals(year, reset.period(MapDataType.LOG_HIVE))
        assertFalse(reset.isVisible(type))
        assertEquals("2026 · пчёл ≥ 5 · циклов ≥ 10", state.summary(type))
        assertEquals("Все данные", state.summary(MapDataType.HOLLOW))
    }

    @Test fun allCriteriaRoundTripAndGlobalResetRetainsVisibility() {
        val state = DEFAULT_MAP_DATA_DISPLAY.withFilters(type, filters)
            .withFilters(MapDataType.HOLLOW, PhysicalObjectFilterSet(year, MeasurementRange(150.5, 300.0), MeasurementRange(max = 50.0)))
            .withVisibility(MapDataType.HOLLOW, false)
        assertEquals(state, MapDataDisplayCodec.decode(MapDataDisplayCodec.encode(state)))
        val reset = state.withResetFilters()
        assertEquals(ObservationPointFilterSet(), reset.display(type).filters)
        assertEquals(PhysicalObjectFilterSet(), reset.display(MapDataType.HOLLOW).filters)
        assertFalse(reset.isVisible(MapDataType.HOLLOW))
    }

    @Test fun legacyV1RetainsVisibilityAndPeriodWithOpenNewCriteria() {
        val state = MapDataDisplayCodec.decode("""v1|{"types":{"HOLLOW":{"visible":false,"from":"2026-01-01","to":"2026-12-31"}}}""")!!
        assertFalse(state.isVisible(MapDataType.HOLLOW))
        assertEquals(PhysicalObjectFilterSet(year), state.display(MapDataType.HOLLOW).filters)
        assertEquals(ObservationPointFilterSet(), state.display(type).filters)
    }

    @Test fun invalidBoundsAreRejectedInsteadOfApplied() {
        for (block in listOf<() -> Any>({ CountRange(-1) }, { CountRange(5, 4) },
            { MeasurementRange(Double.NaN) }, { MeasurementRange(2.0, 1.0) })) {
            assertThrows(IllegalArgumentException::class.java) { block() }
        }
        val encoded = MapDataDisplayCodec.encode(DEFAULT_MAP_DATA_DISPLAY.withFilters(type, filters))
        assertNull(MapDataDisplayCodec.decode(encoded.replace("\"beeMin\":5", "\"beeMin\":-1")))
    }

    @Test fun exactOwnerCalendarRangesRetainFirstBoundary() {
        fun range(first: PeriodUnit, last: PeriodUnit) = applyUnitTap(applyUnitTap(null, first), last)!!.interval
        assertEquals(ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 31)), range(monthUnit(2026, 5), monthUnit(2026, 7)))
        assertEquals(ResearchDateInterval(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30)), range(monthUnit(2026, 3), monthUnit(2026, 9)))
        assertEquals(ResearchDateInterval(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28)), range(monthUnit(2025, 12), monthUnit(2026, 2)))
        assertEquals(ResearchDateInterval(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31)), range(yearUnit(2024), yearUnit(2026)))
        assertEquals(ResearchDateInterval(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 15)), range(dayUnit(LocalDate.of(2026, 5, 10)), dayUnit(LocalDate.of(2026, 5, 15))))
        assertEquals(ResearchDateInterval(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 10)), applyUnitTap(null, dayUnit(LocalDate.of(2026, 5, 10)))!!.interval)
    }
}
