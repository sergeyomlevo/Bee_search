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
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.ui.settings.SettingsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class HelpScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val exchangeStorage = BeeSearchExchangeStorage(File("Download"), "Beta")

    private fun sections() = helpSections(exchangeStorage)

    @Test
    fun settingsOpensHelpAndHelpContainsCurrentGuidance() {
        val helpOpened = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                if (helpOpened.value) {
                    HelpScreen(exchangeStorage = exchangeStorage, onBack = {})
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
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        sections().forEachIndexed { index, section ->
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
        composeRule.onNodeWithText(sections()[0].title).assertExists()
        composeRule.onNodeWithTag("help-section-0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, EXPANDED_STATE))
    }

    @Test
    fun helpExposesDataAndMapSections() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        val dataSectionIndex = sections().indexOfFirst { it.title == "Экспорт и очистка данных" }
        val mapSectionIndex = sections().indexOfFirst { it.title == "Карты" }

        composeRule.onNodeWithTag("help-screen")
            .performScrollToIndex(FIRST_SECTION_INDEX + dataSectionIndex)
        composeRule.onNodeWithText("Экспорт и очистка данных").assertIsDisplayed()
        composeRule.onNodeWithTag("help-screen")
            .performScrollToIndex(FIRST_SECTION_INDEX + mapSectionIndex)
        composeRule.onNodeWithText("Карты").assertIsDisplayed()
    }

    @Test
    fun helpExposesCoverageCreationAndMapLoadingSections() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        val coverageIndex = sections().indexOfFirst { it.title == "Создание участка офлайн-карты" }
        val loadingIndex = sections().indexOfFirst { it.title == "Загрузка офлайн-карты" }
        assertTrue("both offline-map sections must exist", coverageIndex > 0 && loadingIndex > coverageIndex)

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(FIRST_SECTION_INDEX + coverageIndex)
        composeRule.onNodeWithText("Создание участка офлайн-карты").assertIsDisplayed()
        composeRule.onNodeWithTag("help-section-$coverageIndex").performClick()
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(DONE_SAVES_FRAGMENT, substring = true))
        composeRule.onNodeWithText(DONE_SAVES_FRAGMENT, substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(FIRST_SECTION_INDEX + loadingIndex)
        composeRule.onNodeWithText("Загрузка офлайн-карты").assertIsDisplayed()
        composeRule.onNodeWithTag("help-section-$loadingIndex").performClick()
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(FILE_PAIR_FRAGMENT, substring = true))
        composeRule.onNodeWithText(FILE_PAIR_FRAGMENT, substring = true).assertIsDisplayed()
    }

    @Test
    fun helpExposesTheExchangeFolderOfThisVariant() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        val exchangeIndex = sections().indexOfFirst { it.title == EXCHANGE_HELP_TITLE }
        assertTrue("the exchange section must exist", exchangeIndex > 0)

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(FIRST_SECTION_INDEX + exchangeIndex)
        composeRule.onNodeWithText(EXCHANGE_HELP_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag("help-section-$exchangeIndex").performClick()
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(EXCHANGE_PATH_FRAGMENT))
        composeRule.onNodeWithText(EXCHANGE_PATH_FRAGMENT).assertIsDisplayed()
    }

    private companion object {
        /** LazyColumn index of `detailedHelpSections[0]`: two headings plus the three quick steps. */
        const val FIRST_SECTION_INDEX = 5
        const val PREPARATION_SECTION_INDEX = 3
        const val COLLAPSED_STATE = "Свёрнуто"
        const val EXPANDED_STATE = "Развёрнуто"
        const val DONE_SAVES_FRAGMENT = "сохраняет выбранные участки и завершает редактирование"
        const val FILE_PAIR_FRAGMENT = "*.pmtiles.manifest.json"
        const val EXCHANGE_PATH_FRAGMENT = "Download/BeeSearch/Beta/Exchange"
    }
}
