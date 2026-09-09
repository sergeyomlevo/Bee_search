package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.ui.map.COPY_SELECTED_COVERAGE_DESCRIPTION
import org.beesearch.app.ui.map.CURRENT_COVERAGE_SUMMARY_TAG
import org.beesearch.app.ui.map.MapCoverageSelectionControls
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.OfflineMapPackagePanel
import org.beesearch.app.ui.map.OFFLINE_MAP_PACKAGE_PANEL_TAG
import org.beesearch.app.ui.map.coverageBoundsSummary
import org.beesearch.app.ui.theme.Bee_searchTheme
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
        composeRule.onNodeWithText("Выбрать участок").assertIsDisplayed()
    }

    @Test
    fun devExportIsAvailableForOneSelectedRectangle() {
        var copied = false
        val selected = coverageBoundsSummary(
            MapGeoBounds(north = 56.4, east = 42.8, south = 56.1, west = 42.2),
        )
        composeRule.setContent {
            Bee_searchTheme {
                MapCoverageSelectionControls(
                    fragmentCount = 1,
                    viewportSummary = selected,
                    selectedSummary = selected,
                    showDevBoundsExport = true,
                    canAddFragment = true,
                    onAddFragment = {},
                    onUndo = {},
                    onShowAll = {},
                    onClear = {},
                    onDone = {},
                    onCopySelectedBounds = { copied = true },
                )
            }
        }

        composeRule.onNodeWithText("Копировать bbox").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(COPY_SELECTED_COVERAGE_DESCRIPTION).performClick()
        assertTrue(copied)
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
}
