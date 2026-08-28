package org.beesearch.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Rule
import org.junit.Test

class FeedbackBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun informationalFeedbackAppearsAndAutoDismissesAfterTimeout() {
        val current = mutableStateOf<UiFeedback?>(autoFeedback(id = 1, message = "Пчела добавлена"))
        composeRule.mainClock.autoAdvance = false
        setFeedbackContent(current)

        composeRule.onNodeWithText("Пчела добавлена").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS - 1)
        composeRule.onNodeWithText("Пчела добавлена").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feedback-banner").assertDoesNotExist()
    }

    @Test
    fun persistentFeedbackDoesNotUseInformationalTimeout() {
        val current = mutableStateOf<UiFeedback?>(
            UiFeedback(
                id = 1,
                message = "Не удалось сохранить изменения",
                displayMode = FeedbackDisplayMode.PERSISTENT,
            ),
        )
        composeRule.mainClock.autoAdvance = false
        setFeedbackContent(current)

        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS * 2)
        composeRule.onNodeWithText("Не удалось сохранить изменения").assertIsDisplayed()
    }

    @Test
    fun replacementRestartsTimeoutAndOldEffectCannotDismissNewFeedback() {
        val current = mutableStateOf<UiFeedback?>(autoFeedback(id = 1, message = "Первое сообщение"))
        composeRule.mainClock.autoAdvance = false
        setFeedbackContent(current)

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.runOnIdle {
            current.value = autoFeedback(id = 2, message = "Новое сообщение")
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS - 1_000)

        composeRule.onNodeWithText("Первое сообщение").assertDoesNotExist()
        composeRule.onNodeWithText("Новое сообщение").assertIsDisplayed()
    }

    @Test
    fun manualDismissImmediatelyClearsFeedback() {
        val current = mutableStateOf<UiFeedback?>(autoFeedback(id = 1, message = "Сохранено"))
        composeRule.mainClock.autoAdvance = false
        setFeedbackContent(current)

        composeRule.onNodeWithTag("feedback-dismiss").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("feedback-banner").assertDoesNotExist()
    }

    private fun setFeedbackContent(current: androidx.compose.runtime.MutableState<UiFeedback?>) {
        composeRule.setContent {
            Bee_searchTheme {
                current.value?.let { feedback ->
                    FeedbackBanner(
                        feedback = feedback,
                        onDismiss = { id ->
                            if (current.value?.id == id) current.value = null
                        },
                    )
                }
            }
        }
    }

    private fun autoFeedback(id: Long, message: String) = UiFeedback(
        id = id,
        message = message,
        displayMode = FeedbackDisplayMode.AUTO_DISMISS,
    )
}
