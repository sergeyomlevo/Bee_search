package org.beesearch.app

import kotlinx.coroutines.flow.first
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.repository.ObservationRepository

/** Test-data adapters for older persistence scenarios; not part of production workflow. */
internal suspend fun ObservationRepository.createObservationPointWithFirstBee(
    point: NewObservationPoint,
    markColor: String,
    markPosition: MarkPosition,
): ObservationPoint {
    val created = createObservationPoint(point)
    startFirstFlight(created.id, markColor, markPosition)
    return observeActivePoint().first() ?: error("Active point disappeared")
}

internal suspend fun ObservationRepository.addBee(
    pointId: java.util.UUID,
    markColor: String,
    markPosition: MarkPosition,
): Bee = startFirstFlight(pointId, markColor, markPosition).bee

internal suspend fun ObservationRepository.startInitialGroupRelease(
    pointId: java.util.UUID,
): List<FlightCycle> = observeFlightCyclesForPoint(pointId).first()
    .filter { it.sequenceNumber == 1 }

internal suspend fun ObservationRepository.removePreparedBee(beeId: java.util.UUID) {
    undoLastBeeAction(beeId)
}
