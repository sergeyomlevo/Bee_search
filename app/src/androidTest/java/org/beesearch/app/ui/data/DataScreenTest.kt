package org.beesearch.app.ui.data

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import org.beesearch.app.ui.settings.SettingsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DataScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsOpensDataFeature() {
        val opened = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = emptyList(),
                    currentObserverId = null,
                    territories = emptyList(),
                    currentTerritoryId = null,
                    onBack = {},
                    onOpenData = { opened.value = true },
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {},
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("settings-data").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(opened.value) }
    }

    @Test
    fun exportActionAndResultStatesArePresented() {
        val exported = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                DataScreen(
                    state = DataUiState(
                        counts = ObservationDataCounts(2, 3, 4),
                        status = DataStatus("Экспорт завершён. Файл Bee Search сохранён.", false),
                    ),
                    onBack = {},
                    onExport = { exported.value = true },
                    onDeleteCompletedPoint = {},
                    onClearObservationData = {},
                )
            }
        }

        composeRule.onNodeWithTag("export-data").performClick()
        composeRule.runOnIdle { assertTrue(exported.value) }
        composeRule.onNodeWithTag("data-screen").performScrollToIndex(3)
        composeRule.onNodeWithTag("data-success").assertIsDisplayed()
    }

    @Test
    fun exportFailureIsPresented() {
        composeRule.setContent {
            Bee_searchTheme {
                DataScreen(
                    state = DataUiState(
                        counts = ObservationDataCounts(2, 3, 4),
                        status = DataStatus("Не удалось экспортировать данные.", true),
                    ),
                    onBack = {},
                    onExport = {},
                    onDeleteCompletedPoint = {},
                    onClearObservationData = {},
                )
            }
        }
        composeRule.onNodeWithTag("data-screen").performScrollToIndex(3)
        composeRule.onNodeWithTag("data-error").assertIsDisplayed()
    }

    @Test
    fun clearRequiresConfirmationAndCancelDoesNotInvokeDelete() {
        val cleared = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                DataScreen(
                    state = DataUiState(counts = ObservationDataCounts(2, 3, 4)),
                    onBack = {},
                    onExport = {},
                    onDeleteCompletedPoint = {},
                    onClearObservationData = { cleared.value = true },
                )
            }
        }

        composeRule.onNodeWithTag("data-screen").performScrollToIndex(2)
        composeRule.onNodeWithTag("clear-observation-data").performClick()
        composeRule.onNodeWithText("Очистить данные наблюдений?").assertIsDisplayed()
        composeRule.onNodeWithText("точки наблюдения: 2", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-clear-observation-data").performClick()
        composeRule.runOnIdle { assertFalse(cleared.value) }
        composeRule.onNodeWithText("Очистить данные наблюдений?").assertDoesNotExist()

        composeRule.onNodeWithTag("data-screen").performScrollToIndex(2)
        composeRule.onNodeWithTag("clear-observation-data").performClick()
        composeRule.onNodeWithTag("confirm-clear-observation-data").performClick()
        composeRule.runOnIdle { assertTrue(cleared.value) }
    }

    @Test
    fun selectiveDeleteRequiresConfirmationAndTargetsOnlyTheSelectedPoint() {
        val selected = pointSummary(1)
        val other = pointSummary(2)
        val deletedIds = mutableListOf<UUID>()
        composeRule.setContent {
            Bee_searchTheme {
                DataScreen(
                    state = DataUiState(
                        counts = ObservationDataCounts(2, 3, 4),
                        completedPoints = listOf(selected, other),
                    ),
                    onBack = {},
                    onExport = {},
                    onDeleteCompletedPoint = { deletedIds += it },
                    onClearObservationData = {},
                )
            }
        }

        composeRule.onNodeWithTag("show-completed-points").performScrollTo().performClick()
        composeRule.onNodeWithTag("delete-point-${selected.id}").performScrollTo().performClick()
        composeRule.onNodeWithText("Удалить точку наблюдения?").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-point-details-${selected.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-delete-point").performClick()
        composeRule.runOnIdle { assertTrue(deletedIds.isEmpty()) }

        composeRule.onNodeWithTag("delete-point-${other.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-delete-point").performClick()
        composeRule.runOnIdle { assertEquals(listOf(other.id), deletedIds) }
    }

    private fun pointSummary(number: Int) = CompletedObservationPointSummary(
        id = UUID.randomUUID(),
        createdAt = Instant.parse("2026-09-0${number}T08:00:00Z"),
        observationYear = 2026,
        pointNumber = number,
        territoryCode = "T01",
        territoryName = "Территория",
        beeCount = number,
    )
}
