package org.beesearch.app.domain.model

data class MarkColorOption(
    val value: String,
    val displayName: String,
)

data class BeeMarkCombination(
    val markColor: String,
    val markPosition: MarkPosition,
)

object BeeMarkCatalog {
    const val MAX_BEES_PER_OBSERVATION_POINT = 10

    val colors = listOf(
        MarkColorOption("WHITE", "Белая"),
        MarkColorOption("YELLOW", "Жёлтая"),
        MarkColorOption("BLUE", "Синяя"),
        MarkColorOption("RED", "Красная"),
        MarkColorOption("GREEN", "Зелёная"),
    )

    /**
     * Positions offered when marking a new Bee. Legacy [MarkPosition.LEFT_WING]
     * is deliberately absent: it is never a valid new marking, so it never
     * consumes a slot of the available-choice list.
     */
    val positions: List<MarkPosition> = MarkPosition.newBeePositions

    /** 5 colours × 2 real positions = 10 marks. */
    val supportedCombinations: List<BeeMarkCombination>
        get() = colors.flatMap { color ->
            positions.map { position -> BeeMarkCombination(color.value, position) }
        }

    fun availableCombinations(used: Collection<BeeMarkCombination>): List<BeeMarkCombination> =
        supportedCombinations.filterNot(used.toSet()::contains)

    /**
     * Accessibility description of a mark. The position is carried by the shape
     * of the drawn mark, so this text exists for accessibility services and for
     * domain explanation rather than for a visible label.
     */
    fun displayName(markColor: String, markPosition: MarkPosition): String {
        val colorName = colorDisplayName(markColor)
        return "$colorName метка, ${positionDisplayName(markPosition).lowercase()}"
    }

    fun colorDisplayName(markColor: String): String =
        colors.firstOrNull { it.value == markColor }?.displayName ?: markColor

    /**
     * Human-readable position. The legacy value must not claim a real position,
     * because the old `LEFT_WING` records were never confirmed to be either the
     * thorax or the abdomen.
     */
    fun positionDisplayName(markPosition: MarkPosition): String = when (markPosition) {
        MarkPosition.THORAX -> "Грудь"
        MarkPosition.ABDOMEN -> "Брюшко"
        MarkPosition.LEFT_WING -> "Положение не определено"
    }
}
