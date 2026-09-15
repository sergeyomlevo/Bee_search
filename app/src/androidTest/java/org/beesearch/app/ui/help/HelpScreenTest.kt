package org.beesearch.app.ui.help

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.onNodeWithTag("help-screen")
            .performScrollToIndex(FIRST_SECTION_INDEX + PREPARATION_SECTION_INDEX)
        composeRule.onNodeWithTag("help-section-$PREPARATION_SECTION_INDEX").performClick()
        val firstFlightGuidance = "Если первый вылет отмечен ошибочно, отмените его в карточке пчелы: " +
            "пчела и её цикл будут удалены, а метка снова станет доступным вариантом. После " +
            "зарегистрированного возврата такая отмена недоступна."
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(firstFlightGuidance))
        composeRule.onNodeWithText(firstFlightGuidance).assertIsDisplayed()
        composeRule.onNodeWithText(
            "В первом цикле полёт длительностью менее одной минуты при анализе не учитывается.",
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            "Первый выпуск запускается одновременно для всей подготовленной группы.",
        ).assertDoesNotExist()
    }

    @Test
    fun helpSectionHeadersShowOnlyTheTopicAndKeepExpandSemantics() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(onBack = {}) } }

        detailedHelpSections.forEachIndexed { index, section ->
            composeRule.onNodeWithTag("help-screen").performScrollToIndex(FIRST_SECTION_INDEX + index)
            // The exact topic is the header's text value: a service prefix would make this fail.
            composeRule.onNodeWithText(section.title).assertExists()
            composeRule.onNodeWithText("Развернуть: ${section.title}").assertDoesNotExist()
            composeRule.onNodeWithText("Свернуть: ${section.title}").assertDoesNotExist()
            composeRule.onNodeWithTag("help-section-$index")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, COLLAPSED_STATE),
                )
        }

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(FIRST_SECTION_INDEX)
        composeRule.onNodeWithTag("help-section-0").performClick()
        composeRule.onNodeWithText(detailedHelpSections[0].title).assertExists()
        composeRule.onNodeWithTag("help-section-0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, EXPANDED_STATE))
    }

    @Test
    fun helpExposesDataAndMapSections() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(onBack = {}) } }

        val dataSectionIndex = detailedHelpSections.indexOfFirst { it.title == "Экспорт и очистка данных" }
        val mapSectionIndex = detailedHelpSections.indexOfFirst { it.title == "Карты" }

        composeRule.onNodeWithTag("help-screen")
            .performScrollToIndex(FIRST_SECTION_INDEX + dataSectionIndex)
        composeRule.onNodeWithText("Экспорт и очистка данных").assertIsDisplayed()
        composeRule.onNodeWithTag("help-screen")
            .performScrollToIndex(FIRST_SECTION_INDEX + mapSectionIndex)
        composeRule.onNodeWithText("Карты").assertIsDisplayed()
    }

    private companion object {
        /** LazyColumn index of `detailedHelpSections[0]`: two headings plus the three quick steps. */
        const val FIRST_SECTION_INDEX = 5
        const val PREPARATION_SECTION_INDEX = 3
        const val COLLAPSED_STATE = "Свёрнуто"
        const val EXPANDED_STATE = "Развёрнуто"
    }
}
