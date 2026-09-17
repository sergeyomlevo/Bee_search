package org.beesearch.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeMarkCatalogTest {
    private val expectedColors = listOf("WHITE", "YELLOW", "BLUE", "RED", "GREEN")

    @Test
    fun catalogContainsExactlyTenMarksOfFiveColorsAndTwoPositions() {
        val combinations = BeeMarkCatalog.supportedCombinations

        assertEquals(10, combinations.size)
        assertEquals(5, BeeMarkCatalog.colors.size)
        assertEquals(2, BeeMarkCatalog.positions.size)
        assertEquals(BeeMarkCatalog.colors.size * BeeMarkCatalog.positions.size, combinations.size)
        assertEquals(expectedColors, BeeMarkCatalog.colors.map { it.value })
    }

    @Test
    fun everyColorOffersThoraxThenAbdomenAndNothingElse() {
        val combinations = BeeMarkCatalog.supportedCombinations

        assertEquals(
            listOf(
                "WHITE" to MarkPosition.THORAX,
                "WHITE" to MarkPosition.ABDOMEN,
                "YELLOW" to MarkPosition.THORAX,
                "YELLOW" to MarkPosition.ABDOMEN,
                "BLUE" to MarkPosition.THORAX,
                "BLUE" to MarkPosition.ABDOMEN,
                "RED" to MarkPosition.THORAX,
                "RED" to MarkPosition.ABDOMEN,
                "GREEN" to MarkPosition.THORAX,
                "GREEN" to MarkPosition.ABDOMEN,
            ),
            combinations.map { it.markColor to it.markPosition },
        )
        BeeMarkCatalog.colors.forEach { color ->
            val positions = combinations.filter { it.markColor == color.value }.map { it.markPosition }
            assertEquals(listOf(MarkPosition.THORAX, MarkPosition.ABDOMEN), positions)
        }
    }

    @Test
    fun legacyLeftWingIsNeverOfferedForANewBee() {
        assertTrue(MarkPosition.LEFT_WING in MarkPosition.entries)
        assertFalse(MarkPosition.LEFT_WING.isOfferedForNewBee)
        assertFalse(MarkPosition.LEFT_WING in BeeMarkCatalog.positions)
        assertFalse(BeeMarkCatalog.supportedCombinations.any { it.markPosition == MarkPosition.LEFT_WING })
    }

    @Test
    fun availableCombinationsAreDerivedFromUsedMarks() {
        val used = BeeMarkCatalog.supportedCombinations.dropLast(1)

        assertEquals(listOf(BeeMarkCatalog.supportedCombinations.last()), BeeMarkCatalog.availableCombinations(used))
        assertTrue(BeeMarkCatalog.availableCombinations(BeeMarkCatalog.supportedCombinations).isEmpty())
    }

    @Test
    fun accessibilityNamesDescribeColorAndPositionWithoutWingTerminology() {
        assertEquals("Белая метка, грудь", BeeMarkCatalog.displayName("WHITE", MarkPosition.THORAX))
        assertEquals("Жёлтая метка, брюшко", BeeMarkCatalog.displayName("YELLOW", MarkPosition.ABDOMEN))
        assertEquals("Синяя метка, грудь", BeeMarkCatalog.displayName("BLUE", MarkPosition.THORAX))
        assertEquals("Красная метка, брюшко", BeeMarkCatalog.displayName("RED", MarkPosition.ABDOMEN))
        assertEquals("Зелёная метка, грудь", BeeMarkCatalog.displayName("GREEN", MarkPosition.THORAX))
        assertEquals("Белая метка, брюшко", BeeMarkCatalog.displayName("WHITE", MarkPosition.ABDOMEN))

        BeeMarkCatalog.colors.forEach { color ->
            MarkPosition.newBeePositions.forEach { position ->
                val name = BeeMarkCatalog.displayName(color.value, position)
                assertTrue(name.isNotBlank())
                assertFalse(name.contains("КП"))
                assertFalse(name.contains("КЛ"))
                assertFalse(name.contains("крыл", ignoreCase = true))
            }
        }
    }

    @Test
    fun legacyPositionNameDoesNotClaimARealPosition() {
        assertEquals("Грудь", BeeMarkCatalog.positionDisplayName(MarkPosition.THORAX))
        assertEquals("Брюшко", BeeMarkCatalog.positionDisplayName(MarkPosition.ABDOMEN))
        val legacy = BeeMarkCatalog.positionDisplayName(MarkPosition.LEFT_WING)
        assertFalse(legacy.contains("Грудь"))
        assertFalse(legacy.contains("Брюшко"))
    }
}
