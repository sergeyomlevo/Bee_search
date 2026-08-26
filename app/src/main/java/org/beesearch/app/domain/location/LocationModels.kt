package org.beesearch.app.domain.location

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val timestamp: Instant,
)

sealed interface LocationUiState {
    data object PermissionRequired : LocationUiState
    data object WaitingForFix : LocationUiState
    data class Available(val reading: LocationReading) : LocationUiState
    data class Unavailable(val message: String) : LocationUiState
}

class LocationUnavailableException(message: String) : IllegalStateException(message)

interface LocationProvider {
    fun updates(): Flow<LocationReading>
}
