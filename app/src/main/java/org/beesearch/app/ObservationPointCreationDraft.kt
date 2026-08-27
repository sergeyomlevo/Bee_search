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
    val gpsRecenterRequestId: Long = 0,
    val isSaving: Boolean = false,
) {
    fun withSelectedCoordinates(latitude: Double, longitude: Double): ObservationPointCreationDraft = copy(
        selectedLatitude = latitude,
        selectedLongitude = longitude,
    )

    fun requestGpsRecenter(): ObservationPointCreationDraft = copy(
        gpsRecenterRequestId = gpsRecenterRequestId + 1,
    )

    fun toNewObservationPoint(): NewObservationPoint = NewObservationPoint(
        territoryId = territoryId,
        latitude = selectedLatitude,
        longitude = selectedLongitude,
        gpsLatitude = originalGps.latitude,
        gpsLongitude = originalGps.longitude,
        gpsAccuracyM = originalGps.accuracyMeters,
    )
}
