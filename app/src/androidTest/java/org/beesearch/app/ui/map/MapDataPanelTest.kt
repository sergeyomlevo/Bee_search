package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import java.time.LocalDate
import java.time.YearMonth
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.model.CountRange
import org.beesearch.app.domain.model.ObservationPointFilterSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Approved «Данные на карте» behaviour on the production composables.
 *
 * The harness applies every callback to the real display state, so these tests exercise the same
 * chain the map screen uses: a period is passed to one type only, hiding a type keeps its filter, and
 * nothing but `Сбросить фильтры` clears more than one type.
 */
class MapDataPanelTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 9)
    private val augustSeptember2026 = ResearchDateInterval(
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 9, 30),
    )
    private val year2024 = ResearchDateInterval(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))

    @Test
    fun panelShowsOnlyTypesWithAUserReachableLifecycle() {
        setPanel()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        MapDataType.entries.forEach { type ->
            rule.node("map-data-type-${type.name}").assertIsDisplayed()
            rule.nodeText(type.userName).assertIsDisplayed()
        }
        // Apiary has no user lifecycle and Trap has no domain type: neither is promised in the UI,
        // not even as a disabled row.
        rule.nodeText("Пасеки").assertDoesNotExist()
        rule.nodeText("Осмотры").assertDoesNotExist()
        rule.nodeText("Ловушки").assertDoesNotExist()
        rule.node(MAP_DATA_RESET_TAG).assertIsDisplayed()
        rule.node(MAP_DATA_DONE_TAG).assertIsDisplayed()
    }

    @Test
    fun defaultStateShowsEveryTypeVisibleWithAllTimeAndNoPeriod() {
        setPanel()
        MapDataType.entries.forEach { type ->
            rule.node("map-data-visibility-${type.name}").assertIsOn()
            rule.node("map-data-summary-${type.name}").assertTextEquals("Все данные")
        }
    }

    @Test
    fun hidingATypeChangesVisibilityOnlyAndKeepsItsPeriod() {
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))

        rule.node("map-data-visibility-HOLLOW").performClick()

        assertEquals(false, state.display.isVisible(MapDataType.HOLLOW))
        // The filter survived the switch, the summary still names it, and no type screen opened.
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
        rule.node("map-data-summary-HOLLOW").assertTextEquals("авг–сен 2026")
        rule.node("map-data-type-screen-HOLLOW").assertDoesNotExist()
        // Nothing else changed.
        assertNull(state.display.period(MapDataType.OBSERVATION_POINT))
        assertTrue(state.display.isVisible(MapDataType.OBSERVATION_POINT))

        // Showing it again restores exactly the same filter.
        rule.node("map-data-visibility-HOLLOW").performClick()
        assertEquals(true, state.display.isVisible(MapDataType.HOLLOW))
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
    }

    @Test
    fun rowTapOpensTheTypeFiltersScreenWithoutTouchingVisibility() {
        setPanel()
        rule.node("map-data-type-LOG_HIVE").performClick()

        rule.node("map-data-type-screen-LOG_HIVE").assertIsDisplayed()
        // `Период` is expanded on entry because it is the only defined filter.
        rule.node("period-row-LOG_HIVE").assertIsDisplayed()
        rule.node("period-editor-LOG_HIVE").assertIsDisplayed()
        assertTrue(state.display.isVisible(MapDataType.LOG_HIVE))

        rule.onNodeWithContentDescription("Назад").performClick()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
    }

    @Test
    fun collapsingThePeriodKeepsItsValueAndExpandingItShowsTheEditorAgain() {
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()

        rule.node("period-row-HOLLOW").performClick()
        rule.node("period-editor-HOLLOW").assertDoesNotExist()
        // The collapsed row still names the value, and the value itself did not change.
        rule.node("period-summary-HOLLOW").assertTextEquals("авг–сен 2026")
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))

        rule.node("period-row-HOLLOW").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
    }

    @Test
    fun selectingAYearAppliesImmediatelyAndOnlyToThatType() {
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.LOG_HIVE, augustSeptember2026))
        rule.node("map-data-type-OBSERVATION_POINT").performClick()

        rule.node("period-years-OBSERVATION_POINT").performScrollTo()
        rule.node("2026").performClick()

        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            state.display.period(MapDataType.OBSERVATION_POINT),
        )
        // The other type keeps its own period untouched.
        assertEquals(augustSeptember2026, state.display.period(MapDataType.LOG_HIVE))
        rule.node("period-summary-OBSERVATION_POINT").assertTextEquals("2026")
    }

    @Test
    fun tappingTheSameUnitTwiceClearsOnlyThatType() {
        setPanel(
            initial = DEFAULT_MAP_DATA_DISPLAY
                .withPeriod(MapDataType.OBSERVATION_POINT, augustSeptember2026),
        )
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-years-HOLLOW").performScrollTo()
        rule.node("2025").performClick()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
            state.display.period(MapDataType.HOLLOW),
        )

        rule.node("2025").performClick()
        assertNull(state.display.period(MapDataType.HOLLOW))
        rule.node("period-summary-HOLLOW").assertTextEquals(ALL_TIME_SUMMARY)
        // The other type was never touched by this.
        assertEquals(augustSeptember2026, state.display.period(MapDataType.OBSERVATION_POINT))
    }

    @Test
    fun numericRangesApplyOpenBoundsAndLocalResetKeepsPeriod() {
        val period = augustSeptember2026
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withFilters(
            MapDataType.OBSERVATION_POINT,
            ObservationPointFilterSet(dateInterval = period),
        ))
        rule.node("map-data-type-OBSERVATION_POINT").performClick()
        rule.node("filter-row-bee-count-OBSERVATION_POINT").performClick()
        rule.node("filter-bee-count-min-OBSERVATION_POINT").performTextInput("2")
        assertEquals(CountRange(min = 2), (state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet).beeCount)
        assertEquals(period, state.display.period(MapDataType.OBSERVATION_POINT))
        rule.node("filter-reset-bee-count-OBSERVATION_POINT").performClick()
        assertEquals(CountRange(), (state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet).beeCount)
        assertEquals(period, state.display.period(MapDataType.OBSERVATION_POINT))
        rule.node("filter-row-flight-cycle-count-OBSERVATION_POINT").performClick()
        rule.node("filter-flight-cycle-count-min-OBSERVATION_POINT").performTextInput("1")
        rule.node("filter-flight-cycle-count-max-OBSERVATION_POINT").performTextInput("3")
        assertEquals(CountRange(1, 3), (state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet).flightCycleCount)
        rule.node("period-row-OBSERVATION_POINT").performClick()
        rule.node("period-reset-OBSERVATION_POINT").performClick()
        val finalFilters = state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet
        assertNull(finalFilters.dateInterval)
        assertEquals(CountRange(1, 3), finalFilters.flightCycleCount)
    }

    @Test
    fun invalidNumericDraftShowsErrorAndResetClearsItWithoutChangingQuery() {
        setPanel()
        rule.node("map-data-type-OBSERVATION_POINT").performClick()
        rule.node("filter-row-bee-count-OBSERVATION_POINT").performClick()
        rule.node("filter-bee-count-min-OBSERVATION_POINT").performTextInput("-1")
        rule.node("filter-bee-count-error-OBSERVATION_POINT").assertIsDisplayed()
        assertEquals(CountRange(), (state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet).beeCount)
        rule.node("filter-reset-bee-count-OBSERVATION_POINT").performClick()
        rule.node("filter-bee-count-error-OBSERVATION_POINT").assertDoesNotExist()
        assertEquals(CountRange(), (state.display.display(MapDataType.OBSERVATION_POINT).filters as ObservationPointFilterSet).beeCount)
    }

    @Test
    fun navigationToAnotherYearBuildsOneRangeAcrossTheYearBoundary() {
        setPanel()
        rule.node("map-data-type-LOG_HIVE").performClick()
        rule.node("period-precision-LOG_HIVE-MONTH").performClick()
        rule.node("period-month-grid-LOG_HIVE").performScrollTo()
        rule.node("янв 2026").performClick()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
            state.display.period(MapDataType.LOG_HIVE),
        )

        // Navigation is not a selection: the value stays while the grid moves to the previous year.
        rule.node("period-months-LOG_HIVE-previous").performClick()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
            state.display.period(MapDataType.LOG_HIVE),
        )
        rule.node("period-month-grid-LOG_HIVE").performScrollTo()
        rule.node("дек 2025").performClick()

        assertEquals(
            ResearchDateInterval(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31)),
            state.display.period(MapDataType.LOG_HIVE),
        )
        rule.node("period-summary-LOG_HIVE").assertTextEquals("дек 2025 – янв 2026")
    }

    @Test
    fun switchingPrecisionNeverChangesTheFilter() {
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))
        rule.node("map-data-type-HOLLOW").performClick()
        // Entering the type screen shows the coarsest precision that expresses the interval.
        rule.node("period-precision-HOLLOW-MONTH").assertIsDisplayed()
        rule.node("period-precision-HOLLOW-DAY").performClick()

        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
        rule.node("period-summary-HOLLOW").assertTextEquals("авг–сен 2026")
        rule.node("period-days-HOLLOW-label").assertIsDisplayed()

        // A day remains a day, and a precision that cannot express it keeps the value as it is.
        rule.node("period-day-grid-HOLLOW").performScrollTo()
        rule.node("01.08.2026").performClick()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
            state.display.period(MapDataType.HOLLOW),
        )
        rule.node("period-precision-HOLLOW-MONTH").performClick()
        assertNull(projectPeriod(state.display.period(MapDataType.HOLLOW), PeriodPrecision.MONTH))
        rule.node("period-summary-HOLLOW").assertTextEquals("01.08.2026")
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
            state.display.period(MapDataType.HOLLOW),
        )
    }

    @Test
    fun resetInsideTheSectionClearsOneTypeAndKeepsTheSectionOpen() {
        setPanel(
            initial = DEFAULT_MAP_DATA_DISPLAY
                .withPeriod(MapDataType.HOLLOW, augustSeptember2026)
                .withPeriod(MapDataType.OBSERVATION_POINT, augustSeptember2026)
                .withVisibility(MapDataType.LOG_HIVE, false),
        )
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-reset-HOLLOW").performScrollTo()
        rule.node("period-reset-HOLLOW").performClick()

        assertNull(state.display.period(MapDataType.HOLLOW))
        // Still expanded, because collapsing would hide the result of the action.
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        rule.node("period-summary-HOLLOW").assertTextEquals(ALL_TIME_SUMMARY)
        assertEquals(augustSeptember2026, state.display.period(MapDataType.OBSERVATION_POINT))
        assertEquals(false, state.display.isVisible(MapDataType.LOG_HIVE))
    }

    @Test
    fun globalResetClearsEveryPeriodAndChangesNoVisibility() {
        setPanel(
            initial = DEFAULT_MAP_DATA_DISPLAY
                .withPeriod(MapDataType.HOLLOW, augustSeptember2026)
                .withPeriod(MapDataType.OBSERVATION_POINT, augustSeptember2026)
                .withVisibility(MapDataType.LOG_HIVE, false),
        )
        rule.node(MAP_DATA_RESET_TAG).performClick()

        MapDataType.entries.forEach { type -> assertNull(state.display.period(type)) }
        assertEquals(false, state.display.isVisible(MapDataType.LOG_HIVE))
        assertEquals(true, state.display.isVisible(MapDataType.HOLLOW))
        rule.node("map-data-summary-HOLLOW").assertTextEquals("Все данные")
        rule.node("map-data-summary-LOG_HIVE").assertTextEquals("Все данные")
    }

    @Test
    fun goingBackAndClosingThePanelKeepsEveryFilter() {
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))
        rule.node("map-data-type-HOLLOW").performClick()
        // The screen opened on the coarsest precision that expresses the interval; the user can move
        // to another precision and the value stays what it was.
        rule.node("period-precision-HOLLOW-MONTH").assertIsDisplayed()
        rule.node("period-precision-HOLLOW-YEAR").performClick()
        rule.node("period-years-HOLLOW").performScrollTo()
        rule.node("2025").performClick()
        rule.onNodeWithContentDescription("Назад").performClick()

        // `Готово` closes the panel; the state it belongs to is not part of the panel.
        rule.node(MAP_DATA_DONE_TAG).performClick()
        assertTrue(closed)
        assertEquals(
            ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
            state.display.period(MapDataType.HOLLOW),
        )
    }

    @Test
    fun aTypeWithoutRecordsExplainsItsEmptyCalendar() {
        setPanel(availability = MapDataAvailability.EMPTY)
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-empty-HOLLOW").assertIsDisplayed()
        rule.node("period-undated-HOLLOW").assertDoesNotExist()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
    }

    @Test
    fun aTypeWhoseRecordsHaveNoKnownDateDoesNotClaimThereAreNoRecords() {
        // Legacy Hollow/LogHive records have no fixation date: they stay visible on the map while no
        // calendar unit can be offered, so the editor must describe exactly that.
        setPanel(
            availability = MapDataAvailability(
                years = mapOf(MapDataType.HOLLOW to emptyList()),
                months = mapOf(MapDataType.HOLLOW to emptyList()),
                hasRecords = mapOf(MapDataType.HOLLOW to true),
            ),
        )
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-undated-HOLLOW").assertIsDisplayed()
        rule.node("period-empty-HOLLOW").assertDoesNotExist()
    }

    @Test
    fun everyCalendarUnitKeepsTheRequiredTouchTargetAndDayCellsShowNumbers() {
        setPanel()
        rule.node("map-data-type-LOG_HIVE").performClick()
        rule.node("period-years-LOG_HIVE").performScrollTo()
        rule.node("2026").assertWidthIsAtLeast(MIN_TOUCH_TARGET)

        rule.node("period-precision-LOG_HIVE-DAY").performClick()
        rule.node("period-day-grid-LOG_HIVE").performScrollTo()
        // The approved day level shows a whole month of full-size cells; the cell shows the day
        // number while its accessible name stays the full date. Whether the grid keeps the weekday
        // layout or reflows to fewer columns is pinned by MapPeriodGridTest, which is deterministic
        // for every screen width.
        rule.onNodeWithTag("01.08.2026").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        rule.onNodeWithTag("31.08.2026").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        rule.onNodeWithTag("10.08.2026").assertTextEquals("10")
        rule.onNodeWithTag("10.08.2026")
            .assertContentDescriptionEquals(dayUnit(LocalDate.of(2026, 8, 10)).label)
    }

    @Test
    fun monthNavigationStaysInsideTheYearsThatHaveRecords() {
        // A stored period may point at a year that no longer has records; the editor must not open a
        // year whose grid has no selectable unit and where both arrows are disabled.
        setPanel(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, year2024))
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-precision-HOLLOW-MONTH").performClick()

        rule.node("period-months-HOLLOW-label").assertTextEquals("2025")
        rule.onNodeWithContentDescription("Следующий год").assertIsEnabled()
        rule.node("period-months-HOLLOW-next").performClick()
        rule.node("period-months-HOLLOW-label").assertTextEquals("2026")
    }

    @Test
    fun reopeningThePanelStartsFromTheTypeListAgain() {
        state = MapDataUiState(
            today = today,
            display = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026),
            availability = defaultAvailability(),
            panelOpen = true,
        )
        rule.setContent { SheetHarness() }
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("map-data-type-screen-HOLLOW").assertExists()

        // Closing the panel from the list and opening it again starts at the type list.
        rule.node("filter-done-HOLLOW").performClick()
        rule.node("map-data-type-HOLLOW").assertExists()
        rule.node(MAP_DATA_DONE_TAG).performClick()
        rule.waitForIdle()
        assertTrue(!state.panelOpen)
        rule.node(MAP_DATA_CONTENT_TAG).assertDoesNotExist()

        rule.runOnIdle { state = state.copy(panelOpen = true) }
        rule.node(MAP_DATA_CONTENT_TAG).assertExists()
        rule.node("map-data-type-screen-HOLLOW").assertDoesNotExist()
        // The filter the user set is still the same value.
        rule.node("map-data-summary-HOLLOW").assertTextEquals("авг–сен 2026")
    }

    @Test
    fun calendarNavigationIsReachableWithoutRelyingOnItsGlyph() {
        setPanel()
        rule.node("map-data-type-LOG_HIVE").performClick()
        rule.node("period-precision-LOG_HIVE-MONTH").performClick()
        rule.onNodeWithContentDescription("Предыдущий год").assertIsDisplayed()
        // An icon-only action owns a full touch target in both dimensions, not only in height.
        rule.node("period-months-LOG_HIVE-previous").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        rule.node("period-months-LOG_HIVE-next").assertWidthIsAtLeast(MIN_TOUCH_TARGET)

        rule.node("period-precision-LOG_HIVE-DAY").performClick()
        rule.onNodeWithContentDescription("Следующий месяц").assertIsDisplayed()
        rule.onNodeWithContentDescription("Предыдущий месяц").assertIsDisplayed()
        rule.node("period-days-LOG_HIVE-previous").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        rule.node("period-days-LOG_HIVE-next").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun thePanelCloseActionOwnsAFullTouchTarget() {
        setPanel()
        rule.node("map-data-close").assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        rule.node("map-data-close").assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun theTypeScreenKeepsTheApprovedBottomActions() {
        setSheet(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-reset-HOLLOW").assertIsDisplayed()
        rule.node(MAP_DATA_FILTER_FOOTER_TAG).assertIsDisplayed()
        rule.node("filter-done-HOLLOW").assertIsDisplayed()
        rule.onAllNodesWithTag("filter-done-HOLLOW", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("Готово", useUnmergedTree = true).assertCountEquals(1)
        rule.node("period-done-HOLLOW").assertDoesNotExist()
        rule.node("filter-done-bee-count-HOLLOW").assertDoesNotExist()

        // «Готово» finishes this type and returns to the list; the panel itself stays open.
        rule.node("filter-done-HOLLOW").performClick()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        assertTrue(state.panelOpen)
        // Finishing a type never resets the filter it displayed.
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
    }

    @Test
    fun everyTypeHasExactlyOneDoneOutsideScrollableAccordionContent() {
        setSheet()
        MapDataType.entries.forEach { type ->
            rule.node("map-data-type-${type.name}").performClick()
            rule.node(MAP_DATA_FILTER_CONTENT_TAG).assertIsDisplayed()
            rule.node(MAP_DATA_FILTER_FOOTER_TAG).assertIsDisplayed()
            rule.onAllNodesWithTag("filter-done-${type.name}", useUnmergedTree = true).assertCountEquals(1)
            rule.onAllNodesWithText("Готово", useUnmergedTree = true).assertCountEquals(1)
            assertDoneIsOnlyInFooter(type)
            rule.node("period-done-${type.name}").assertDoesNotExist()
            rule.node("filter-done-bee-count-${type.name}").assertDoesNotExist()
            rule.node("filter-done-flight-cycle-count-${type.name}").assertDoesNotExist()
            rule.node("filter-done-entrance-height-${type.name}").assertDoesNotExist()
            rule.node("filter-done-outer-diameter-${type.name}").assertDoesNotExist()

            val sections = when (type) {
                MapDataType.OBSERVATION_POINT -> listOf(
                    "period-row-${type.name}" to "period-editor-${type.name}",
                    "filter-row-bee-count-${type.name}" to "filter-bee-count-min-${type.name}",
                    "filter-row-flight-cycle-count-${type.name}" to "filter-flight-cycle-count-min-${type.name}",
                )
                MapDataType.HOLLOW, MapDataType.LOG_HIVE -> listOf(
                    "period-row-${type.name}" to "period-editor-${type.name}",
                    "filter-row-entrance-height-${type.name}" to "filter-entrance-height-min-${type.name}",
                    "filter-row-outer-diameter-${type.name}" to "filter-outer-diameter-min-${type.name}",
                )
            }
            sections.forEach { (rowTag, editorTag) ->
                rule.node(rowTag).performScrollTo()
                if (rule.onAllNodesWithTag(editorTag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                    rule.node(rowTag).performClick()
                }
                rule.node(editorTag).performScrollTo()
                rule.node(editorTag).assertIsDisplayed()
                rule.node(MAP_DATA_FILTER_FOOTER_TAG).assertIsDisplayed()
                rule.onAllNodesWithTag("filter-done-${type.name}", useUnmergedTree = true).assertCountEquals(1)
                assertDoneIsOnlyInFooter(type)
            }
            rule.node("filter-done-${type.name}").performClick()
            rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        }
    }

    private fun assertDoneIsOnlyInFooter(type: MapDataType) {
        rule.node("filter-done-${type.name}").assert(
            hasAnyAncestor(hasTestTag(MAP_DATA_FILTER_FOOTER_TAG)),
        )
        rule.node("filter-done-${type.name}").assert(
            hasAnyAncestor(hasTestTag(MAP_DATA_FILTER_CONTENT_TAG)).not(),
        )
    }

    @Test
    fun theBottomPanelOffersTheDisplayActionAndStatesRestrictionsInWords() {
        var opened = 0
        rule.setContent {
            MapBottomPanel(
                onOpenObjects = {},
                onOpenSettings = {},
                onOpenMapData = { opened += 1 },
                mapDataRestricted = false,
            )
        }
        rule.onNodeWithContentDescription(MAP_DATA_DESCRIPTION).assertIsDisplayed()
        rule.node(MAP_DATA_INDICATOR_TAG).assertDoesNotExist()
        rule.node("open-map-data").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun anActiveRestrictionAddsTheIndicatorAndSaysSoWithoutRelyingOnColour() {
        rule.setContent {
            MapBottomPanel(
                onOpenObjects = {},
                onOpenSettings = {},
                onOpenMapData = {},
                mapDataRestricted = true,
            )
        }
        rule.node(MAP_DATA_INDICATOR_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("$MAP_DATA_DESCRIPTION. Есть активные ограничения отображения.")
            .assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsFromTheTypeScreenToThePanelBeforeClosingIt() {
        setSheet()
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("map-data-type-screen-HOLLOW").assertExists()

        // Back on the type screen returns to the panel and does not close it or reset anything.
        Espresso.pressBack()
        rule.node(MAP_DATA_CONTENT_TAG).assertExists()
        assertTrue(state.panelOpen)

        // With no type screen open, Back belongs to the panel again.
        Espresso.pressBack()
        rule.waitForIdle()
        assertTrue(!state.panelOpen)
    }

    // ---- Owner regression gate: one panel session must survive every change in it. --------------
    // These tests drive the real ModalBottomSheet, not the body alone, because the defect they pin was
    // about the sheet's lifetime: any action used to close it and return to the map.

    @Test
    fun sectionAVisibilityTogglesOfAllThreeTypesKeepThePanelOpen() {
        setSheet()
        MapDataType.entries.forEach { type ->
            rule.node("map-data-visibility-${type.name}").performClick()
            rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
            assertEquals(false, state.display.isVisible(type))
            // The tap belongs to the switch alone: it must not also act as a tap on the parent row.
            rule.node("map-data-type-screen-${type.name}").assertDoesNotExist()
            assertNull(state.openedType)
        }
        // One opening was enough for all three switches.
        MapDataType.entries.forEach { type ->
            assertEquals(false, state.display.isVisible(type))
            rule.node("map-data-visibility-${type.name}").performClick()
            rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
            assertNull(state.openedType)
        }
        MapDataType.entries.forEach { type -> assertEquals(true, state.display.isVisible(type)) }
    }

    @Test
    fun sectionBPeriodPrecisionSelectionKeepsTheEditorOpen() {
        setSheet()
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()

        rule.node("period-precision-HOLLOW-MONTH").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        rule.node("period-precision-HOLLOW-DAY").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        rule.node("period-precision-HOLLOW-YEAR").performClick()
        rule.node("period-editor-HOLLOW").assertIsDisplayed()

        rule.node("period-years-HOLLOW").performScrollTo()
        rule.node("2026").performClick()
        // A year choice applies immediately and the editor stays where it is.
        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        rule.node("map-data-type-screen-HOLLOW").assertIsDisplayed()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            state.display.period(MapDataType.HOLLOW),
        )
    }

    @Test
    fun sectionCBothRangeBoundariesKeepTheEditorOpen() {
        setSheet()
        rule.node("map-data-type-LOG_HIVE").performClick()
        rule.node("period-precision-LOG_HIVE-MONTH").performClick()
        rule.node("period-month-grid-LOG_HIVE").performScrollTo()

        rule.node("янв 2026").performClick()
        rule.node("period-editor-LOG_HIVE").assertIsDisplayed()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
            state.display.period(MapDataType.LOG_HIVE),
        )

        rule.node("мар 2026").performClick()
        rule.node("period-editor-LOG_HIVE").assertIsDisplayed()
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            state.display.period(MapDataType.LOG_HIVE),
        )
    }

    @Test
    fun sectionDResetInsideTheSectionKeepsTheEditorOpen() {
        setSheet(initial = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, augustSeptember2026))
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-reset-HOLLOW").performScrollTo()
        rule.node("period-reset-HOLLOW").performClick()

        rule.node("period-editor-HOLLOW").assertIsDisplayed()
        rule.node("map-data-type-screen-HOLLOW").assertIsDisplayed()
        assertNull(state.display.period(MapDataType.HOLLOW))
    }

    @Test
    fun sectionEAndFTypeScreenDoneAndBackReturnToThePanelWithoutClosingIt() {
        setSheet()
        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-row-HOLLOW").assertIsDisplayed()
        rule.node("filter-done-HOLLOW").performClick()

        // Back in the «Данные на карте» list, and the map is not the exposed surface yet.
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        rule.node("map-data-type-screen-HOLLOW").assertDoesNotExist()
        assertTrue(state.panelOpen)

        // Section F: Back from the type screen returns to the same list.
        rule.node("map-data-type-LOG_HIVE").performClick()
        rule.node("map-data-type-screen-LOG_HIVE").assertIsDisplayed()
        Espresso.pressBack()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        assertTrue(state.panelOpen)
    }

    @Test
    fun sectionGAndHBackAndDoneOnTheMainPanelCloseIt() {
        setSheet()
        Espresso.pressBack()
        rule.waitForIdle()
        assertTrue(!state.panelOpen)
        rule.node(MAP_DATA_CONTENT_TAG).assertDoesNotExist()

        // Section H: «Готово» on the main panel closes the sheet.
        rule.runOnIdle { state = state.copy(panelOpen = true) }
        rule.node(MAP_DATA_DONE_TAG).performClick()
        rule.waitForIdle()
        assertTrue(!state.panelOpen)
        rule.node(MAP_DATA_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    fun sectionITogglingVisibilityNeverResetsAPeriod() {
        setSheet(
            initial = DEFAULT_MAP_DATA_DISPLAY
                .withPeriod(MapDataType.HOLLOW, augustSeptember2026)
                .withPeriod(MapDataType.LOG_HIVE, year2024),
        )
        MapDataType.entries.forEach { type ->
            rule.node("map-data-visibility-${type.name}").performClick()
            rule.node("map-data-visibility-${type.name}").performClick()
        }
        assertEquals(augustSeptember2026, state.display.period(MapDataType.HOLLOW))
        assertEquals(year2024, state.display.period(MapDataType.LOG_HIVE))
        rule.node("map-data-summary-HOLLOW").assertTextEquals("авг–сен 2026")
        rule.node("map-data-summary-LOG_HIVE").assertTextEquals("2024")
        // Independence survives the toggling as well.
        assertNull(state.display.period(MapDataType.OBSERVATION_POINT))
    }

    @Test
    fun sectionJSeveralChangesInOneOpeningProduceThreeIndependentStates() {
        setSheet()
        // Open the panel once and do the whole session in it, exactly as the field workflow requires.
        rule.node("map-data-visibility-LOG_HIVE").performClick()
        rule.node("map-data-visibility-LOG_HIVE").performClick()

        rule.node("map-data-type-OBSERVATION_POINT").performClick()
        rule.node("period-years-OBSERVATION_POINT").performScrollTo()
        rule.node("2026").performClick()
        rule.node("filter-done-OBSERVATION_POINT").performClick()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()

        rule.node("map-data-type-HOLLOW").performClick()
        rule.node("period-precision-HOLLOW-MONTH").performClick()
        rule.node("period-month-grid-HOLLOW").performScrollTo()
        rule.node("авг 2026").performClick()
        rule.node("filter-done-HOLLOW").performClick()
        rule.node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()

        rule.node("map-data-visibility-LOG_HIVE").performClick()
        rule.node(MAP_DATA_DONE_TAG).performClick()
        rule.waitForIdle()

        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            state.display.period(MapDataType.OBSERVATION_POINT),
        )
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
            state.display.period(MapDataType.HOLLOW),
        )
        assertNull(state.display.period(MapDataType.LOG_HIVE))
        assertEquals(false, state.display.isVisible(MapDataType.LOG_HIVE))
        assertEquals(true, state.display.isVisible(MapDataType.OBSERVATION_POINT))
        assertTrue(!state.panelOpen)
    }

    private var state by mutableStateOf(MapDataUiState(today = LocalDate.of(2026, 10, 9)))
    private var closed = false

    private fun setPanel(
        initial: MapDataDisplayState = DEFAULT_MAP_DATA_DISPLAY,
        availability: MapDataAvailability = defaultAvailability(),
    ) {
        state = MapDataUiState(today = today, display = initial, availability = availability)
        closed = false
        rule.setContent { MapDataHarness() }
    }

    /**
     * Composes the real `MapDataPanel` (a real `ModalBottomSheet`) with the same wiring the map screen
     * uses: the panel session lives outside the sheet, so a change inside it cannot close it.
     */
    private fun setSheet(
        initial: MapDataDisplayState = DEFAULT_MAP_DATA_DISPLAY,
        availability: MapDataAvailability = defaultAvailability(),
    ) {
        state = MapDataUiState(
            today = today,
            display = initial,
            availability = availability,
            panelOpen = true,
        )
        rule.setContent { SheetHarness() }
    }

    @Composable
    private fun SheetHarness() {
        if (!state.panelOpen) return
        MapDataPanel(
            state = state,
            onVisibilityChange = { type, visible ->
                state = state.copy(display = state.display.withVisibility(type, visible))
            },
            onPeriodChange = { type, period ->
                state = state.copy(display = state.display.withPeriod(type, period))
            },
            onFiltersChange = { type, filters ->
                state = state.copy(display = state.display.withFilters(type, filters))
            },
            onResetPeriod = { type ->
                state = state.copy(display = state.display.withResetPeriod(type))
            },
            onResetFilters = {
                state = state.copy(display = state.display.withResetFilters())
            },
            onOpenType = { type -> state = state.copy(openedType = type) },
            onCloseType = { state = state.copy(openedType = null) },
            // Closing the panel is what the ViewModel does too: the session ends with the sheet.
            onDismiss = { state = state.copy(panelOpen = false, openedType = null) },
        )
    }

    private fun defaultAvailability() = MapDataAvailability(
        years = MapDataType.entries.associateWith { listOf(2025, 2026) },
        months = MapDataType.entries.associateWith {
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        },
        hasRecords = MapDataType.entries.associateWith { true },
    )

    @Composable
    private fun MapDataHarness() {
        var openedType by remember { mutableStateOf<MapDataType?>(null) }
        MapDataPanelBody(
            state = state,
            openedType = openedType,
            onOpenType = { openedType = it },
            onBack = { openedType = null },
            onVisibilityChange = { type, visible ->
                state = state.copy(display = state.display.withVisibility(type, visible))
            },
            onPeriodChange = { type, period ->
                state = state.copy(display = state.display.withPeriod(type, period))
            },
            onFiltersChange = { type, filters ->
                state = state.copy(display = state.display.withFilters(type, filters))
            },
            onResetPeriod = { type ->
                state = state.copy(display = state.display.withResetPeriod(type))
            },
            onResetFilters = {
                state = state.copy(display = state.display.withResetFilters())
            },
            onDismiss = { closed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Tag lookup in the unmerged tree: a tagged child of a clickable or switch row is merged into its
 * parent in the merged tree, so the child tag is only reachable there.
 */
private fun ComposeContentTestRule.node(tag: String): SemanticsNodeInteraction =
    onNodeWithTag(tag, useUnmergedTree = true)

private fun ComposeContentTestRule.nodeText(text: String): SemanticsNodeInteraction =
    onNodeWithText(text, useUnmergedTree = true)

/** Approved minimum touch target for every calendar unit, the bottom actions and the filter row. */
private val MIN_TOUCH_TARGET = 48.dp
