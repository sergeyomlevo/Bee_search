package org.beesearch.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each map mode shows for the Ареал.
 *
 * Viewing the Ареал and editing its участки are different screens, and the field map is a third one.
 * These tests fix that separation, because it is what keeps an accidental tap in the view mode from
 * changing saved geometry.
 */
class MapAreaPresentationTest {
    private val stored = MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)
    private val storedSecond = MapGeoBounds(north = 56.9, east = 38.9, south = 56.8, west = 38.8)
    private val extra = MapGeoBounds(north = 55.0, east = 37.0, south = 54.0, west = 36.0)

    private val persistedFragments = listOf(
        MapCoverageFragment(stored),
        MapCoverageFragment(storedSecond),
    )

    private fun presentation(
        mode: BeeMapMode,
        editorOpen: Boolean = false,
        working: List<MapCoverageFragment> = emptyList(),
        persisted: List<MapCoverageFragment> = persistedFragments,
        persistedLoaded: Boolean = true,
    ) = mapAreaPresentation(
        mode = mode,
        editorOpen = editorOpen,
        working = working,
        persisted = persisted,
        persistedLoaded = persistedLoaded,
    )

    @Test
    fun `the area view shows exactly the stored участки`() {
        val view = presentation(BeeMapMode.AREA_VIEW)

        // Every saved участок, in the stored order, with no merging and no simplification.
        assertEquals(persistedFragments, view.fragments)
        assertTrue(view.drawFragments)
    }

    @Test
    fun `the area view is not the editor`() {
        val view = presentation(BeeMapMode.AREA_VIEW)

        assertFalse(view.editorOpen)
        assertFalse("the view must not mark a viewport for editing", view.showViewportFrame)
    }

    @Test
    fun `the area view frames the whole area once`() {
        val view = presentation(BeeMapMode.AREA_VIEW)

        assertTrue(view.frameWholeArea)
        assertEquals(
            MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0),
            unionBounds(view.fragments.map(MapCoverageFragment::bounds))!!,
        )
    }

    @Test
    fun `the field map draws nothing even though the area is loaded`() {
        val field = presentation(BeeMapMode.FIELD)

        assertFalse(field.drawFragments)
        assertFalse(field.editorOpen)
        assertFalse(field.frameWholeArea)
        assertEquals(persistedFragments, field.fragments)
    }

    @Test
    fun `the editor shows the draft and its viewport frame`() {
        val draft = listOf(MapCoverageFragment(stored), MapCoverageFragment(extra))

        val editor = presentation(
            mode = BeeMapMode.FIELD,
            editorOpen = true,
            working = draft,
            persisted = persistedFragments,
        )

        assertEquals(draft, editor.fragments)
        assertTrue(editor.drawFragments)
        assertTrue(editor.showViewportFrame)
        assertTrue(editor.editorOpen)
        // The editor never re-frames the camera by itself: the user is choosing участки.
        assertFalse(editor.frameWholeArea)
    }

    @Test
    fun `an empty editor draft still marks the viewport that would be added`() {
        val editor = presentation(mode = BeeMapMode.FIELD, editorOpen = true, working = emptyList())

        // Nothing is drawn, but the editor keeps showing which rectangle «Добавить участок» would add.
        assertTrue(editor.fragments.isEmpty())
        assertTrue(editor.showViewportFrame)
    }

    @Test
    fun `nothing is shown before the area of the territory is loaded`() {
        val view = presentation(BeeMapMode.AREA_VIEW, persisted = emptyList(), persistedLoaded = false)

        assertFalse(view.drawFragments)
        assertTrue(view.fragments.isEmpty())
    }
}
