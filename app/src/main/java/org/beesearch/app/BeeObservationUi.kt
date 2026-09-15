package org.beesearch.app

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.FlightCycle
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal enum class BeeFieldState {
    IN_FLIGHT,
    AT_POINT,
}

internal data class BeeObservationCardModel(
    val bee: Bee,
    val cycles: List<FlightCycle>,
) {
    val latestCycle: FlightCycle? = cycles.maxByOrNull(FlightCycle::sequenceNumber)

    val fieldState: BeeFieldState
        get() = latestCycle?.let { cycle ->
            if (cycle.returnTime == null) BeeFieldState.IN_FLIGHT else BeeFieldState.AT_POINT
        } ?: BeeFieldState.AT_POINT

    val stateStartedAt: Instant?
        get() = latestCycle?.let { cycle -> cycle.returnTime ?: cycle.departureTime }

    /**
     * This is deliberately derived from the latest persisted cycle instead of
     * becoming a separate undo history. It describes only the one correction
     * which can still be reversed without changing an earlier observation.
     */
    val lastReversibleAction: BeeLastReversibleAction?
        get() = latestCycle?.let { cycle ->
            when {
                cycle.returnTime != null -> BeeLastReversibleAction.Return(cycle.id)
                cycle.azimuthDeg != null -> BeeLastReversibleAction.Azimuth(cycle.id)
                cycle.sequenceNumber > 1 -> BeeLastReversibleAction.NextFlight(cycle.id)
                cycle.sequenceNumber == 1 && cycle.isFirstDepartureCancellationEligible ->
                    BeeLastReversibleAction.FirstDeparture(cycle.id)
                else -> null
            }
        }
}

internal sealed interface BeeLastReversibleAction {
    val flightCycleId: UUID

    data class Azimuth(override val flightCycleId: UUID) : BeeLastReversibleAction
    data class Return(override val flightCycleId: UUID) : BeeLastReversibleAction
    data class NextFlight(override val flightCycleId: UUID) : BeeLastReversibleAction
    data class FirstDeparture(override val flightCycleId: UUID) : BeeLastReversibleAction
}

internal fun buildBeeObservationCards(
    bees: List<Bee>,
    flightCycles: List<FlightCycle>,
): List<BeeObservationCardModel> {
    val cyclesByBee = flightCycles.groupBy(FlightCycle::beeId)
    val inputOrder = bees.withIndex().associate { (index, bee) -> bee.id to index }
    return bees.map { bee ->
        BeeObservationCardModel(
            bee = bee,
            cycles = cyclesByBee[bee.id].orEmpty().sortedBy(FlightCycle::sequenceNumber),
        )
    }.sortedWith { first, second ->
        val groupComparison = fieldStateGroup(first.fieldState)
            .compareTo(fieldStateGroup(second.fieldState))
        if (groupComparison != 0) {
            groupComparison
        } else {
            val cycleComparison = if (first.fieldState == BeeFieldState.IN_FLIGHT) {
                compareValues(
                    first.latestCycle?.sequenceNumber,
                    second.latestCycle?.sequenceNumber,
                )
            } else {
                0
            }
            if (cycleComparison != 0) {
                cycleComparison
            } else {
            val durationComparison = when (first.fieldState) {
                BeeFieldState.IN_FLIGHT -> compareValues(
                    second.stateStartedAt,
                    first.stateStartedAt,
                )
                BeeFieldState.AT_POINT -> {
                    val firstStartedAt = first.stateStartedAt
                    val secondStartedAt = second.stateStartedAt
                    when {
                        firstStartedAt == null && secondStartedAt == null -> 0
                        firstStartedAt == null -> 1
                        secondStartedAt == null -> -1
                        else -> firstStartedAt.compareTo(secondStartedAt)
                    }
                }
            }
            if (durationComparison != 0) {
                durationComparison
            } else {
                inputOrder.getValue(first.bee.id).compareTo(inputOrder.getValue(second.bee.id))
            }
            }
        }
    }
}

internal fun availableBeeMarks(bees: List<Bee>): List<BeeMarkCombination> =
    BeeMarkCatalog.availableCombinations(
        bees.map { BeeMarkCombination(it.markColor, it.markPosition) },
    )

private fun fieldStateGroup(state: BeeFieldState): Int = when (state) {
    BeeFieldState.IN_FLIGHT -> 0
    BeeFieldState.AT_POINT -> 1
}

internal fun formatElapsedTime(startedAt: Instant, now: Instant): String {
    val totalSeconds = Duration.between(startedAt, now).seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours == 0L) {
        "%02d:%02d".format(minutes, seconds)
    } else {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    }
}
