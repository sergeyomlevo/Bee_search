package org.beesearch.app

import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapCameraPadding
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.addCoverageFragment
import org.beesearch.app.ui.map.clearCoverageFragments
import org.beesearch.app.ui.map.coverageBoundsSummary
import org.beesearch.app.ui.map.coverageReviewCameraPadding
import org.beesearch.app.ui.map.coverageBoundsForShowAll
import org.beesearch.app.ui.map.formatMapPackageBuilderBounds
import org.beesearch.app.ui.map.normalMapCameraPadding
import org.beesearch.app.ui.map.normalizeMapPackageBounds
import org.beesearch.app.ui.map.undoLastCoverageFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoverageSelectionTest {
    private val firstViewportAtZoom15 = MapGeoBounds(
        north = 56.21,
        east = 42.80,
        south = 56.18,
        west = 42.75,
    )
    private val overlappingViewportAtZoom16 = MapGeoBounds(
        north = 56.20,
        east = 42.84,
        south = 56.17,
        west = 42.78,
    )
    private val separateViewportAtZoom16 = MapGeoBounds(
        north = 56.16,
        east = 42.90,
        south = 56.13,
        west = 42.85,
    )

    @Test
    fun `several fragments stay as independently selected rectangles including overlap`() {
        val afterFirst = addCoverageFragment(emptyList(), firstViewportAtZoom15)
        val afterSecond = addCoverageFragment(afterFirst, overlappingViewportAtZoom16)

        assertEquals(
            listOf(
                MapCoverageFragment(firstViewportAtZoom15),
                MapCoverageFragment(overlappingViewportAtZoom16),
            ),
            afterSecond,
        )
        assertTrue(firstViewportAtZoom15.east > overlappingViewportAtZoom16.west)
        assertTrue(firstViewportAtZoom15.south < overlappingViewportAtZoom16.north)
    }

    @Test
    fun `adding a later viewport does not change geographic geometry already selected`() {
        val afterFirst = addCoverageFragment(emptyList(), firstViewportAtZoom15)
        val afterSecond = addCoverageFragment(afterFirst, separateViewportAtZoom16)

        assertEquals(firstViewportAtZoom15, afterSecond.first().bounds)
        assertEquals(separateViewportAtZoom16, afterSecond.last().bounds)
    }

    @Test
    fun `undo removes only the last fragment and clear removes all fragments`() {
        val fragments = listOf(
            MapCoverageFragment(firstViewportAtZoom15),
            MapCoverageFragment(overlappingViewportAtZoom16),
            MapCoverageFragment(separateViewportAtZoom16),
        )

        assertEquals(fragments.dropLast(1), undoLastCoverageFragment(fragments))
        assertEquals(emptyList<MapCoverageFragment>(), clearCoverageFragments())
    }

    @Test
    fun `show all bounds frame every fragment without replacing the selection`() {
        val fragments = listOf(
            MapCoverageFragment(firstViewportAtZoom15),
            MapCoverageFragment(separateViewportAtZoom16),
        )

        assertEquals(
            MapGeoBounds(north = 56.21, east = 42.90, south = 56.13, west = 42.75),
            coverageBoundsForShowAll(fragments),
        )
        assertEquals(
            listOf(
                MapCoverageFragment(firstViewportAtZoom15),
                MapCoverageFragment(separateViewportAtZoom16),
            ),
            fragments,
        )
    }

    @Test
    fun `leaving coverage mode restores unpadded normal camera geometry`() {
        assertEquals(
            MapCameraPadding(left = 16, top = 16, right = 16, bottom = 246),
            coverageReviewCameraPadding(controlsHeightPx = 230, edgePaddingPx = 16),
        )
        assertEquals(MapCameraPadding(), normalMapCameraPadding())
    }

    @Test
    fun `current viewport summary reports its own coordinates and dimensions`() {
        val summary = coverageBoundsSummary(firstViewportAtZoom15)

        assertEquals(firstViewportAtZoom15, summary.bounds)
        assertTrue(summary.widthKm > 0.0)
        assertTrue(summary.heightKm > 0.0)
        assertTrue(summary.areaKm2 > 0.0)
    }

    @Test
    fun `selected bbox is normalized outward to PMTiles header precision and exports in builder order`() {
        val raw = MapGeoBounds(
            north = 56.444694041,
            east = 42.729915019,
            south = 56.153038049,
            west = 42.288289091,
        )

        val normalized = normalizeMapPackageBounds(raw)

        assertEquals(
            MapGeoBounds(
                north = 56.4446941,
                east = 42.7299151,
                south = 56.1530380,
                west = 42.2882890,
            ),
            normalized,
        )
        assertEquals(
            "-West 42.2882890 -South 56.1530380 -East 42.7299151 -North 56.4446941",
            formatMapPackageBuilderBounds(normalized),
        )
    }
}
