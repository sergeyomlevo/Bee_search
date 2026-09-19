package org.beesearch.app.ui.properties

import org.junit.Assert.assertEquals
import org.junit.Test

class PointPropertiesPresentationTest {
    @Test
    fun windDirectionUsesSixteenPointRussianLabels() {
        assertEquals("ЗЮЗ", windDirectionLabel(247.0))
        assertEquals("С", windDirectionLabel(0.0))
        assertEquals("С", windDirectionLabel(359.0))
        assertEquals("Ю", windDirectionLabel(180.0))
    }

    @Test
    fun invalidWindDirectionIsNotInvented() {
        assertEquals("—", windDirectionLabel(null))
        assertEquals("—", windDirectionLabel(Double.NaN))
    }
}
