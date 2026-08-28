package org.beesearch.app

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import java.time.Duration
import java.time.Instant

internal enum class ActivePointWorkflowPhase {
    PREPARATION,
    OBSERVATION,
}

internal enum class BeeFieldState {
    IN_FLIGHT,
    AT_POINT,
}

internal data class BeeObservationCardModel(
    val bee: Bee,
    val cycles: List<FlightCycle>,
) {
    val latestCycle: FlightCycle? = cycles.maxByOrNull(FlightCycle::sequenceNumber)

    val fieldState: BeeFieldState?
        get() = latestCycle?.let { cycle ->
            if (cycle.returnTime == null) BeeFieldState.IN_FLIGHT else BeeFieldState.AT_POINT
        }

    val stateStartedAt: Instant?
        get() = latestCycle?.let { cycle -> cycle.returnTime ?: cycle.departureTime }
}

internal fun activePointWorkflowPhase(hasFlightCycles: Boolean): ActivePointWorkflowPhase =
    if (hasFlightCycles) {
        ActivePointWorkflowPhase.OBSERVATION
    } else {
        ActivePointWorkflowPhase.PREPARATION
    }

internal fun buildBeeObservationCards(
    bees: List<Bee>,
    flightCycles: List<FlightCycle>,
): List<BeeObservationCardModel> {
    val cyclesByBee = flightCycles.groupBy(FlightCycle::beeId)
    return bees.map { bee ->
        BeeObservationCardModel(
            bee = bee,
            cycles = cyclesByBee[bee.id].orEmpty().sortedBy(FlightCycle::sequenceNumber),
        )
    }
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
