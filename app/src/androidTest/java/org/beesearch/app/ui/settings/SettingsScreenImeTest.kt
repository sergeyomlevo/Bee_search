package org.beesearch.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.beesearch.app.SetupSettingsSection
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenImeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialSetupCanBeOpenedManuallyFromSettings() {
        var opened = false
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = {}, onOpenInitialSetup = { opened = true },
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }
        composeRule.onNodeWithTag("settings-initial-setup").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun systemBackUsesContextualSettingsReturn() {
        var returned = false
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = { returned = true },
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }
        Espresso.pressBack()
        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun checklistTerritoryEntryBringsExistingSectionIntoView() {
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = {}, initialSetupSection = SetupSettingsSection.TERRITORY,
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }
        composeRule.onNodeWithText("Территории").assertIsDisplayed()
    }

    @Test
    fun territoryFormKeepsLastFieldReachableAtLargeFontScale() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    SettingsScreen(
                        observers = emptyList(),
                        currentObserverId = null,
                        territories = emptyList(),
                        currentTerritoryId = null,
                        onBack = {},
                        onSelectObserver = {},
                        onCreateObserver = { _, _, _, _, _ -> },
                        onSelectTerritory = {},
                        onCreateTerritory = { _, _, _, _ -> },
                    )
                }
            }
        }

        composeRule.onNodeWithText("+ Добавить территорию")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("territory-code-field")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("territory-district-field")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
            .assertIsDisplayed()
    }

    @Test
    fun checklistObserverEntryOpensTheObserverFlowDirectly() {
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = {},
                initialSetupSection = SetupSettingsSection.OBSERVER,
                initialSetupCreatesMissingValue = true,
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }

        composeRule.onNodeWithTag("observer-code-field").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checklistTerritoryEntryOpensTheTerritoryFormDirectly() {
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = {},
                initialSetupSection = SetupSettingsSection.TERRITORY,
                initialSetupCreatesMissingValue = true,
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }

        composeRule.onNodeWithTag("territory-code-field").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aReadyChecklistStepOnlyShowsItsSection() {
        composeRule.setContent { Bee_searchTheme {
            SettingsScreen(
                observers = emptyList(), currentObserverId = null,
                territories = emptyList(), currentTerritoryId = null,
                onBack = {},
                initialSetupSection = SetupSettingsSection.OBSERVER,
                initialSetupCreatesMissingValue = false,
                onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
            )
        } }

        composeRule.onNodeWithTag("observer-code-field").assertDoesNotExist()
        composeRule.onNodeWithText("+ Добавить наблюдателя").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checklistObserverEntryBringsTheOpenedFormIntoView() {
        composeRule.setContent { Bee_searchTheme {
            // A short viewport: the opened form starts below the fold, so only the screen's own scroll
            // request can make it visible.
            Box(Modifier.height(320.dp)) {
                SettingsScreen(
                    observers = emptyList(), currentObserverId = null,
                    territories = emptyList(), currentTerritoryId = null,
                    onBack = {},
                    initialSetupSection = SetupSettingsSection.OBSERVER,
                    initialSetupCreatesMissingValue = true,
                    onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
                )
            }
        } }

        // No performScrollTo on purpose: the checklist step must show the form by itself.
        composeRule.onNodeWithTag("observer-code-field").assertIsDisplayed()
    }

    @Test
    fun checklistTerritoryEntryBringsTheOpenedFormIntoView() {
        composeRule.setContent { Bee_searchTheme {
            Box(Modifier.height(320.dp)) {
                SettingsScreen(
                    observers = emptyList(), currentObserverId = null,
                    territories = emptyList(), currentTerritoryId = null,
                    onBack = {},
                    initialSetupSection = SetupSettingsSection.TERRITORY,
                    initialSetupCreatesMissingValue = true,
                    onSelectObserver = {}, onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {}, onCreateTerritory = { _, _, _, _ -> },
                )
            }
        } }

        composeRule.onNodeWithTag("territory-code-field").assertIsDisplayed()
    }
}
