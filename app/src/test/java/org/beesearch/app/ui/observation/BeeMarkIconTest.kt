package org.beesearch.app.ui.observation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The icon carries the mark position through geometry: the mark colour is
 * painted on the thorax or on the abdomen, and every other part stays dark.
 */
class BeeMarkIconTest {
    @Test
    fun thoraxMarkPaintsTheColorOnTheThoraxOnly() {
        val colors = beeMarkSegmentColors("YELLOW", MarkPosition.THORAX)

        assertEquals(markColorValue("YELLOW"), colors.thorax)
        assertEquals(BeeMarkBodyDark, colors.head)
        assertEquals(BeeMarkBodyDark, colors.abdomen)
        assertEquals(BeeBodySegment.THORAX, beeMarkMarkedSegment(MarkPosition.THORAX))
    }

    @Test
    fun abdomenMarkPaintsTheColorOnTheAbdomenOnly() {
        val colors = beeMarkSegmentColors("YELLOW", MarkPosition.ABDOMEN)

        assertEquals(markColorValue("YELLOW"), colors.abdomen)
        assertEquals(BeeMarkBodyDark, colors.head)
        assertEquals(BeeMarkBodyDark, colors.thorax)
        assertEquals(BeeBodySegment.ABDOMEN, beeMarkMarkedSegment(MarkPosition.ABDOMEN))
    }

    @Test
    fun sameColorOnDifferentPositionsIsPaintedOnDifferentSegments() {
        BeeMarkCatalog.colors.forEach { color ->
            val thorax = beeMarkSegmentColors(color.value, MarkPosition.THORAX)
            val abdomen = beeMarkSegmentColors(color.value, MarkPosition.ABDOMEN)

            assertEquals(markColorValue(color.value), thorax.thorax)
            assertEquals(markColorValue(color.value), abdomen.abdomen)
            assertNotEquals(thorax.thorax, thorax.abdomen)
            assertNotEquals(abdomen.thorax, abdomen.abdomen)
        }
    }

    @Test
    fun whiteStaysWhiteAndReliesOnADarkOutlineForVisibility() {
        val thoraxMark = beeMarkSegmentColors("WHITE", MarkPosition.THORAX)

        assertEquals("WHITE", BeeMarkCatalog.colors.first().value)
        assertEquals(Color.White, thoraxMark.thorax)
        assertNotEquals(Color.Gray, thoraxMark.thorax)
        assertTrue(BeeMarkOutline.luminance() < 0.1f)
        assertTrue(contrastRatio(Color.White, BeeMarkOutline) >= 4.5)
    }

    @Test
    fun everyMarkColorStaysDistinctFromTheDarkBody() {
        BeeMarkCatalog.colors.forEach { color ->
            val mark = markColorValue(color.value)
            BeeMarkCatalog.positions.forEach { position ->
                val colors = beeMarkSegmentColors(color.value, position)
                val painted = if (position == MarkPosition.THORAX) colors.thorax else colors.abdomen
                assertEquals(mark, painted)
                assertTrue(contrastRatio(painted, BeeMarkOutline) >= 1.5)
            }
        }
    }

    @Test
    fun legacyLeftWingHasNoMarkedSegmentAndFallsBackToTheDarkBody() {
        assertNull(beeMarkMarkedSegment(MarkPosition.LEFT_WING))
        val colors = beeMarkSegmentColors("YELLOW", MarkPosition.LEFT_WING)
        assertEquals(BeeMarkBodyDark, colors.head)
        assertEquals(BeeMarkBodyDark, colors.thorax)
        assertEquals(BeeMarkBodyDark, colors.abdomen)
    }

    @Test
    fun unknownColorFallsBackToAGrayMarkInsteadOfCrashing() {
        val colors = beeMarkSegmentColors("MAGENTA", MarkPosition.ABDOMEN)
        assertEquals(Color.Gray, colors.abdomen)
    }

    /**
     * The silhouette has to read as a bee rather than as a stack of dots: a
     * small head, a noticeably larger thorax, and an abdomen that is the biggest
     * part of the body and elongated vertically.
     */
    @Test
    fun silhouetteUsesTheReviewedProportions() {
        val headDiameter = BeeHeadRadius * 2f
        val thoraxDiameter = BeeThoraxRadius * 2f
        val abdomenWidth = BeeAbdomenRadiusX * 2f
        val abdomenHeight = BeeAbdomenRadiusY * 2f

        assertTrue("Head must be smaller than the thorax", headDiameter < thoraxDiameter)
        assertTrue("Abdomen must be taller than the thorax", abdomenHeight > thoraxDiameter)
        assertTrue("Abdomen must be the dominant mass", abdomenHeight > abdomenWidth * 1.3f)
        assertTrue("Thorax must be noticeably larger than the head", thoraxDiameter > headDiameter * 1.5f)
        assertTrue("Abdomen must be at least as wide as the thorax", abdomenWidth >= thoraxDiameter * 0.95f)
    }

