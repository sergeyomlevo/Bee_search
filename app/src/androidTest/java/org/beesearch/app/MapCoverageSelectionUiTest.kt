package org.beesearch.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.ui.map.ADD_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.map.CLEAR_COVERAGE_CONFIRM_LABEL
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.CLEAR_COVERAGE_DIALOG_TAG
import org.beesearch.app.ui.map.CLEAR_COVERAGE_LABEL
import org.beesearch.app.ui.map.CURRENT_COVERAGE_SUMMARY_TAG
import org.beesearch.app.ui.map.ClearCoverageSelectionDialog
import org.beesearch.app.ui.map.CoverageUnsavedChangesDialog
import org.beesearch.app.ui.map.DISCARD_COVERAGE_CHANGES_LABEL
import org.beesearch.app.ui.map.DONE_COVERAGE_SELECTION_LABEL
import org.beesearch.app.ui.map.MapCoverageSelectionControls
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.OfflineMapPackagePanel
import org.beesearch.app.ui.map.OFFLINE_MAP_PACKAGE_PANEL_TAG
import org.beesearch.app.ui.map.SAVE_COVERAGE_CHANGES_LABEL
import org.beesearch.app.ui.map.SHOW_ALL_COVERAGE_LABEL
import org.beesearch.app.ui.map.STAY_IN_COVERAGE_SELECTION_LABEL
import org.beesearch.app.ui.map.UNDO_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.map.UNSAVED_COVERAGE_CHANGES_DIALOG_TAG
import org.beesearch.app.ui.map.coverageBoundsSummary
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapCoverageSelectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentViewportSummaryShowsBoundsAndMapParameters() {
        val summary = coverageBoundsSummary(
            MapGeoBounds(
                north = 56.21,
                east = 42.80,
                south = 56.18,
                west = 42.75,
            ),
        )

        composeRule.setContent {
            Bee_searchTheme {
                MapCoverageSelectionControls(
                    fragmentCount = 0,
                    viewportSummary = summary,
                    canAddFragment = true,
                    onAddFragment = {},
                    onUndo = {},
                    onShowAll = {},
                    onClear = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithTag(CURRENT_COVERAGE_SUMMARY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Текущий участок").assertIsDisplayed()
        composeRule.onNodeWithText("С: 56.210000  Ю: 56.180000").assertIsDisplayed()
        composeRule.onNodeWithText("З: 42.750000  В: 42.800000").assertIsDisplayed()
        composeRule.onNodeWithText("км²", substring = true).assertIsDisplayed()
    }

    @Test
    fun editorHasNoActionThatLeavesWithoutSaving() {
        composeRule.setContent { Bee_searchTheme { EditorControls() } }

        // "Готово" is the only exit from the editor. The removed plain exit is what discarded a
        // finished selection without asking, so it must not come back under any label.
        composeRule.onNodeWithText("Выйти").assertDoesNotExist()
        composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertIsDisplayed()
    }

    @Test
    fun editorActionsCarryUnambiguousNames() {
        composeRule.setContent { Bee_searchTheme { EditorControls() } }

        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(UNDO_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(SHOW_ALL_COVERAGE_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(CLEAR_COVERAGE_LABEL).assertIsDisplayed()

        composeRule.onNodeWithText("Отмена").assertDoesNotExist()
        composeRule.onNodeWithText("Сброс").assertDoesNotExist()
    }

    @Test
    fun editorActionsAreDisabledWhileNothingIsSelected() {
        composeRule.setContent { Bee_searchTheme { EditorControls(fragmentCount = 0) } }

        composeRule.onNodeWithText(UNDO_COVERAGE_FRAGMENT_LABEL).assertIsNotEnabled()
        composeRule.onNodeWithText(SHOW_ALL_COVERAGE_LABEL).assertIsNotEnabled()
        composeRule.onNodeWithText(CLEAR_COVERAGE_LABEL).assertIsNotEnabled()
        // Adding is always possible: it is how the first fragment appears.
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsEnabled()
    }

    @Test
    fun theEditorOffersNoManualCoordinateCopy() {
        val selected = coverageBoundsSummary(
            MapGeoBounds(north = 56.4, east = 42.8, south = 56.1, west = 42.2),
        )
        composeRule.setContent {
            Bee_searchTheme {
                // «Копировать bbox» was retired together with the Area file: the app owns the file,
                // so neither one rectangle nor several may bring a copying action back.
                MapCoverageSelectionControls(
                    fragmentCount = 1,
                    viewportSummary = selected,
                    canAddFragment = true,
                    onAddFragment = {},
                    onUndo = {},
                    onShowAll = {},
                    onClear = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Копировать bbox").assertDoesNotExist()
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertIsDisplayed()
    }

    @Test
    fun clearingAllAsksForConfirmationBeforeTouchingTheSelection() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            Bee_searchTheme {
                ClearCoverageSelectionDialog(
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag(CLEAR_COVERAGE_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Очистить выбранные участки?").assertIsDisplayed()
        composeRule.onNodeWithText(CLEAR_COVERAGE_CONFIRM_LABEL).assertIsDisplayed()

        // Until "Очистить" is chosen, the selection is untouched.
        assertFalse(confirmed)
        assertFalse(dismissed)

        composeRule.onNodeWithText(CLEAR_COVERAGE_CONFIRM_LABEL).performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        assertFalse(dismissed)
    }

    @Test
    fun decliningTheClearConfirmationChangesNothing() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            Bee_searchTheme {
                ClearCoverageSelectionDialog(
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.runOnIdle { assertTrue(dismissed) }
        assertFalse(confirmed)
    }

    @Test
    fun unsavedChangesDialogOffersSaveDiscardAndStay() {
        var saved = false
        var discarded = false
        var stayed = false
        composeRule.setContent {
            Bee_searchTheme {
                CoverageUnsavedChangesDialog(
                    onSave = { saved = true },
                    onDiscard = { discarded = true },
                    onStay = { stayed = true },
                )
            }
        }

        composeRule.onNodeWithTag(UNSAVED_COVERAGE_CHANGES_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить изменения участка?").assertIsDisplayed()
        composeRule.onNodeWithText(SAVE_COVERAGE_CHANGES_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(DISCARD_COVERAGE_CHANGES_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(STAY_IN_COVERAGE_SELECTION_LABEL).assertIsDisplayed()

        // Every outcome is named; the dialog never asks an ambiguous yes/no question.
        composeRule.onNodeWithText("Да").assertDoesNotExist()
        composeRule.onNodeWithText("Нет").assertDoesNotExist()

        composeRule.onNodeWithText(SAVE_COVERAGE_CHANGES_LABEL).performClick()
        composeRule.runOnIdle { assertTrue(saved) }

        composeRule.onNodeWithText(DISCARD_COVERAGE_CHANGES_LABEL).performClick()
        composeRule.runOnIdle { assertTrue(discarded) }

        composeRule.onNodeWithText(STAY_IN_COVERAGE_SELECTION_LABEL).performClick()
        composeRule.runOnIdle { assertTrue(stayed) }
    }

    @Test
    fun offlinePackagePanelExplainsMissingMapAndOffersNextAction() {
        composeRule.setContent {
            Bee_searchTheme {
                OfflineMapPackagePanel(
                    desiredCoverageConfigured = false,
                    availability = MapPackageAvailability.Missing,
                    isLoading = false,
                    isImporting = false,
                    message = null,
                    onSelectCoverage = {},
                    onImport = {},
                )
            }
        }

        composeRule.onNodeWithTag(OFFLINE_MAP_PACKAGE_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Офлайн-карта не подготовлена").assertIsDisplayed()
        composeRule.onNodeWithText(CREATE_AREA_LABEL).assertIsDisplayed()
    }

    @Test
    fun importPanelExplainsManifestThenPmtilesOrder() {
        composeRule.setContent {
            Bee_searchTheme {
                OfflineMapPackagePanel(
                    desiredCoverageConfigured = true,
                    availability = MapPackageAvailability.Missing,
                    isLoading = false,
                    isImporting = false,
                    message = null,
                    onSelectCoverage = {},
                    onImport = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Импорт: сначала manifest, затем соответствующий PMTiles.",
        ).assertIsDisplayed()
    }

    @Composable
    private fun EditorControls(fragmentCount: Int = 1) {
        MapCoverageSelectionControls(
            fragmentCount = fragmentCount,
            viewportSummary = null,
            canAddFragment = true,
            onAddFragment = {},
            onUndo = {},
            onShowAll = {},
            onClear = {},
            onDone = {},
        )
    }
}
