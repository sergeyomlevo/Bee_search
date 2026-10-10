package org.beesearch.app.ui.map

import java.time.LocalDate
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Approved «Данные на карте» state semantics.
 *
 * The point of the whole surface is that visibility and the temporal filter are two independent
 * states per type, so these tests exist to make that independence fail loudly if it is ever lost.
 */
class MapDataDisplayTest {
    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
    private val year2025 = ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))

    @Test
    fun defaultStateShowsEveryAvailableTypeWithoutPeriod() {
        val state = DEFAULT_MAP_DATA_DISPLAY
        MapDataType.entries.forEach { type ->
            assertTrue(state.isVisible(type))
            assertNull(state.period(type))
            assertEquals("Все данные", state.summary(type))
        }
        assertFalse(state.hasActiveRestriction)
        assertTrue(state.isDefault)
    }

    @Test
    fun eachTypeKeepsItsOwnPeriodAndVisibility() {
        val state = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withVisibility(MapDataType.HOLLOW, false)
            .withPeriod(MapDataType.LOG_HIVE, year2025)

        assertEquals(may2026, state.period(MapDataType.OBSERVATION_POINT))
        assertEquals(year2025, state.period(MapDataType.LOG_HIVE))
        assertNull(state.period(MapDataType.HOLLOW))
        assertFalse(state.isVisible(MapDataType.HOLLOW))
        assertTrue(state.isVisible(MapDataType.OBSERVATION_POINT))

        // One type's period never leaks into another type.
        assertEquals("май 2026", state.summary(MapDataType.OBSERVATION_POINT))
        assertEquals("2025", state.summary(MapDataType.LOG_HIVE))
        assertEquals("Все данные", state.summary(MapDataType.HOLLOW))
    }

    @Test
    fun hidingATypeRetainsItsPeriodAndShowingItRestoresTheSameValue() {
        val withPeriod = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, may2026)
        val hidden = withPeriod.withVisibility(MapDataType.HOLLOW, false)

        assertFalse(hidden.isVisible(MapDataType.HOLLOW))
        assertEquals(may2026, hidden.period(MapDataType.HOLLOW))
        // A hidden type keeps the value that its row shows in muted form.
        assertEquals("май 2026", hidden.summary(MapDataType.HOLLOW))
        // The other types were never touched.
        assertEquals(withPeriod.period(MapDataType.OBSERVATION_POINT), hidden.period(MapDataType.OBSERVATION_POINT))

        val shown = hidden.withVisibility(MapDataType.HOLLOW, true)
        assertEquals(may2026, shown.period(MapDataType.HOLLOW))
        assertEquals(withPeriod, shown)
    }

    @Test
    fun showingATypeNeverSetsOrClearsItsFilter() {
        val shown = DEFAULT_MAP_DATA_DISPLAY.withVisibility(MapDataType.LOG_HIVE, false)
            .withVisibility(MapDataType.LOG_HIVE, true)
        assertNull(shown.period(MapDataType.LOG_HIVE))
        assertEquals(DEFAULT_MAP_DATA_DISPLAY, shown)
    }

    @Test
    fun resettingOnePeriodDoesNotTouchVisibilityOrOtherTypes() {
        val state = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.LOG_HIVE, year2025)
            .withVisibility(MapDataType.HOLLOW, false)
            .withPeriod(MapDataType.HOLLOW, may2026)

        val reset = state.withResetPeriod(MapDataType.OBSERVATION_POINT)
        assertNull(reset.period(MapDataType.OBSERVATION_POINT))
        assertEquals(year2025, reset.period(MapDataType.LOG_HIVE))
        assertEquals(may2026, reset.period(MapDataType.HOLLOW))
        assertFalse(reset.isVisible(MapDataType.HOLLOW))
        assertTrue(reset.isVisible(MapDataType.OBSERVATION_POINT))
    }

    @Test
    fun globalResetClearsEveryFilterAndKeepsEveryVisibility() {
        val state = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.HOLLOW, year2025)
            .withVisibility(MapDataType.LOG_HIVE, false)

        val reset = state.withResetFilters()
        MapDataType.entries.forEach { type -> assertNull(reset.period(type)) }
        assertFalse(reset.isVisible(MapDataType.LOG_HIVE))
        assertTrue(reset.isVisible(MapDataType.OBSERVATION_POINT))
        // Reset changes no research data and no visibility, so the indicator stays on.
        assertTrue(reset.hasActiveRestriction)
    }

    @Test
    fun indicatorFollowsHiddenTypesAndActiveFilters() {
        assertFalse(DEFAULT_MAP_DATA_DISPLAY.hasActiveRestriction)
        assertTrue(DEFAULT_MAP_DATA_DISPLAY.withVisibility(MapDataType.HOLLOW, false).hasActiveRestriction)
        assertTrue(DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.LOG_HIVE, year2025).hasActiveRestriction)
        // A hidden type without a filter still counts, and clearing all filters does not switch it off.
        assertTrue(
            DEFAULT_MAP_DATA_DISPLAY.withVisibility(MapDataType.HOLLOW, false)
                .withResetFilters()
                .hasActiveRestriction,
        )
    }

    @Test
    fun unrelatedTypesDoNotShareOneFilterInstance() {
        val state = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, may2026)
        // The same interval object for two types stays two independent states.
        val both = state.withPeriod(MapDataType.LOG_HIVE, may2026)
        assertEquals(may2026, both.period(MapDataType.HOLLOW))
        assertEquals(may2026, both.period(MapDataType.LOG_HIVE))
        // Resetting one of them leaves the other one exactly as it was.
        val resetHollow = both.withResetPeriod(MapDataType.HOLLOW)
        assertNull(resetHollow.period(MapDataType.HOLLOW))
        assertEquals(may2026, resetHollow.period(MapDataType.LOG_HIVE))
    }
}
