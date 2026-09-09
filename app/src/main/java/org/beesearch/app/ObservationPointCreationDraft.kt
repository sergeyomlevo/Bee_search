package org.beesearch.app

import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.model.NewObservationPoint
import java.util.UUID

internal data class ObservationPointCreationDraft(
    val territoryId: UUID,
    val observerId: UUID,
    val originalGps: LocationReading,
    val selectedLatitude: Double = originalGps.latitude,
    val selectedLongitude: Double = originalGps.longitude,
) {
    fun withSelectedMapCenter(mapCenter: MapTarget): ObservationPointCreationDraft = copy(
        selectedLatitude = mapCenter.latitude,
        selectedLongitude = mapCenter.longitude,
    )

    fun toNewObservationPoint(): NewObservationPoint = NewObservationPoint(
        territoryId = territoryId,
        observerId = observerId,
        latitude = selectedLatitude,
        longitude = selectedLongitude,
        gpsLatitude = originalGps.latitude,
        gpsLongitude = originalGps.longitude,
        gpsAccuracyM = originalGps.accuracyMeters,
    )

}

internal data class ObservationPointPreparationDraft(
    val point: NewObservationPoint,
    val isSaving: Boolean = false,
)
