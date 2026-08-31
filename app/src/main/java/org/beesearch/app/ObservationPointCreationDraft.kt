package org.beesearch.app

import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.model.NewObservationPoint
import java.util.UUID

internal data class ObservationPointCreationDraft(
    val territoryId: UUID,
    val originalGps: LocationReading,
    val selectedLatitude: Double = originalGps.latitude,
    val selectedLongitude: Double = originalGps.longitude,
    val observerCodeInput: String = "",
    val isSaving: Boolean = false,
) {
    fun toNewObservationPoint(): NewObservationPoint = NewObservationPoint(
        territoryId = territoryId,
        latitude = selectedLatitude,
        longitude = selectedLongitude,
        gpsLatitude = originalGps.latitude,
        gpsLongitude = originalGps.longitude,
        gpsAccuracyM = originalGps.accuracyMeters,
    )

    companion object {
        fun fromMapCenter(
            territoryId: UUID,
            originalGps: LocationReading,
            mapCenter: MapTarget,
            observerCodeInput: String,
        ): ObservationPointCreationDraft = ObservationPointCreationDraft(
            territoryId = territoryId,
            originalGps = originalGps,
            selectedLatitude = mapCenter.latitude,
            selectedLongitude = mapCenter.longitude,
            observerCodeInput = observerCodeInput,
        )
    }
}
