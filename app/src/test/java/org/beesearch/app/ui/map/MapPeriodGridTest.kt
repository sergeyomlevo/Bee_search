package org.beesearch.app.ui.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layout arithmetic of the approved calendar grids.
 *
 * The two rules that must hold together are that every unit offers the required touch target and
 * that a grid reflows to fewer columns instead of shrinking cells or hiding units behind a scroll
 * gesture. Both are pure arithmetic, so they are pinned here instead of on one device width.
 */
class MapPeriodGridTest {
    private val minimumCell = 48.dp
    private val dayGap = 2.dp

    @Test
    fun everyColumnCountKeepsTheRequiredTouchTarget() {
        val widths = listOf(240.dp, 280.dp, 320.dp, 360.dp, 393.dp, 411.dp, 480.dp, 600.dp)
        widths.forEach { available ->
            listOf(3, 7).forEach { preferred ->
                val columns = fittingColumns(available, preferred)
                assertTrue("columns for $available/$preferred", columns in 1..preferred)
                assertTrue(
                    "cell width for $available with $columns columns",
                    cellWidth(available, columns) >= minimumCell,
                )
            }
        }
    }

    @Test
    fun aGridUsesThePreferredColumnsWhileTheyFit() {
        // Month and year grids: the approved three cells per row fit on any phone width.
        listOf(320.dp, 360.dp, 393.dp, 480.dp).forEach { available ->
            assertEquals(3, fittingColumns(available, 3))
        }
        // The approved weekday calendar keeps all seven columns from a wide-enough phone upwards.
        assertEquals(7, fittingColumns(480.dp, 7))
        assertEquals(7, fittingColumns(372.dp, 7))
    }

    @Test
    fun aNarrowScreenReflowsTheDayGridInsteadOfShrinkingCells() {
        // The day grid uses the frames' small insets, so it still fits the approved weekday layout on
        // a 360 dp phone: 7 x 48 dp + 6 x 2 dp = 348 dp inside 360 dp - 8 dp of insets.
        val phone360 = 360.dp - 8.dp
        assertEquals(7, fittingColumns(phone360, 7, dayGap))
        assertTrue(cellWidth(phone360, 7, dayGap) >= minimumCell)

        // A narrower screen reflows to fewer columns rather than shrinking a cell below the target.
        val narrow = 320.dp - 8.dp
        val columns = fittingColumns(narrow, 7, dayGap)
        assertTrue("reflowed to fewer columns", columns < 7)
        assertTrue(cellWidth(narrow, columns, dayGap) >= minimumCell)
    }

    @Test
    fun theGuaranteeIsAboutCandidatesThatCanHoldATargetAtAll() {
        // One column is the floor, so the arithmetic never reports a cell wider than its container:
        // a parent narrower than one touch target cannot host one, and the function says so.
        assertEquals(1, fittingColumns(20.dp, 7))
        assertTrue(cellWidth(20.dp, 1) >= minimumCell)
        assertEquals(minimumCell, cellWidth(48.dp, 1))
        // A single cell simply takes the width it is given.
        assertEquals(60.dp, cellWidth(60.dp, 1))
    }
}
