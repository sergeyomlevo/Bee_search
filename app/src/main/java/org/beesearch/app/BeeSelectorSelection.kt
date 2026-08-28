package org.beesearch.app

import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.MarkPosition

internal object BeeSelectorSelectionLogic {
    fun firstAvailableForColor(
        markColor: String,
        available: Collection<BeeMarkCombination>,
    ): BeeMarkCombination? = BeeMarkCatalog.positions
        .asSequence()
        .map { position -> BeeMarkCombination(markColor, position) }
        .firstOrNull(available::contains)

    fun manualPosition(
        markColor: String,
        markPosition: MarkPosition,
        available: Collection<BeeMarkCombination>,
    ): BeeMarkCombination? = BeeMarkCombination(markColor, markPosition)
        .takeIf(available::contains)

    fun nextAfterAdded(
        added: BeeMarkCombination,
        available: Collection<BeeMarkCombination>,
    ): BeeMarkCombination? {
        val positionIndex = BeeMarkCatalog.positions.indexOf(added.markPosition)
        if (positionIndex >= 0) {
            val followingPositions =
                BeeMarkCatalog.positions.drop(positionIndex + 1) +
                    BeeMarkCatalog.positions.take(positionIndex)
            followingPositions.firstNotNullOfOrNull { position ->
                manualPosition(added.markColor, position, available)
            }?.let { return it }
        }

        val colorIndex = BeeMarkCatalog.colors.indexOfFirst { it.value == added.markColor }
        val followingColors = if (colorIndex >= 0) {
            BeeMarkCatalog.colors.drop(colorIndex + 1) + BeeMarkCatalog.colors.take(colorIndex)
        } else {
            BeeMarkCatalog.colors
        }
        return followingColors.firstNotNullOfOrNull { color ->
            firstAvailableForColor(color.value, available)
        }
    }
}
