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
    val colors = listOf(
        MarkColorOption("WHITE", "Белая"),
        MarkColorOption("YELLOW", "Жёлтая"),
        MarkColorOption("BLUE", "Синяя"),
        MarkColorOption("RED", "Красная"),
        MarkColorOption("GREEN", "Зелёная"),
    )

    val positions: List<MarkPosition> = MarkPosition.entries

    val supportedCombinations: List<BeeMarkCombination>
        get() = colors.flatMap { color ->
            positions.map { position -> BeeMarkCombination(color.value, position) }
        }

    fun availableCombinations(used: Collection<BeeMarkCombination>): List<BeeMarkCombination> =
        supportedCombinations.filterNot(used.toSet()::contains)

    fun displayName(markColor: String, markPosition: MarkPosition): String {
        val colorName = colors.firstOrNull { it.value == markColor }?.displayName ?: markColor
        return when (markPosition) {
            MarkPosition.NONE -> colorName
            MarkPosition.RIGHT_WING -> "$colorName КП"
            MarkPosition.LEFT_WING -> "$colorName КЛ"
        }
    }

    fun positionDisplayName(markPosition: MarkPosition): String = when (markPosition) {
        MarkPosition.NONE -> "Обычная"
        MarkPosition.RIGHT_WING -> "КП"
        MarkPosition.LEFT_WING -> "КЛ"
    }
}
