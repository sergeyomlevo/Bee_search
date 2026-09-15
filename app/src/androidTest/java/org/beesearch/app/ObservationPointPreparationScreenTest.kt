package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBackUnconditionally
import org.beesearch.app.domain.model.BeeMarkCatalog
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
        var confirmRequests = 0
        var noBeesRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onConfirmPoint = { confirmRequests += 1 },
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onAbort = { abortRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("observation-point-preparation-draft").assertIsDisplayed()
        composeRule.onNodeWithTag("abort-observation-point-preparation").performClick()

        composeRule.runOnIdle {
            assertEquals(1, abortRequests)
            assertEquals(0, confirmRequests)
            assertEquals(0, noBeesRequests)
        }
        composeRule.onNodeWithText("Завершить наблюдение?").assertDoesNotExist()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertDoesNotExist()
    }

    @Test
    fun systemBackUsesTheSamePreparationAbortAction() {
        var abortRequests = 0
        var confirmRequests = 0
        var noBeesRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onConfirmPoint = { confirmRequests += 1 },
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onAbort = { abortRequests += 1 },
                )
            }
        }

        pressBackUnconditionally()

        composeRule.runOnIdle {
            assertEquals(1, abortRequests)
            assertEquals(0, confirmRequests)
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
                    onConfirmPoint = {},
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
    fun compactHeaderKeepsAbortAvailableAndAddConfirmsPointWithoutCreatingBee() {
        var confirmRequests = 0

        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onConfirmPoint = { confirmRequests += 1 },
                    onRecordNoBeesFound = {},
                    onAbort = {},
                )
            }
        }

        composeRule.onNodeWithText("Подготовка точки").assertIsDisplayed()
        composeRule.onNodeWithTag("abort-observation-point-preparation").assertIsDisplayed()
        composeRule.onNodeWithText("Точка пока не сохранена").assertIsDisplayed()
        composeRule.onNodeWithTag("add-observation-point").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, confirmRequests) }
    }

    @Test
    fun draftScreenDoesNotOfferMarkSelectionBeforeThePointExists() {
        composeRule.setContent {
            Bee_searchTheme {
                ObservationPointPreparationScreen(
                    draft = draft(),
                    onConfirmPoint = {},
                    onRecordNoBeesFound = {},
                    onAbort = {},
                )
            }
        }

        composeRule.onNodeWithTag("add-bee").assertDoesNotExist()
        BeeMarkCatalog.colors.forEach { color ->
            composeRule.onNodeWithTag("mark-color-${color.value}").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("record-no-bees-from-draft").assertIsDisplayed()
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
