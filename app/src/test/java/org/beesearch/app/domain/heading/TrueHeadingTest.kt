package org.beesearch.app.domain.heading

import org.junit.Assert.assertEquals
import org.junit.Test

class TrueHeadingTest {
    @Test
    fun normalizationCoversZeroUpperBoundaryNegativeAndOverflow() {
        assertEquals(0.0, normalizeHeadingDegrees(0.0), 0.0001)
        assertEquals(359.8, normalizeHeadingDegrees(359.8), 0.0001)
        assertEquals(359.0, normalizeHeadingDegrees(-1.0), 0.0001)
        assertEquals(1.0, normalizeHeadingDegrees(361.0), 0.0001)
    }

    @Test
    fun declinationConvertsMagneticHeadingToTrueHeading() {
        assertEquals(110.0, trueHeadingDegrees(100.0, 10.0), 0.0001)
        assertEquals(90.0, trueHeadingDegrees(100.0, -10.0), 0.0001)
    }

    @Test
    fun declinationCorrectionWrapsAcrossNorthInBothDirections() {
        assertEquals(4.0, trueHeadingDegrees(359.0, 5.0), 0.0001)
        assertEquals(356.0, trueHeadingDegrees(1.0, -5.0), 0.0001)
    }

    @Test
    fun roundedHeadingNeverProduces360() {
        assertEquals(0, roundedHeadingDegrees(359.6))
        assertEquals(359, roundedHeadingDegrees(359.4))
    }
}
