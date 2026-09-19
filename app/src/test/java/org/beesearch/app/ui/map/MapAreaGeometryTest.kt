package org.beesearch.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The derived size of an Ареал.
 *
 * The card shows one number for the whole Ареал, so it has to describe the Ареал itself: an overlap
 * between two участки is one piece of ground, a участок inside another adds nothing, and the empty
 * space between separate участки is not part of the Ареал. Nothing here is ever stored.
 */
class MapAreaGeometryTest {
    private fun bounds(
        north: Double,
        east: Double,
        south: Double,
        west: Double,
    ) = MapGeoBounds(north = north, east = east, south = south, west = west)

    private fun area(vararg bounds: MapGeoBounds) = areaUnionKm2(bounds.toList())

    @Test
    fun `one rectangle has the area of that rectangle`() {
        val oneDegreeByOneDegree = bounds(north = 1.0, east = 1.0, south = 0.0, west = 0.0)

        val measured = area(oneDegreeByOneDegree)

        // Cross-checked against a different formula: near the equator one degree of latitude and one
        // degree of longitude are both R × 1°, so the flat rectangle is R² × (1°)².
        val flatReference = EARTH_RADIUS_KM * Math.toRadians(1.0)
        assertEquals(flatReference * flatReference, measured, flatReference * flatReference * 0.01)
    }

    @Test
    fun `two separate rectangles add up`() {
        val first = bounds(north = 1.0, east = 1.0, south = 0.0, west = 0.0)
        val second = bounds(north = 3.0, east = 3.0, south = 2.0, west = 2.0)

        assertEquals(area(first) + area(second), area(first, second), 1e-6)
    }

    @Test
    fun `two adjacent rectangles add up without a gap or a double count`() {
        val west = bounds(north = 1.0, east = 1.0, south = 0.0, west = 0.0)
        val east = bounds(north = 1.0, east = 2.0, south = 0.0, west = 1.0)

        assertEquals(area(west) + area(east), area(west, east), 1e-6)
        // Touching rectangles form one larger rectangle, so the union equals that rectangle.
        assertEquals(area(bounds(north = 1.0, east = 2.0, south = 0.0, west = 0.0)), area(west, east), 1e-6)
    }

    @Test
    fun `a partial overlap is counted once`() {
        val first = bounds(north = 2.0, east = 2.0, south = 0.0, west = 0.0)
        val second = bounds(north = 3.0, east = 3.0, south = 1.0, west = 1.0)
        val overlap = bounds(north = 2.0, east = 2.0, south = 1.0, west = 1.0)

        val union = area(first, second)

        assertEquals(area(first) + area(second) - area(overlap), union, 1e-6)
        assertTrue("overlap must not be counted twice", union < area(first) + area(second))
    }

    @Test
    fun `a rectangle inside another adds nothing`() {
        val outer = bounds(north = 2.0, east = 2.0, south = 0.0, west = 0.0)
        val inner = bounds(north = 1.5, east = 1.5, south = 0.5, west = 0.5)

        assertEquals(area(outer), area(outer, inner), 1e-6)
    }

    @Test
    fun `identical rectangles count once`() {
        val one = bounds(north = 1.0, east = 1.0, south = 0.0, west = 0.0)

        assertEquals(area(one), area(one, one), 1e-9)
    }

    @Test
    fun `the order of bounds does not change the total area`() {
        val first = bounds(north = 2.0, east = 2.0, south = 0.0, west = 0.0)
        val second = bounds(north = 3.0, east = 3.0, south = 1.0, west = 1.0)
        val third = bounds(north = 0.5, east = 5.0, south = 0.25, west = 4.0)

        assertEquals(area(first, second, third), area(third, first, second), 1e-6)
    }

    @Test
    fun `an empty set of bounds has no area`() {
        assertEquals(0.0, areaUnionKm2(emptyList()), 0.0)
    }

    @Test
    fun `a degenerate rectangle has no area`() {
        assertEquals(0.0, area(bounds(north = 1.0, east = 1.0, south = 1.0, west = 0.0)), 1e-9)
    }

    @Test
    fun `one участок shows the same area in the editor and on the card`() {
        val section = bounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)

        // The editor summary and the Ареал card must never disagree about the same geometry.
        assertEquals(coverageBoundsSummary(section).areaKm2, area(section), 0.0)
    }

    @Test
    fun `the outer extent frames all bounds and is not a replacement for them`() {
        val first = bounds(north = 2.0, east = 5.0, south = 0.0, west = 0.0)
        val second = bounds(north = 4.0, east = 1.0, south = 3.0, west = -2.0)

        assertEquals(
            bounds(north = 4.0, east = 5.0, south = 0.0, west = -2.0),
            unionBounds(listOf(first, second)),
        )
        // The two участки stay separate, so the gap between them is not part of the Ареал.
        assertTrue(area(first, second) < area(unionBounds(listOf(first, second))!!))
        assertNull(unionBounds(emptyList()))
    }

    @Test
    fun `coverage show all frames exactly the outer extent`() {
        val fragments = listOf(
            MapCoverageFragment(bounds(north = 2.0, east = 5.0, south = 0.0, west = 0.0)),
            MapCoverageFragment(bounds(north = 4.0, east = 1.0, south = 3.0, west = -2.0)),
        )

        assertEquals(unionBounds(fragments.map(MapCoverageFragment::bounds)), coverageBoundsForShowAll(fragments))
    }
}
