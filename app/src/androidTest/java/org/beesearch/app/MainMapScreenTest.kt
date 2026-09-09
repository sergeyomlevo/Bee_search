package org.beesearch.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.CANCEL_OBSERVATION_POINT_DESCRIPTION
import org.beesearch.app.ui.map.CONFIRM_OBSERVATION_POINT_DESCRIPTION
import org.beesearch.app.ui.map.CREATE_OBSERVATION_POINT_DESCRIPTION
import org.beesearch.app.ui.map.CompactMapStatus
import org.beesearch.app.ui.map.MAIN_BOTTOM_PANEL_TAG
import org.beesearch.app.ui.map.MAIN_MAP_VIEWPORT_TAG
import org.beesearch.app.ui.map.MapCreationControls
import org.beesearch.app.ui.map.MapFirstScaffold
import org.beesearch.app.ui.map.MapIdleControls
import org.beesearch.app.ui.map.RECENTER_MAP_DESCRIPTION
import org.beesearch.app.ui.map.SETTINGS_DESCRIPTION
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainMapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mapFirstChromeKeepsMapDominantAndExposesCompactActions() {
        val settingsOpened = mutableStateOf(false)
        val recentered = mutableStateOf(false)
        val creationStarted = mutableStateOf(false)

        composeRule.setContent {
            Bee_searchTheme {
                MapFirstScaffold(
                    onOpenSettings = { settingsOpened.value = true },
                ) { mapModifier ->
                    Box(mapModifier.testTag(MAIN_MAP_VIEWPORT_TAG)) {
                        CompactMapStatus(
                            accuracyMeters = 3.8,
                            measurement = null,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                        MapCenterTarget(Modifier.align(Alignment.Center))
                        MapIdleControls(
                            canRecenter = true,
                            canCreateObservationPoint = true,
                            onRecenter = { recentered.value = true },
                            onCreateObservationPoint = { creationStarted.value = true },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }

        val mapBounds = composeRule.onNodeWithTag(MAIN_MAP_VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val panelBounds = composeRule
            .onNodeWithTag(MAIN_BOTTOM_PANEL_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val minimumTouchTargetPx = with(composeRule.density) { 48f * density }
        val maximumPanelHeightPx = with(composeRule.density) { 64f * density }
        assertTrue("Bottom panel must preserve a 48 dp touch row", panelBounds.height >= minimumTouchTargetPx)
        assertTrue("Bottom panel must remain compact", panelBounds.height <= maximumPanelHeightPx)
        assertTrue("Map should dominate the vertical workspace", mapBounds.height > panelBounds.height * 8f)

        composeRule.onNodeWithText("Bee Search").assertDoesNotExist()
        composeRule.onNodeWithText("Карта").assertDoesNotExist()
        composeRule.onNodeWithText("Настройки").assertDoesNotExist()
        composeRule.onNodeWithText("Новая точка").assertDoesNotExist()
        composeRule.onNodeWithText("К GPS").assertDoesNotExist()
        composeRule.onNodeWithText("Подтвердить точку").assertDoesNotExist()
        composeRule.onNodeWithText("Смещение от GPS", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Управление территориями").assertDoesNotExist()
        composeRule.onNodeWithText("KLYAZMA — Клязьминско-Лухский заказник").assertDoesNotExist()
        composeRule.onNodeWithTag("gps-accuracy-overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Точность", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("map-measurement-overlay").assertDoesNotExist()
        composeRule.onNodeWithText("0 м", substring = true).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(MAP_CENTER_TARGET_DESCRIPTION)
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Прицел центра карты и положения точки наблюдения")
            .assertDoesNotExist()

        val recenterNode = composeRule
            .onNodeWithContentDescription(RECENTER_MAP_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        val createNode = composeRule
            .onNodeWithContentDescription(CREATE_OBSERVATION_POINT_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        val settingsNode = composeRule
            .onNodeWithContentDescription(SETTINGS_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        assertTrue(recenterNode.fetchSemanticsNode().boundsInRoot.width >= minimumTouchTargetPx)
        assertTrue(recenterNode.fetchSemanticsNode().boundsInRoot.height >= minimumTouchTargetPx)
        assertTrue(createNode.fetchSemanticsNode().boundsInRoot.width >= minimumTouchTargetPx)
        assertTrue(createNode.fetchSemanticsNode().boundsInRoot.height >= minimumTouchTargetPx)
        assertTrue(settingsNode.fetchSemanticsNode().boundsInRoot.width >= minimumTouchTargetPx)
        assertTrue(settingsNode.fetchSemanticsNode().boundsInRoot.height >= minimumTouchTargetPx)
        assertTrue(
            "Settings belongs at the right edge of the panel",
            settingsNode.fetchSemanticsNode().boundsInRoot.right > panelBounds.right - maximumPanelHeightPx,
        )

        recenterNode.performClick()
        createNode.performClick()
        settingsNode.performClick()
        composeRule.runOnIdle {
            assertTrue(recentered.value)
            assertTrue(creationStarted.value)
            assertTrue(settingsOpened.value)
        }
    }

    @Test
    fun displacedMapCenterShowsMeasurementAndRecenterReturnsToCenteredState() {
        val measurement = mutableStateOf<MapMeasurement?>(
            MapMeasurement(
                distanceMeters = 127.0,
                bearingDegrees = 64.0,
                directionAbbreviation = "СВ",
            ),
        )
        composeRule.setContent {
            Bee_searchTheme {
                MapFirstScaffold(
                    onOpenSettings = {},
                ) { mapModifier ->
                    Box(mapModifier.fillMaxSize().testTag(MAIN_MAP_VIEWPORT_TAG)) {
                        CompactMapStatus(
                            accuracyMeters = 3.8,
                            measurement = measurement.value,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                        MapCenterTarget(Modifier.align(Alignment.Center))
                        MapIdleControls(
                            canRecenter = true,
                            canCreateObservationPoint = true,
                            onRecenter = { measurement.value = null },
                            onCreateObservationPoint = {},
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(MAIN_MAP_VIEWPORT_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(MAP_CENTER_TARGET_DESCRIPTION)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("map-measurement-overlay").assertIsDisplayed()
        composeRule.onNodeWithText("127 м · 64° СВ").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SETTINGS_DESCRIPTION).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(RECENTER_MAP_DESCRIPTION).performClick()
        composeRule.onNodeWithTag("map-measurement-overlay").assertDoesNotExist()
        composeRule.onNodeWithText("0 м", substring = true).assertDoesNotExist()
    }

    @Test
    fun creationControlsExposeConfirmCancelAndRecenterActions() {
        val recentered = mutableStateOf(false)
        val confirmed = mutableStateOf(false)
        val cancelled = mutableStateOf(false)

        composeRule.setContent {
            Bee_searchTheme {
                Box(Modifier.fillMaxSize()) {
                    MapCreationControls(
                        canRecenter = true,
                        canConfirm = true,
                        isSaving = false,
                        onRecenter = { recentered.value = true },
                        onConfirm = { confirmed.value = true },
                        onCancel = { cancelled.value = true },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(RECENTER_MAP_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(CONFIRM_OBSERVATION_POINT_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(CANCEL_OBSERVATION_POINT_DESCRIPTION)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(recentered.value)
            assertTrue(confirmed.value)
            assertTrue(cancelled.value)
        }
    }

    @Test
    fun settingsShowsSavedEntitiesAndSelectsAnotherTerritory() {
        val selectedTerritory = mutableStateOf<UUID?>(null)
        val otherTerritory = territory.copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000112"),
            code = "LUKH",
            name = "Участок у Луха",
        )
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = listOf(observer),
                    currentObserverId = observer.id,
                    territories = listOf(territory, otherTerritory),
                    currentTerritoryId = territory.id,
                    onBack = {},
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = { selectedTerritory.value = it },
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText("Наблюдатель")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Сделать текущей")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(selectedTerritory.value == otherTerritory.id) }
    }

    private companion object {
        val territory = Territory(
            id = UUID.fromString("00000000-0000-0000-0000-000000000111"),
            code = "KLYAZMA",
            name = "Клязьминско-Лухский заказник",
            region = "Владимирская область",
            district = "Гороховецкий район",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val observer = Observer(
            id = UUID.fromString("00000000-0000-0000-0000-000000000113"),
            code = "GSE",
            lastName = "Сергеев",
            firstName = "Георгий",
            middleName = null,
            contact = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
