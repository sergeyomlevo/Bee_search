package org.beesearch.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenAreaTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val territory = Territory(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        code = "DEV",
        name = "Тестовая территория",
        region = "Область",
        district = "Район",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun currentTerritoryOpensItsAreaEntryFromSettings() {
        var opened = false
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = emptyList(),
                    currentObserverId = null,
                    territories = listOf(territory),
                    currentTerritoryId = territory.id,
                    onBack = {},
                    onOpenArea = { opened = true },
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {},
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("settings-area").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun areaEntryIsDisabledWithoutCurrentTerritory() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag("settings-area").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun areaEntryFollowsTheCurrentTerritoryWhenItChanges() {
        val other = territory.copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            code = "FIELD",
            name = "Другая территория",
        )
        var selectedId by mutableStateOf(territory.id)
        var openedFor: UUID? = null
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = emptyList(),
                    currentObserverId = null,
                    territories = listOf(territory, other),
                    currentTerritoryId = selectedId,
                    onBack = {},
                    onOpenArea = { openedFor = selectedId },
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = { selectedId = it },
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Участки и офлайн-карта территории «Тестовая территория».").assertExists()
        composeRule.runOnIdle { selectedId = other.id }
        composeRule.onNodeWithText("Участки и офлайн-карта территории «Другая территория».").assertExists()
        composeRule.onNodeWithTag("settings-area").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(other.id, openedFor) }
    }

    @Test
    fun areaEntryAppearsAfterTerritorySection() {
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = emptyList(),
                    currentObserverId = null,
                    territories = listOf(territory),
                    currentTerritoryId = territory.id,
                    onBack = {},
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {},
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        val territoriesTop = composeRule.onNodeWithText("Территории").fetchSemanticsNode().positionInRoot.y
        val areaTop = composeRule.onNodeWithTag("settings-area").fetchSemanticsNode().positionInRoot.y
        assertTrue("Area entry must follow territory management", areaTop > territoriesTop)
        composeRule.onNodeWithText("Офлайн-карты").assertDoesNotExist()
    }

    @Test
    fun areaEntryRemainsReachableAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.7f)) {
                Bee_searchTheme {
                    SettingsScreen(
                        observers = emptyList(),
                        currentObserverId = null,
                        territories = listOf(territory),
                        currentTerritoryId = territory.id,
                        onBack = {},
                        onSelectObserver = {},
                        onCreateObserver = { _, _, _, _, _ -> },
                        onSelectTerritory = {},
                        onCreateTerritory = { _, _, _, _ -> },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-area").performScrollTo().assertIsDisplayed()
    }
}
