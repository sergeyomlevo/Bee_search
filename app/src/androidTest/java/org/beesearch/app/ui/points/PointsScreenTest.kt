package org.beesearch.app.ui.points

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.SavedObjectMarker
import org.beesearch.app.ui.map.observationPointMarkers
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PointsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val devTerritory = Territory(
        UUID.randomUUID(), "DEV-BENCH2", "DEV Territory", "Регион", "Район", Instant.EPOCH, Instant.EPOCH,
    )
    private val otherTerritory = Territory(
        UUID.randomUUID(), "KLYAZMA-01", "Клязьма", "Регион", "Район", Instant.EPOCH, Instant.EPOCH,
    )

    @Test
    fun tableShowsResultsCountsAndOpensTheCorrectPoint() {
        val found = summary(1, BeePresenceResult.BEES_FOUND, beeCount = 2)
        val noBees = summary(2, BeePresenceResult.NO_BEES_FOUND, beeCount = 0)
        val unresolved = summary(3, null, beeCount = 0)
        var opened: UUID? = null

        composeRule.setContent {
            Bee_searchTheme {
                PointsTable(
                    points = listOf(found, noBees, unresolved),
                    onOpenPoint = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Пчёлы найдены").assertIsDisplayed()
        composeRule.onNodeWithText("Пчёлы отсутствуют").assertIsDisplayed()
        composeRule.onNodeWithText("Результат не зафиксирован").assertIsDisplayed()
        composeRule.onAllNodesWithText("Пчёл: 0", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithTag("point-row-${noBees.id}").performClick()
        composeRule.runOnIdle { assertEquals(noBees.id, opened) }
    }

    @Test
    fun savedPointMarkerOpensThatPoint() {
        val point = summary(7, BeePresenceResult.NO_BEES_FOUND, beeCount = 0)
        var selected: UUID? = null

        composeRule.setContent {
            Bee_searchTheme {
                SavedObjectMarker(
                    marker = observationPointMarkers(listOf(point)).single(),
                    onClick = { selected = point.id },
                )
            }
        }

        composeRule.onNodeWithTag("point-marker-${point.id}").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(point.id, selected) }
    }

    @Test
    fun emptyTableIsSafe() {
        composeRule.setContent {
            Bee_searchTheme { PointsTable(points = emptyList(), onOpenPoint = {}) }
        }
        composeRule.onNodeWithText("Точек за выбранный период нет").assertIsDisplayed()
    }

    @Test
    fun browserHeaderIsCompactAndShowsOneLineTitleWithBackArrow() {
        var backPressed = false
        setBrowserContent(state = browserState(points = listOf(summary(1))), onBack = { backPressed = true })

        composeRule.onNodeWithTag("screen-title").assertIsDisplayed()
            .assertTextEquals("Точки")
        // The former large text button is gone; Back is a compact arrow.
        composeRule.onNodeWithText("Назад").assertDoesNotExist()
        composeRule.onNodeWithTag("screen-back").performClick()
        composeRule.runOnIdle { assertEquals(true, backPressed) }
    }

    @Test
    fun territoryFilterShowsTheViewedTerritoryCodeAndReportsAnotherChoice() {
        var chosen: UUID? = null
        setBrowserContent(
            state = browserState(points = listOf(summary(1))),
            onSelectTerritory = { chosen = it },
        )

        composeRule.onNodeWithTag("points-territory-filter").assertIsDisplayed()
        // The selected Territory is reported by its short code, never truncated and never a UUID.
        composeRule.onNodeWithText("DEV-BENCH2 ▾").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Территория: DEV-BENCH2").assertIsDisplayed()
        composeRule.onNodeWithTag("points-territory-filter").performClick()
        // The menu names every Territory by code and name, never by UUID.
        composeRule.onNodeWithText("DEV-BENCH2 — DEV Territory").assertIsDisplayed()
        composeRule.onNodeWithTag("points-territory-${otherTerritory.id}").performClick()
        composeRule.runOnIdle { assertEquals(otherTerritory.id, chosen) }
    }

    @Test
    fun yearFilterListsOnlyYearsOfTheViewedTerritory() {
        var chosen: PointsYearFilter? = null
        setBrowserContent(
            state = browserState(points = listOf(summary(1)), yearFilter = PointsYearFilter.Year(2026)),
            onSelectYear = { chosen = it },
        )

        composeRule.onNodeWithTag("points-year-filter").assertIsDisplayed()
        composeRule.onNodeWithText("2026 ▾").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Год: 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("points-year-filter").performClick()
        composeRule.onNodeWithText("Все годы").assertIsDisplayed()
        composeRule.onNodeWithText("Все годы").performClick()
        composeRule.runOnIdle { assertEquals(PointsYearFilter.All, chosen) }
    }

    @Test
    fun mapAndTableModesShowTheSameFilteredPoints() {
        val first = summary(1)
        val second = summary(2)
        var mode: PointsViewMode = PointsViewMode.TABLE
        val state = browserState(points = listOf(first, second), viewMode = PointsViewMode.TABLE)

        composeRule.setContent {
            Bee_searchTheme {
                PointsScreen(
                    territories = listOf(devTerritory, otherTerritory),
                    state = state,
                    mapAreaStore = UnusedMapAreaStore(),
                    mapPackageStore = UnusedMapPackageStore(),
                    onBack = {},
                    onSelectTerritory = {},
                    onSelectYear = {},
                    onSelectViewMode = { mode = it },
                    onSelectPoint = {},
                    onOpenPoint = {},
                )
            }
        }

        composeRule.onNodeWithTag("point-row-${first.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("point-row-${second.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("points-mode-map").performClick()
        composeRule.runOnIdle { assertEquals(PointsViewMode.MAP, mode) }
    }

    @Test
    fun massActionMenuOffersExportAndDeleteWithConfirmation() {
        var exportConfirmed = 0
        var deleteConfirmed = 0
        setBrowserContent(
            state = browserState(points = listOf(summary(1)), counts = ObservationDataCounts(3, 9, 23)),
            onExportAll = { exportConfirmed += 1 },
            onDeleteAll = { deleteConfirmed += 1 },
        )

        composeRule.onNodeWithTag("points-menu").performClick()
        composeRule.onNodeWithTag("points-menu-export").assertIsDisplayed()
        composeRule.onNodeWithTag("points-menu-delete-all").assertIsDisplayed()

        // Export is confirmed before the system file picker opens.
        composeRule.onNodeWithTag("points-menu-export").performClick()
        composeRule.onNodeWithTag("confirm-export-all").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, exportConfirmed) }

        // Delete-all states its real scope and counts, and cancelling changes nothing.
        composeRule.onNodeWithTag("points-menu").performClick()
        composeRule.onNodeWithTag("points-menu-delete-all").performClick()
        composeRule.onNodeWithText("Будут безвозвратно удалены ВСЕ точки наблюдения во всех территориях:", substring = true)
            .assertExists()
        composeRule.onNodeWithText("• точки наблюдения: 3", substring = true).assertExists()
        composeRule.onNodeWithTag("cancel-delete-all-points").performClick()
        composeRule.runOnIdle { assertEquals(0, deleteConfirmed) }

        composeRule.onNodeWithTag("points-menu").performClick()
        composeRule.onNodeWithTag("points-menu-delete-all").performClick()
        composeRule.onNodeWithTag("confirm-delete-all-points").performClick()
        composeRule.runOnIdle { assertEquals(1, deleteConfirmed) }
    }

    @Test
    fun browserMessageIsShownAndDismissible() {
        var dismissed = false
        setBrowserContent(
            state = browserState(
                points = listOf(summary(1)),
                message = PointsMessage("Экспорт завершён. Файл Bee Search сохранён.", isError = false),
            ),
            onDismissMessage = { dismissed = true },
        )

        composeRule.onNodeWithTag("points-message").assertIsDisplayed()
        composeRule.onNodeWithTag("points-message-dismiss").performClick()
        composeRule.runOnIdle { assertEquals(true, dismissed) }
    }

    private fun setBrowserContent(
        state: PointsUiState,
        onBack: () -> Unit = {},
        onSelectTerritory: (UUID) -> Unit = {},
        onSelectYear: (PointsYearFilter) -> Unit = {},
        onExportAll: () -> Unit = {},
        onDeleteAll: () -> Unit = {},
        onDismissMessage: () -> Unit = {},
        onOpenPoint: (UUID) -> Unit = {},
    ) {
        composeRule.setContent {
            Bee_searchTheme {
                PointsScreen(
                    territories = listOf(devTerritory, otherTerritory),
                    state = state,
                    mapAreaStore = UnusedMapAreaStore(),
                    mapPackageStore = UnusedMapPackageStore(),
                    onBack = onBack,
                    onSelectTerritory = onSelectTerritory,
                    onSelectYear = onSelectYear,
                    onSelectViewMode = {},
                    onSelectPoint = {},
                    onOpenPoint = onOpenPoint,
                    onExportAll = onExportAll,
                    onDeleteAll = onDeleteAll,
                    onDismissMessage = onDismissMessage,
                )
            }
        }
    }

    private fun browserState(
        points: List<ObservationPointSummary>,
        viewMode: PointsViewMode = PointsViewMode.TABLE,
        yearFilter: PointsYearFilter = PointsYearFilter.Year(2026),
        selectedPoint: ObservationPointSummary? = null,
        counts: ObservationDataCounts? = null,
        message: PointsMessage? = null,
    ) = PointsUiState(
        points = points,
        selectedTerritoryId = devTerritory.id,
        availableYears = listOf(2026),
        yearFilter = yearFilter,
        viewMode = viewMode,
        selectedPoint = selectedPoint,
        isLoading = false,
        counts = counts,
        message = message,
    )

    private fun summary(
        number: Int,
        result: BeePresenceResult? = BeePresenceResult.BEES_FOUND,
        beeCount: Int = 1,
    ) = ObservationPointSummary(
        id = UUID.randomUUID(), territoryId = devTerritory.id, observationYear = 2026,
        pointNumber = number, code = null, beePresenceResult = result,
        latitude = 56.0, longitude = 43.0, gpsAccuracyM = 4.0,
        createdAt = Instant.parse("2026-09-17T08:00:00Z"), completedAt = null,
        beeCount = beeCount, completedFlightCycleCount = 1,
    )
}
