package org.beesearch.app.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import java.io.File
import java.time.Instant
import java.util.UUID
import org.beesearch.app.InitialSetupState
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.ActiveMapPackage
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageManifest
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InitialSetupScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingDoesNotClaimThatSettingsAreMissing() {
        composeRule.setContent { Bee_searchTheme {
            InitialSetupScreen(InitialSetupState.Loading(), {}, {}, {}, {}, {})
        } }
        composeRule.onNodeWithText("Проверяем данные…").assertIsDisplayed()
        composeRule.onNodeWithTag("setup-observer").assertDoesNotExist()
    }

    @Test fun eachStepAndExitAreReachableAtLargeFontScale() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                Bee_searchTheme {
                    InitialSetupScreen(
                        InitialSetupState.Ready(null, null, null, MapAreaReadResult.Absent, null, false),
                        { clicked += "observer" }, { clicked += "territory" },
                        { clicked += "area" }, { clicked += "map" }, { clicked += "continue" },
                    )
                }
            }
        }
        listOf("setup-observer", "setup-territory", "setup-area", "setup-map", "setup-continue")
            .forEach { tag -> composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick() }
        composeRule.runOnIdle {
            assertEquals(listOf("observer", "territory", "area", "map", "continue"), clicked)
        }
        composeRule.onNodeWithText("Продолжить без настройки").assertIsDisplayed()
    }

    @Test fun completedSetupUsesDoneWithoutChangingTheExitAction() {
        var continued = false
        val territory = Territory(
            UUID.randomUUID(), "T", "Территория", "Регион", "Район", Instant.EPOCH, Instant.EPOCH,
        )
        val observer = Observer(
            UUID.randomUUID(), "O", "Иванов", "Иван", null, null, Instant.EPOCH, Instant.EPOCH,
        )
        val area = MapArea(
            UUID.randomUUID(), "Ареал", listOf(MapGeoBounds(57.0, 39.0, 56.0, 38.0)),
        )
        val readyMap = MapPackageAvailability.Ready(
            ActiveMapPackage(
                MapPackageManifest(
                    1, "test", "test", "test", "v1", "v1", emptyList(),
                    0, 1, "test.pmtiles", 1, "0".repeat(64),
                ),
                File("test.pmtiles"),
            ),
        )
        composeRule.setContent {
            Bee_searchTheme {
                InitialSetupScreen(
                    InitialSetupState.Ready(
                        null, observer, territory, MapAreaReadResult.Present(area), readyMap, false,
                    ),
                    {}, {}, {}, {}, { continued = true },
                )
            }
        }

        composeRule.onNodeWithText("Готово").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Продолжить без настройки").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(true, continued) }
    }
}
