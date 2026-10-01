package org.beesearch.app

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkersGeometryTest {
    private val center = Offset(100f, 100f)

    @Test
    fun visibleGpsPositionIsUsedDirectly() {
        assertEquals(
            Offset(160f, 40f),
            clipDirectionEndpointToViewport(center, Offset(160f, 40f), 200f, 200f),
        )
    }

    @Test
    fun offscreenGpsPositionContinuesToTheViewportEdge() {
        assertEquals(
            Offset(200f, 100f),
            clipDirectionEndpointToViewport(center, Offset(350f, 100f), 200f, 200f),
        )
        assertEquals(
            Offset(100f, 0f),
            clipDirectionEndpointToViewport(center, Offset(100f, -50f), 200f, 200f),
        )
    }

    @Test
    fun offscreenDiagonalKeepsItsDirectionAtTheEdge() {
        assertEquals(
            Offset(200f, 200f),
            clipDirectionEndpointToViewport(center, Offset(300f, 300f), 200f, 200f),
        )
    }
}
