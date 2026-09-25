package org.beesearch.app.ui.physicalobjects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalObjectFormStateTest {
    @Test
    fun `hollow requires positive values and azimuth range`() {
        val result = PhysicalObjectFormState(
            tree = "   ", entranceHeightCm = "0", azimuthDeg = "360", outerDiameterCm = "-1",
        ).validateHollow()
        assertFalse(result.isValid)
        assertEquals("Укажите дерево", result.errors["tree"])
        assertTrue(result.errors.containsKey("entranceHeightCm"))
        assertTrue(result.errors.containsKey("azimuthDeg"))
        assertTrue(result.errors.containsKey("outerDiameterCm"))
    }

    @Test
    fun `hollow accepts omitted optional internal diameter`() {
        val result = PhysicalObjectFormState(
            tree = "дуб", entranceHeightCm = "120", azimuthDeg = "0", outerDiameterCm = "42",
        ).validateHollow()
        assertTrue(result.isValid)
        assertNull(result.hollow!!.internalDiameterCm)
    }

    @Test
    fun `log hive requires material and construction dimensions`() {
        val result = PhysicalObjectFormState(
            tree = "осина", entranceHeightCm = "120", azimuthDeg = "123", outerDiameterCm = "42",
        ).validateLogHive()
        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey("material"))
        assertTrue(result.errors.containsKey("internalDiameterCm"))
        assertTrue(result.errors.containsKey("internalHeightCm"))
    }

    @Test
    fun `log hive trims required text before creating properties`() {
        val result = PhysicalObjectFormState(
            tree = " осина ", entranceHeightCm = "120", azimuthDeg = "359", outerDiameterCm = "42",
            material = " липа ", internalDiameterCm = "30", internalHeightCm = "100", notes = " заметка ",
        ).validateLogHive()
        assertTrue(result.isValid)
        assertEquals("осина", result.logHive!!.tree)
        assertEquals("липа", result.logHive.material)
        assertEquals("заметка", result.logHive.notes)
    }
}
