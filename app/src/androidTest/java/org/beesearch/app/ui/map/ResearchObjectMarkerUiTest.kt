package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResearchObjectMarkerUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun eachTypeRetainsDescriptionSelectionAnd48dpTarget() {
        rule.setContent {
            Column {
                listOf(false, true).forEach { selected ->
                    Row {
                        ResearchMarkerType.entries.forEach { type ->
                            ResearchObjectMarker(type, selected = selected)
                        }
                    }
                }
            }
        }
        ResearchMarkerType.entries.forEach { type ->
            listOf(false, true).forEachIndexed { index, selected ->
                val node = rule.onAllNodesWithTag("research-marker-${type.name.lowercase()}")[index]
                node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(type.contentDescription)))
                node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected))
                node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            }
        }
    }

    @Test
    fun selectedHaloActuallyRendersMoreWhitePixels() {
        rule.setContent {
            Surface(color = Color.DarkGray) {
                Row {
                    ResearchObjectMarker(ResearchMarkerType.LOG_HIVE, visualSize = 32.dp)
                    ResearchObjectMarker(ResearchMarkerType.LOG_HIVE, visualSize = 32.dp, selected = true)
                }
            }
        }
        fun whitePixels(index: Int): Int {
            val pixels = rule.onAllNodesWithTag("research-marker-log_hive")[index].captureToImage().toPixelMap()
            return (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x ->
                    val color = pixels[x, y]
                    color.red > .96f && color.green > .96f && color.blue > .96f
                }
            }
        }
        assertTrue("Selected outline must survive the pin fill", whitePixels(1) > whitePixels(0))
    }

    @Test
    fun defaultVisualSizeIs32dpAcrossDensities() {
        val densities = listOf(1f, 2f, 3f)
        // One composition: a px-derived size would report a different dp at density 2 and 3.
        rule.setContent {
            Row {
                densities.forEach { densityValue ->
                    CompositionLocalProvider(LocalDensity provides Density(densityValue, 1f)) {
                        ResearchObjectMarker(ResearchMarkerType.HOLLOW)
                    }
                }
            }
        }
        densities.forEachIndexed { index, _ ->
            rule.onAllNodesWithTag("research-marker-hollow-canvas")[index]
                .assertWidthIsEqualTo(32.dp)
                .assertHeightIsEqualTo(32.dp)
        }
    }
}