    /**
     * The drawn body and its outline must fit completely inside the reserved
     * slot, otherwise a card would clip the very part that carries the mark.
     */
    @Test
    fun silhouetteAndOutlineStayInsideTheIconSlot() {
        val halfOutline = BeeMarkOutlineFraction / 2f
        val headTop = BeeHeadCenterY - BeeHeadRadius - halfOutline
        val abdomenBottom = BeeAbdomenCenterY + BeeAbdomenRadiusY + halfOutline
        val widestHalfWidth = maxOf(BeeThoraxRadius, BeeAbdomenRadiusX) + halfOutline

        assertTrue("Head must not be clipped at the top: $headTop", headTop >= 0f)
        assertTrue("Abdomen must not be clipped at the bottom: $abdomenBottom", abdomenBottom <= 1f)
        assertTrue(
            "Body must fit into the reserved width: $widestHalfWidth",
            widestHalfWidth <= BeeMarkAspectRatio / 2f,
        )
    }

    /**
     * The reference silhouette is markedly taller than wide, which is what lets
     * the mark span both card rows without widening the card.
     */
    @Test
    fun silhouetteIsTallerThanWide() {
        assertTrue(BeeMarkAspectRatio < 0.5f)
        assertTrue(BeeMarkIconHeight >= 72.dp)
    }

    /**
     * Field review reported that darker red, blue and green marks go blind in
     * direct sunlight: at low luminance the filled segment stops separating
     * from the dark body, so the whole bee reads as one dark blob. Every mark
     * colour must therefore stay bright and must separate from the body.
     */
    @Test
    fun everyMarkColorStaysBrightEnoughForDirectSunlight() {
        BeeMarkCatalog.colors.forEach { color ->
            val value = markColorValue(color.value)

            assertTrue(
                "${color.value} is too dark for outdoor use",
                value.luminance() >= 0.22f,
            )
            assertTrue(
                "${color.value} does not separate from the dark body: " +
                    "contrast ${contrastRatio(value, BeeMarkOutline)}",
                contrastRatio(value, BeeMarkOutline) >= 4.0f,
            )
        }
    }

    /**
     * Brightening must not collapse two marks into the same appearance. Any two
     * chromatic colours must differ by lightness or by hue by a margin large
     * enough to tell them apart on a small outdoor graphic.
     */
    @Test
    fun brightenedMarkColorsRemainMutuallyDistinguishable() {
        val chromatic = BeeMarkCatalog.colors
            .map { it.value }
            .filterNot { it == "WHITE" || it == "YELLOW" }

        chromatic.forEach { first ->
            chromatic.filterNot { it == first }.forEach { second ->
                val firstColor = markColorValue(first)
                val secondColor = markColorValue(second)
                val luminanceGap = abs(firstColor.luminance() - secondColor.luminance())
                val hueGap = hueDistanceDegrees(firstColor, secondColor)

                assertTrue(
                    "$first and $second look too similar: " +
                        "luminance gap $luminanceGap, hue gap $hueGap",
                    luminanceGap >= 0.05f || hueGap >= 40f,
                )
            }
        }
    }

    /** Yellow must stay clearly distinguishable from the brightened green. */
    @Test
    fun yellowStaysDistantFromTheBrightenedChromaticColors() {
        val yellow = markColorValue("YELLOW")

        BeeMarkCatalog.colors
            .map { it.value }
            .filterNot { it == "YELLOW" }
            .forEach { other ->
                val otherColor = markColorValue(other)
                val luminanceGap = abs(yellow.luminance() - otherColor.luminance())
                val hueGap = hueDistanceDegrees(yellow, otherColor)

                assertTrue(
                    "YELLOW and $other look too similar: " +
                        "luminance gap $luminanceGap, hue gap $hueGap",
                    luminanceGap >= 0.12f || hueGap >= 40f,
                )
            }
    }

    private fun hueDistanceDegrees(first: Color, second: Color): Float {
        val difference = abs(hueDegrees(first) - hueDegrees(second))
        return minOf(difference, 360f - difference)
    }

    private fun hueDegrees(color: Color): Float {
        val red = color.red
        val green = color.green
        val blue = color.blue
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val delta = maximum - minimum
        if (delta == 0f) return 0f
        val hue = when (maximum) {
            red -> 60f * (((green - blue) / delta) % 6f)
            green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }
        return if (hue < 0f) hue + 360f else hue
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
