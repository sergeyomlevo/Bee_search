package org.beesearch.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapCenterTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun targetRemainsAtExactViewportCenterWhenCameraCoordinatesChange() {
        var simulatedCameraChange by mutableIntStateOf(0)
        composeRule.setContent {
            Bee_searchTheme {
                Box(Modifier.size(200.dp).testTag("map-viewport")) {
                    Text("camera-$simulatedCameraChange", Modifier.align(Alignment.TopStart))
                    MapCenterTarget(Modifier.align(Alignment.Center))
                }
            }
        }

        val before = composeRule
            .onNodeWithContentDescription(MAP_CENTER_TARGET_DESCRIPTION)
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle { simulatedCameraChange += 1 }
        composeRule.waitForIdle()

        val after = composeRule
            .onNodeWithContentDescription(MAP_CENTER_TARGET_DESCRIPTION)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(before, after)
        val expectedCenterPx = with(composeRule.density) { 100.dp.toPx() }
        assertEquals(expectedCenterPx, after.center.x, 0.5f)
        assertEquals(expectedCenterPx, after.center.y, 0.5f)
    }

    @Test
    fun gpsMarkerUsesProjectedScreenPositionIndependentlyOfMapStyle() {
        val expectedCenterPx = with(composeRule.density) { 100.dp.toPx() }
        composeRule.setContent {
            Bee_searchTheme {
                Box(Modifier.size(200.dp)) {
                    MapGpsMarker(Offset(expectedCenterPx, expectedCenterPx))
                }
            }
        }

        val markerBounds = composeRule
            .onNodeWithContentDescription(MAP_GPS_MARKER_DESCRIPTION)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(expectedCenterPx, markerBounds.center.x, 0.5f)
        assertEquals(expectedCenterPx, markerBounds.center.y, 0.5f)
    }
}
