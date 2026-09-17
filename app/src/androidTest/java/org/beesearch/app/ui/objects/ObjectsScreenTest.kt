package org.beesearch.app.ui.objects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ObjectsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun objectsCatalogOpensObservationPointsAndBackReturnsToMap() {
        var openedPoints = false
        var backed = false
        composeRule.setContent {
            Bee_searchTheme {
                ObjectsScreen(
                    onBack = { backed = true },
                    onOpenObservationPoints = { openedPoints = true },
                )
            }
        }

        composeRule.onNodeWithText("Объекты").assertIsDisplayed()
        composeRule.onNodeWithTag("objects-observation-points")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Точки наблюдения").assertIsDisplayed()
        composeRule.onNodeWithText("Назад").performClick()
        composeRule.runOnIdle {
            assertEquals(true, openedPoints)
            assertEquals(true, backed)
        }
    }
}
