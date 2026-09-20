package org.beesearch.app.ui.area

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.ui.map.AREA_VIEW_CONTROLS_TAG
import org.beesearch.app.ui.map.ADD_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.map.AreaViewControls
import org.beesearch.app.ui.map.CLEAR_COVERAGE_LABEL
import org.beesearch.app.ui.map.DONE_COVERAGE_SELECTION_LABEL
import org.beesearch.app.ui.map.EDIT_AREA_SECTIONS_LABEL
import org.beesearch.app.ui.map.MapCoverageSelectionControls
import org.beesearch.app.ui.map.UNDO_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Ареал view mode offers one action and no editor.
 *
 * A user who only wants to look at the Ареал must not be able to change it by tapping the map, so the
 * editor panel is absent here and switching to editing is a deliberate step.
 */
class AreaViewControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theViewOffersOnlyTheStepIntoEditing() {
        var edited = false
        composeRule.setContent {
            Bee_searchTheme { AreaViewControls(onEditSections = { edited = true }) }
        }

        composeRule.onNodeWithTag(AREA_VIEW_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(EDIT_AREA_SECTIONS_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(AREA_VIEW_CONTROLS_TAG).performClick()

        composeRule.runOnIdle { assertTrue(edited) }
    }

    @Test
    fun theViewShowsNoneOfTheEditorActions() {
        composeRule.setContent {
            Bee_searchTheme { AreaViewControls(onEditSections = {}) }
        }

        // The editor panel is a different screen: none of its actions may appear in the view mode.
        listOf(
            ADD_COVERAGE_FRAGMENT_LABEL,
            UNDO_COVERAGE_FRAGMENT_LABEL,
            CLEAR_COVERAGE_LABEL,
            DONE_COVERAGE_SELECTION_LABEL,
            "Текущий участок",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }
        composeRule.onNodeWithTag("map-coverage-selection-controls").assertDoesNotExist()
    }

    @Test
    fun theEditorPanelStillCarriesItsOwnActions() {
        // Guard for the test above: the labels really are the editor's, so their absence in the view
        // mode means something.
        composeRule.setContent {
            Bee_searchTheme {
                MapCoverageSelectionControls(
                    fragmentCount = 1,
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

        composeRule.onNodeWithTag("map-coverage-selection-controls").assertIsDisplayed()
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertIsDisplayed()
    }
}
