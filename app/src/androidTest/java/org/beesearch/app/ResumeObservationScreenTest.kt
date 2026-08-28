package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ResumeObservationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completionRequiresExplicitConfirmation() {
        var completionRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(BeePresenceResult.BEES_FOUND),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = { completionRequests += 1 },
                    onOpenTerritories = {},
                )
            }
        }

        composeRule.onNodeWithText("Завершить").performClick()
        composeRule.onNodeWithText("Завершить наблюдение?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, completionRequests) }

        composeRule.onNodeWithTag("confirm-complete-observation").performClick()
        composeRule.runOnIdle { assertEquals(1, completionRequests) }
    }

    @Test
    fun preparedBeesAndReadinessAreVisibleWithoutStartingRelease() {
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("WHITE", MarkPosition.RIGHT_WING)),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                    onOpenTerritories = {},
                )
            }
        }

        composeRule.onNodeWithText("Белая КП").assertIsDisplayed()
        composeRule.onNodeWithText("Готово к выпуску: 1").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-preparation-list").performScrollToNode(hasTestTag("add-bee"))
        composeRule.onNodeWithText("Выпустить всех").assertIsNotEnabled()
    }

    @Test
    fun noBeesResultRequiresConfirmationAndCancelDoesNotInvokePersistenceAction() {
        var noBeesRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(pointId = pointId, isLoading = false),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onComplete = {},
                    onOpenTerritories = {},
                )
            }
        }

        composeRule.onNodeWithTag("bee-preparation-list")
            .performScrollToNode(hasTestTag("record-no-bees"))
        composeRule.onNodeWithTag("record-no-bees").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Точка наблюдения будет сохранена с результатом «пчёлы отсутствуют» и завершена.",
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        composeRule.onNodeWithTag("record-no-bees").performClick()
        composeRule.onNodeWithTag("confirm-no-bees").performClick()
        composeRule.runOnIdle { assertEquals(1, noBeesRequests) }
    }

    @Test
    fun noBeesActionIsUnavailableAfterBeeWasFound() {
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(BeePresenceResult.BEES_FOUND),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("BLUE", MarkPosition.NONE)),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                    onOpenTerritories = {},
                )
            }
        }

        composeRule.onNodeWithText("Пчёлы отсутствуют").assertDoesNotExist()
    }

    @Test
    fun directPositionSelectionAddsChosenCombination() {
        var added: Pair<String, MarkPosition>? = null
        composeRule.setContent {
            Bee_searchTheme {
                BeeSelector(
                    bees = emptyList(),
                    enabled = true,
                    onAddBee = { color, position -> added = color to position },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").performClick()
        composeRule.onNodeWithTag("add-bee").performClick()
        composeRule.runOnIdle { assertEquals("WHITE" to MarkPosition.RIGHT_WING, added) }
    }

    @Test
    fun unavailableSelectionIsClearedInsteadOfBeingReplaced() {
        composeRule.setContent {
            val bees = remember { mutableStateOf(emptyList<Bee>()) }
            Bee_searchTheme {
                BeeSelector(
                    bees = bees.value,
                    enabled = true,
                    onAddBee = { color, position -> bees.value = listOf(bee(color, position)) },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-GREEN").performClick()
        composeRule.onNodeWithTag("mark-position-NONE").performClick()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.onNodeWithTag("add-bee").assertIsNotEnabled()
    }

    private val pointId = UUID.fromString("00000000-0000-0000-0000-000000000111")

    private fun bee(markColor: String, markPosition: MarkPosition) = Bee(
        id = UUID.randomUUID(),
        observationPointId = pointId,
        markColor = markColor,
        markPosition = markPosition,
        createdAt = Instant.parse("2026-08-27T08:32:00Z"),
    )

    private fun point(beePresenceResult: BeePresenceResult? = null) = ObservationPoint(
        id = pointId,
        territoryId = UUID.randomUUID(),
        observerCode = "GSE",
        observationYear = 2026,
        pointNumber = 1,
        beePresenceResult = beePresenceResult,
        code = null,
        latitude = 56.1959786,
        longitude = 42.7477116,
        gpsLatitude = 56.1959000,
        gpsLongitude = 42.7477000,
        gpsAccuracyM = 3.8,
        createdAt = Instant.parse("2026-08-27T08:31:00Z"),
        completedAt = null,
    )
}
