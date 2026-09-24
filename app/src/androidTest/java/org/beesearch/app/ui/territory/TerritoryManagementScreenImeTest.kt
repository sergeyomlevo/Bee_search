package org.beesearch.app.ui.territory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The territory form is the screen the field user reaches from the map's territory blocker and from
 * the Initial Setup checklist. It is one scrollable surface with IME padding, so the last field and the
 * create button stay reachable while the keyboard is open and at an enlarged font scale.
 *
 * A Compose test cannot raise the real system keyboard, so these tests prove the scroll behaviour that
 * makes the IME-reachable layout work; the real keyboard check stays a device check.
 */
@RunWith(AndroidJUnit4::class)
class TerritoryManagementScreenImeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lastFieldAndCreateButtonAreReachableAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                Bee_searchTheme {
                    TerritoryManagementScreen(
                        territories = emptyList(),
                        currentTerritoryId = null,
                        onBack = {},
                        onOpenSettings = {},
                        onSelectTerritory = {},
                        onCreateTerritory = { _, _, _, _ -> },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("territory-form-code-field")
            .performScrollTo()
            .performClick()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("territory-form-district-field")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Создать").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savedTerritoriesStayReachableInTheSameScrollableSurface() {
        val territory = Territory(
            UUID.randomUUID(), "A01", "Лухское полесье", "Ивановская область", "Лухский район",
            Instant.EPOCH, Instant.EPOCH,
        )
        composeRule.setContent { Bee_searchTheme {
            TerritoryManagementScreen(
                territories = listOf(territory),
                currentTerritoryId = territory.id,
                onBack = {},
                onOpenSettings = {},
                onSelectTerritory = {},
                onCreateTerritory = { _, _, _, _ -> },
            )
        } }

        composeRule.onNodeWithText("Лухское полесье").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Текущая территория").performScrollTo().assertIsDisplayed()
    }
}
