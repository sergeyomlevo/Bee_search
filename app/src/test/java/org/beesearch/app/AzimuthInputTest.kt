package org.beesearch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzimuthInputTest {
    @Test
    fun zeroAnd359AreAccepted() {
        assertEquals(0, validateManualAzimuth("0").value)
        assertEquals(359, validateManualAzimuth("359").value)
    }

    @Test
    fun emptyInputIsRejected() {
        val result = validateManualAzimuth("  ")

        assertNull(result.value)
        assertEquals("Введите азимут", result.errorMessage)
    }

    @Test
    fun valuesOutsideRangeAreRejectedWithoutNormalization() {
        assertNull(validateManualAzimuth("-1").value)
        assertNull(validateManualAzimuth("360").value)
        assertEquals("Азимут должен быть от 0° до 359°", validateManualAzimuth("360").errorMessage)
    }

    @Test
    fun textAndFractionAreRejected() {
        assertNull(validateManualAzimuth("север").value)
        assertNull(validateManualAzimuth("247.5").value)
        assertEquals(
            "Введите целое число от 0 до 359",
            validateManualAzimuth("247.5").errorMessage,
        )
    }

    @Test
    fun actionFormattingDistinguishesMissingZeroAndSavedValues() {
        assertEquals("Азимут —", formatAzimuthAction(null))
        assertEquals("Азимут 0°", formatAzimuthAction(0.0))
        assertEquals("Азимут 247°", formatAzimuthAction(247.0))
        assertEquals("Азимут 127,4°", formatAzimuthAction(127.4))
    }
}
