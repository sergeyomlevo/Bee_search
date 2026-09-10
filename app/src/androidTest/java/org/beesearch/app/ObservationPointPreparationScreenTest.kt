package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBackUnconditionally
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.ui.observation.ObservationPointPreparationScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ObservationPointPreparationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun explicitAbortDiscardsOnlyThePreparationDraftWithoutConfirmation() {
        var abortRequests = 0
        var firstBeeRequests = 0
        var noBeesRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onAddFirstBee = { _, _ -> firstBeeRequests += 1 },
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onAbort = { abortRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("observation-point-preparation-draft").assertIsDisplayed()
        composeRule.onNodeWithTag("abort-observation-point-preparation").performClick()

        composeRule.runOnIdle {
            assertEquals(1, abortRequests)
            assertEquals(0, firstBeeRequests)
            assertEquals(0, noBeesRequests)
        }
        composeRule.onNodeWithText("Завершить наблюдение?").assertDoesNotExist()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertDoesNotExist()
    }

    @Test
    fun systemBackUsesTheSamePreparationAbortAction() {
        var abortRequests = 0
        var firstBeeRequests = 0
        var noBeesRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onAddFirstBee = { _, _ -> firstBeeRequests += 1 },
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onAbort = { abortRequests += 1 },
                )
            }
        }

        pressBackUnconditionally()

        composeRule.runOnIdle {
            assertEquals(1, abortRequests)
            assertEquals(0, firstBeeRequests)
            assertEquals(0, noBeesRequests)
        }
    }

    @Test
    fun noBeesStillRequiresItsOwnExplicitResearchConfirmation() {
        var noBeesRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onAddFirstBee = { _, _ -> },
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onAbort = {},
                )
            }
        }

        composeRule.onNodeWithTag("record-no-bees-from-draft").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-no-bees-from-draft").performClick()

        composeRule.runOnIdle { assertEquals(1, noBeesRequests) }
    }

    @Test
    fun compactHeaderKeepsAbortAvailableAndFirstBeeUsesSelectedMark() {
        var added: Pair<String, MarkPosition>? = null

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onAddFirstBee = { color, position -> added = color to position },
                    onRecordNoBeesFound = {},
                    onAbort = {},
                )
            }
        }

        composeRule.onNodeWithText("Подготовка точки").assertIsDisplayed()
        composeRule.onNodeWithTag("abort-observation-point-preparation").assertIsDisplayed()
        composeRule.onNodeWithText("Точка пока не сохранена").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Цвет метки: Красная").performClick()
        composeRule.onNodeWithTag("mark-color-RED").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").performClick()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.runOnIdle { assertEquals("RED" to MarkPosition.LEFT_WING, added) }
    }

    private fun draft() = ObservationPointPreparationDraft(
        point = NewObservationPoint(
            territoryId = UUID.randomUUID(),
            observerId = UUID.randomUUID(),
            latitude = 56.1,
            longitude = 42.7,
        ),
    )
}
