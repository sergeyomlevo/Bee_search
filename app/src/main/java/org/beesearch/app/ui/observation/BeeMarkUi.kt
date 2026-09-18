package org.beesearch.app.ui.observation

import androidx.compose.ui.graphics.Color
import org.beesearch.app.domain.model.MarkPosition

/** Body parts of the drawn Bee mark, from head to abdomen. */
internal enum class BeeBodySegment {
    HEAD,
    THORAX,
    ABDOMEN,
}

/**
 * The one body segment that carries the mark colour. `null` means no confirmed
 * position exists for the value, which is the case only for the legacy
 * `LEFT_WING` records.
 */
internal fun beeMarkMarkedSegment(markPosition: MarkPosition): BeeBodySegment? = when (markPosition) {
    MarkPosition.THORAX -> BeeBodySegment.THORAX
    MarkPosition.ABDOMEN -> BeeBodySegment.ABDOMEN
    MarkPosition.LEFT_WING -> null
}

/** Dark body colour used for the unmarked parts and for every outline. */
internal val BeeMarkBodyDark = Color(0xFF241C17)

/** Dark outline colour. It is what keeps a WHITE mark visible on a light card. */
internal val BeeMarkOutline = Color(0xFF2B211D)

internal data class BeeMarkSegmentColorSet(
    val head: Color,
    val thorax: Color,
    val abdomen: Color,
)

/**
 * Resolves the fill of every body segment. The mark colour lands on the thorax
 * for [MarkPosition.THORAX] and on the abdomen for [MarkPosition.ABDOMEN]; the
 * remaining segments stay dark so the position is readable from the shape alone.
 */
internal fun beeMarkSegmentColors(
    markColor: String,
    markPosition: MarkPosition,
): BeeMarkSegmentColorSet {
    val mark = markColorValue(markColor)
    return when (beeMarkMarkedSegment(markPosition)) {
        BeeBodySegment.THORAX -> BeeMarkSegmentColorSet(
            head = BeeMarkBodyDark,
            thorax = mark,
            abdomen = BeeMarkBodyDark,
        )
        BeeBodySegment.ABDOMEN -> BeeMarkSegmentColorSet(
            head = BeeMarkBodyDark,
            thorax = BeeMarkBodyDark,
            abdomen = mark,
        )
        // The head never carries a mark, and a legacy value has no confirmed
        // position at all, so both cases stay a fully dark body.
        BeeBodySegment.HEAD, null -> BeeMarkSegmentColorSet(
            head = BeeMarkBodyDark,
            thorax = BeeMarkBodyDark,
            abdomen = BeeMarkBodyDark,
        )
    }
}

/**
 * Mark colours stay semantically WHITE, YELLOW, BLUE, RED and GREEN. WHITE is
 * not darkened to gain visibility; the icon relies on its outline instead.
 *
 * The chromatic colours are deliberately bright and fully saturated so a mark
 * stays identifiable in direct sunlight. A dark tone does not merely look dull
 * outdoors: its luminance approaches the dark body colour, so the filled
 * segment stops separating from the rest of the bee. Every colour here keeps a
 * luminance of at least 0.22 and a contrast ratio of at least 4.0 against
 * [BeeMarkOutline].
 */
internal fun markColorValue(markColor: String): Color = when (markColor) {
    "WHITE" -> Color.White
    "YELLOW" -> Color(0xFFFFD54F)
    "BLUE" -> Color(0xFF0A84FF)
    "RED" -> Color(0xFFFF3B30)
    "GREEN" -> Color(0xFF4CAF50)
    else -> Color.Gray
}
