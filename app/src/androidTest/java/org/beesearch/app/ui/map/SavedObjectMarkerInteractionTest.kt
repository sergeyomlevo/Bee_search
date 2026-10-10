package org.beesearch.app.ui.map

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Actual touch, rather than a semantics action, over a native map-like view. */
class SavedObjectMarkerInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun positionedMarkerReceivesTouchAboveNativeView() {
        val marker = MapObjectMarker(MapObjectType.LOG_HIVE, UUID.randomUUID(), 56.0, 43.0, "Колода")
        rule.setContent {
            var selected by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { View(it).apply { isClickable = true } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().zIndex(2f)) {
                    SavedObjectMarker(marker, onClick = { selected = !selected }, selected = selected,
                        modifier = Modifier.offset { IntOffset(300, 600) })
                }
            }
        }
        rule.onNodeWithTag(markerTestTag(marker)).performTouchInput { click() }.assertIsSelected()
    }
}
