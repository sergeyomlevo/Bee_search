package org.beesearch.app.ui.help

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import org.beesearch.app.ui.settings.SettingsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HelpScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsOpensHelpAndHelpContainsCurrentGuidance() {
        val helpOpened = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                if (helpOpened.value) {
                    HelpScreen(onBack = {})
                } else {
                    SettingsScreen(
                        observers = emptyList(),
                        currentObserverId = null,
                        territories = emptyList(),
                        currentTerritoryId = null,
                        onBack = {},
                        onOpenHelp = { helpOpened.value = true },
                        onSelectObserver = {},
                        onCreateObserver = { _, _, _, _, _ -> },
                        onSelectTerritory = {},
                        onCreateTerritory = { _, _, _, _ -> },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-help").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(helpOpened.value) }

        composeRule.onNodeWithText("Краткий старт").assertIsDisplayed()
        composeRule.onNodeWithText("Подробная помощь").assertIsDisplayed()
        composeRule.onNodeWithTag("help-screen").performScrollToIndex(8)
        composeRule.onNodeWithText("Развернуть: Подготовка пчёл и первый выпуск")
            .performClick()
        composeRule.onNodeWithText(
            "Если пчела не улетела, отмените для неё вылет и дождитесь, когда она действительно улетит.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "В первом цикле полёт длительностью менее одной минуты при анализе не учитывается.",
        ).assertDoesNotExist()
    }

    @Test
    fun helpExposesDataAndMapSections() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(onBack = {}) } }

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(11)
        composeRule.onNodeWithText("Развернуть: Экспорт и очистка данных").assertIsDisplayed()
        composeRule.onNodeWithTag("help-screen").performScrollToIndex(12)
        composeRule.onNodeWithText("Развернуть: Карты").assertIsDisplayed()
    }
}
