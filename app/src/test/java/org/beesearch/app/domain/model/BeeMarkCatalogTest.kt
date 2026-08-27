package org.beesearch.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeMarkCatalogTest {
    @Test
    fun catalogContainsEveryColorAndPositionCombination() {
        val combinations = BeeMarkCatalog.supportedCombinations

        assertEquals(BeeMarkCatalog.colors.size * BeeMarkCatalog.positions.size, combinations.size)
        BeeMarkCatalog.colors.forEach { color ->
            MarkPosition.entries.forEach { position ->
                assertTrue(BeeMarkCombination(color.value, position) in combinations)
            }
        }
    }

    @Test
    fun availableCombinationsAreDerivedFromUsedMarks() {
        val used = BeeMarkCatalog.supportedCombinations.dropLast(1)

        assertEquals(listOf(BeeMarkCatalog.supportedCombinations.last()), BeeMarkCatalog.availableCombinations(used))
        assertTrue(BeeMarkCatalog.availableCombinations(BeeMarkCatalog.supportedCombinations).isEmpty())
    }

    @Test
    fun displayNamesKeepPositionVisibleInText() {
        assertEquals("Белая", BeeMarkCatalog.displayName("WHITE", MarkPosition.NONE))
        assertEquals("Белая КП", BeeMarkCatalog.displayName("WHITE", MarkPosition.RIGHT_WING))
        assertEquals("Белая КЛ", BeeMarkCatalog.displayName("WHITE", MarkPosition.LEFT_WING))
        assertFalse(BeeMarkCatalog.displayName("BLUE", MarkPosition.RIGHT_WING).isBlank())
    }
}
