package org.beesearch.app.ui.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapBottomToolbarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun toolbarHasFourEqualOrderedButtonZonesAtNormalFontScale() {
        assertToolbarLayout(fontScale = 1f)
    }

    @Test
    fun toolbarHasFourEqualOrderedButtonZonesAtLargeFontScale() {
        assertToolbarLayout(fontScale = 1.7f)
    }

    @Test
    fun reservedAnalysisIsDisabledAndDoesNotNavigate() {
        var objectsOpened = false
        var settingsOpened = false
        var mapDataOpened = false
        setToolbar(
            onOpenObjects = { objectsOpened = true },
            onOpenSettings = { settingsOpened = true },
            onOpenMapData = { mapDataOpened = true },
        )

        rule.onNodeWithTag("open-analysis")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Пока недоступно",
                ),
            )
        rule.onNodeWithTag("open-analysis").performTouchInput { click() }

        assertEquals(false, objectsOpened)
        assertEquals(false, settingsOpened)
        assertEquals(false, mapDataOpened)
    }

    @Test
    fun eachWorkingZoneInvokesItsExistingCallback() {
        val calls = mutableListOf<String>()
        setToolbar(
            onOpenObjects = { calls += "objects" },
            onOpenSettings = { calls += "settings" },
            onOpenMapData = { calls += "map-data" },
        )

        rule.onNodeWithTag("open-map-data").assertIsEnabled().performClick()
        rule.onNodeWithTag("open-objects").assertIsEnabled().performClick()
        rule.onNodeWithTag("open-settings").assertIsEnabled().performClick()

        assertEquals(listOf("map-data", "objects", "settings"), calls)
    }

    @Test
    fun mapDataOpenStateFollowsOpenThenClose() {
        val open = mutableStateOf(false)
        rule.setContent {
            Bee_searchTheme {
                ToolbarTestHost {
                MapBottomPanel(
                    onOpenObjects = {},
                    onOpenSettings = {},
                    onOpenMapData = { open.value = true },
                    mapDataOpen = open.value,
                )
                }
            }
        }
        rule.onNodeWithTag("open-map-data").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false),
        )
        saveVisualEvidence("toolbar-normal.png")
        rule.onNodeWithTag("open-map-data").performClick()
        rule.onNodeWithTag("open-map-data").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
        saveVisualEvidence("toolbar-active.png")
        open.value = false
        rule.waitForIdle()
        rule.onNodeWithTag("open-map-data").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false),
        )
    }

    @Test
    fun restrictionDescriptionAndIndicatorAreIndependentOfOpenState() {
        val open = mutableStateOf(false)
        rule.setContent {
            Bee_searchTheme {
                ToolbarTestHost {
                MapBottomPanel(
                    onOpenObjects = {},
                    onOpenSettings = {},
                    onOpenMapData = {},
                    mapDataOpen = open.value,
                    mapDataRestricted = true,
                )
                }
            }
        }
        rule.onNodeWithTag(MAP_DATA_INDICATOR_TAG).assertIsDisplayed()
        rule.runOnIdle { open.value = true }
        rule.onNodeWithContentDescription(
            "$MAP_DATA_DESCRIPTION. Есть активные ограничения отображения.",
        ).assertIsDisplayed()
        rule.onNodeWithTag(MAP_DATA_INDICATOR_TAG).assertIsDisplayed()
    }

    @Test
    fun toolbarHasNoVisibleTextLabels() {
        setToolbar()
        listOf("Анализ", MAP_DATA_DESCRIPTION, OBJECTS_DESCRIPTION, SETTINGS_DESCRIPTION).forEach {
            rule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    private fun assertToolbarLayout(fontScale: Float) {
        setToolbar(fontScale = fontScale)
        val tags = listOf("open-analysis", "open-map-data", "open-objects", "open-settings")
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(4)

        val nodes = tags.map { rule.onNodeWithTag(it).assertIsDisplayed() }
        val bounds = nodes.map { it.fetchSemanticsNode().boundsInRoot }
        val expectedHeight = with(rule.density) { 56.dp.toPx() }
        val minimumTouchTarget = with(rule.density) { 48.dp.toPx() }
        bounds.forEach {
            assertEquals(expectedHeight, it.height, 0.5f)
            assertTrue("toolbar zone must keep a 48 dp touch target", it.height >= minimumTouchTarget)
            assertTrue("toolbar zone must keep a 48 dp touch target", it.width >= minimumTouchTarget)
        }
        val width = bounds.first().width
        bounds.forEach { assertEquals(width, it.width, 0.5f) }
        bounds.zipWithNext().forEach { (left, right) ->
            assertEquals(left.right, right.left, 0.5f)
        }
    }

    // Test the component inside a viewport, rather than under the activity's system status bar.
    @Composable
    private fun ToolbarTestHost(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }

    // Optional device evidence of the production component, without domain data or a preview route.
    private fun saveVisualEvidence(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        Log.i("ToolbarEvidence", name)
    }

    private fun setToolbar(
        fontScale: Float = 1f,
        onOpenObjects: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenMapData: () -> Unit = {},
        mapDataOpen: Boolean = false,
        mapDataRestricted: Boolean = false,
    ) {
        rule.setContent {
            Bee_searchTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    ToolbarTestHost {
                    MapBottomPanel(
                        onOpenObjects = onOpenObjects,
                        onOpenSettings = onOpenSettings,
                        onOpenMapData = onOpenMapData,
                        mapDataOpen = mapDataOpen,
                        mapDataRestricted = mapDataRestricted,
                    )
                    }
                }
            }
        }
    }
}
