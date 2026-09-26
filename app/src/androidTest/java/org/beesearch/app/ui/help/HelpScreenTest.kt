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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class HelpScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val exchangeStorage = BeeSearchExchangeStorage(File("Download"), "Beta")

    private fun sections() = helpSections(exchangeStorage.userVisiblePath())

    private fun sectionIndex(title: String) = sections().indexOfFirst { it.title == title }

    /** Items of the help list before the first section: the intro title, its blocks and a header. */
    private fun headerItems() = 2 + helpIntro.blocks.size

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
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText("Подробная помощь"))
        composeRule.onNodeWithText("Подробная помощь").assertIsDisplayed()
        composeRule.onNodeWithTag("help-screen")
            .performScrollToNode(hasText("Создать запись здесь", substring = true))
        composeRule.onNodeWithText("Создать запись здесь", substring = true).assertIsDisplayed()
    }

    @Test
    fun everySectionIsReachableAndKeepsItsTopicAsItsName() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        sections().forEachIndexed { index, section ->
            composeRule.onNodeWithTag("help-screen").performScrollToIndex(headerItems() + index)
            composeRule.onNodeWithText(section.title).assertExists()
            composeRule.onNodeWithText("Развернуть: ${section.title}").assertDoesNotExist()
            composeRule.onNodeWithText("Свернуть: ${section.title}").assertDoesNotExist()
            composeRule.onNodeWithTag("help-section-$index")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun expandingASectionShowsItsStepsAndNotes() {
        val index = sectionIndex("Дупла и колоды")
        assertTrue("the object workflow section must exist", index > 0)
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(headerItems() + index)
        composeRule.onNodeWithTag("help-section-$index").performClick()

        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText("Зафиксировать с компаса", substring = true))
        composeRule.onNodeWithText("Зафиксировать с компаса", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("help-section-$index")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, EXPANDED_STATE))
    }

    @Test
    fun objectsNumberingAndResetAreExplainedInTheWorkflowOrder() {
        val titles = sections().filter { it.level == 1 }.map { it.title }

        listOf(
            "Что находится в разделе «Объекты»",
            "Дупла и колоды",
            "Удаление объекта и правило номера",
            "Сброс нумерации",
        ).forEach { title ->
            assertTrue("help must contain the section «$title»", titles.contains(title))
        }
        assertTrue(
            "objects must be explained before deletion",
            titles.indexOf("Дупла и колоды") < titles.indexOf("Удаление объекта и правило номера"),
        )
    }

    @Test
    fun aDeclaredImageSlotWithoutAResourceLeavesNoGap() {
        val index = sectionIndex("Главный экран: карта")
        assertTrue("the main screen section must exist", index > 0)
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(headerItems() + index)
        composeRule.onNodeWithTag("help-section-$index").performClick()

        // The canonical source declares an image slot here, but no drawable is shipped yet: the help
        // shows the text and must not compose an image node for the missing resource.
        val visualBlock = sections()[index].blocks.indexOfFirst { it is HelpBlock.Visual }
        assertTrue("the canonical source must declare an image slot", visualBlock >= 0)
        composeRule.onNodeWithTag("help-screen")
            .performScrollToNode(hasText("не подписаны текстом", substring = true))
        composeRule.onNodeWithText("не подписаны текстом", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("help-section-$index-block-$visualBlock").assertDoesNotExist()
    }

    @Test
    fun exchangeSectionNamesTheFolderOfThisVariant() {
        val index = sectionIndex("Где находятся файлы Bee Search")
        assertTrue("the exchange section must exist", index > 0)
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        composeRule.onNodeWithTag("help-screen").performScrollToIndex(headerItems() + index)
        composeRule.onNodeWithTag("help-section-$index").performClick()
        composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(EXCHANGE_PATH_FRAGMENT))
        composeRule.onNodeWithText(EXCHANGE_PATH_FRAGMENT).assertIsDisplayed()
    }

    @Test
    fun sectionsReflectTheCanonicalSourceOrder() {
        composeRule.setContent { Bee_searchTheme { HelpScreen(exchangeStorage = exchangeStorage, onBack = {}) } }

        val titles = sections().map { it.title }
        listOf(titles.first(), titles[TITLES_MIDDLE_INDEX], titles.last()).forEach { title ->
            composeRule.onNodeWithTag("help-screen").performScrollToNode(hasText(title))
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
        assertEquals("Удаление объекта и правило номера", titles[TITLES_MIDDLE_INDEX])
    }

    private companion object {
        const val EXPANDED_STATE = "Развёрнуто"
        const val EXCHANGE_PATH_FRAGMENT = "Download/BeeSearch/Beta/Exchange"
        const val TITLES_MIDDLE_INDEX = 12
    }
}
