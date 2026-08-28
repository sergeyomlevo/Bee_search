package org.beesearch.app

import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeeSelectorSelectionLogicTest {
    @Test
    fun `manual color selection starts with ordinary position`() {
        assertEquals(
            mark("WHITE", MarkPosition.NONE),
            BeeSelectorSelectionLogic.firstAvailableForColor("WHITE", available()),
        )
    }

    @Test
    fun `ordinary advances to right wing`() {
        val added = mark("WHITE", MarkPosition.NONE)

        assertEquals(
            mark("WHITE", MarkPosition.RIGHT_WING),
            BeeSelectorSelectionLogic.nextAfterAdded(added, available(added)),
        )
    }

    @Test
    fun `right wing advances to left wing`() {
        val ordinary = mark("WHITE", MarkPosition.NONE)
        val added = mark("WHITE", MarkPosition.RIGHT_WING)

        assertEquals(
            mark("WHITE", MarkPosition.LEFT_WING),
            BeeSelectorSelectionLogic.nextAfterAdded(added, available(ordinary, added)),
        )
    }

    @Test
    fun `exhausted color advances to first position of next catalog color`() {
        val white = BeeMarkCatalog.positions.map { position -> mark("WHITE", position) }

        assertEquals(
            mark("YELLOW", MarkPosition.NONE),
            BeeSelectorSelectionLogic.nextAfterAdded(white.last(), available(*white.toTypedArray())),
        )
    }

    @Test
    fun `occupied first position is skipped`() {
        assertEquals(
            mark("WHITE", MarkPosition.RIGHT_WING),
            BeeSelectorSelectionLogic.firstAvailableForColor(
                "WHITE",
                available(mark("WHITE", MarkPosition.NONE)),
            ),
        )
    }

    @Test
    fun `only free position of color is selected`() {
        val used = arrayOf(
            mark("WHITE", MarkPosition.NONE),
            mark("WHITE", MarkPosition.LEFT_WING),
        )

        assertEquals(
            mark("WHITE", MarkPosition.RIGHT_WING),
            BeeSelectorSelectionLogic.firstAvailableForColor("WHITE", available(*used)),
        )
    }

    @Test
    fun `manual position has priority when it is available`() {
        assertEquals(
            mark("WHITE", MarkPosition.LEFT_WING),
            BeeSelectorSelectionLogic.manualPosition(
                "WHITE",
                MarkPosition.LEFT_WING,
                available(),
            ),
        )
    }

    @Test
    fun `after manual position addition selection continues with next free position`() {
        val ordinary = mark("WHITE", MarkPosition.NONE)
        val manuallyAdded = mark("WHITE", MarkPosition.LEFT_WING)

        assertEquals(
            mark("WHITE", MarkPosition.RIGHT_WING),
            BeeSelectorSelectionLogic.nextAfterAdded(
                manuallyAdded,
                available(ordinary, manuallyAdded),
            ),
        )
    }

    @Test
    fun `manual color change selects its first available position`() {
        val yellowOrdinary = mark("YELLOW", MarkPosition.NONE)

        assertEquals(
            mark("YELLOW", MarkPosition.RIGHT_WING),
            BeeSelectorSelectionLogic.firstAvailableForColor(
                "YELLOW",
                available(yellowOrdinary),
            ),
        )
    }

    @Test
    fun `occupied combination cannot be selected manually`() {
        val occupied = mark("BLUE", MarkPosition.LEFT_WING)

        assertNull(
            BeeSelectorSelectionLogic.manualPosition(
                "BLUE",
                MarkPosition.LEFT_WING,
                available(occupied),
            ),
        )
    }

    @Test
    fun `full catalog leaves no automatic selection`() {
        val available = BeeMarkCatalog.availableCombinations(BeeMarkCatalog.supportedCombinations)

        assertNull(BeeSelectorSelectionLogic.firstAvailableForColor("WHITE", available))
        assertNull(
            BeeSelectorSelectionLogic.nextAfterAdded(
                BeeMarkCatalog.supportedCombinations.last(),
                available,
            ),
        )
    }

    private fun available(vararg used: BeeMarkCombination) =
        BeeMarkCatalog.availableCombinations(used.toList())

    private fun mark(color: String, position: MarkPosition) =
        BeeMarkCombination(color, position)
}
