package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class MarkerVisualPreviewTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun previewIsExplicitAndCloseRemovesReservedSpecimens() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MaterialTheme { MarkerVisualPreview(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithTag("research-marker-apiary").assertDoesNotExist()
        rule.onNodeWithTag("marker-preview-open").performClick()
        rule.onNodeWithText("✓ 32 dp").assertExists()
        val controls = rule.onNodeWithTag("marker-preview-controls").fetchSemanticsNode().boundsInRoot
        val marker = rule.onAllNodesWithTag("research-marker-observation_point")[0].fetchSemanticsNode().boundsInRoot
        assertTrue("Specimens must remain below enlarged controls", marker.top >= controls.bottom)
        val viewport = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Specimens must leave the fixed map centre clear", marker.top > viewport.center.y)
        rule.onNodeWithText("20 dp").performClick()
        rule.onNodeWithText("✓ 20 dp").assertExists()
        rule.onNodeWithText("К px").performClick()
        rule.onNodeWithText("✓ 20 px").assertExists()
        rule.onNodeWithText("Сравнить dp").performClick()
        rule.onNodeWithText("✓ 20 dp").assertExists()
        rule.onNodeWithText("Рядом").performClick()
        rule.onNodeWithText("Разнести").assertExists()
        rule.onNodeWithTag("marker-preview-close").performClick()
        rule.onNodeWithTag("research-marker-apiary").assertDoesNotExist()
    }

    @Test
    fun enlargedFontCrowdedGroupStaysInsideViewport() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MaterialTheme { MarkerVisualPreview(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithTag("marker-preview-open").performClick()
        rule.onNodeWithText("Рядом").performClick()
        val viewport = rule.onRoot().fetchSemanticsNode().boundsInRoot
        ResearchMarkerType.entries.forEach { type ->
            rule.onAllNodesWithTag("research-marker-${type.name.lowercase()}").fetchSemanticsNodes().forEach { marker ->
                assertTrue("Crowded specimen must remain in viewport", marker.boundsInRoot.left >= viewport.left)
                assertTrue("Crowded specimen must remain in viewport", marker.boundsInRoot.right <= viewport.right)
                assertTrue("Crowded specimen must remain in viewport", marker.boundsInRoot.top >= viewport.top)
                assertTrue("Crowded specimen must remain in viewport", marker.boundsInRoot.bottom <= viewport.bottom)
            }
        }
    }
}
