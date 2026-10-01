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

/**
 * Restarting foreground map tracking must not make a still-useful fix look lost while the
 * platform is waiting to deliver the next callback.
 */
internal fun LocationUiState.awaitingNextFix(preserveAvailable: Boolean): LocationUiState =
    if (preserveAvailable && this is LocationUiState.Available) this else LocationUiState.WaitingForFix

class LocationUnavailableException(message: String) : IllegalStateException(message)

interface LocationProvider {
    fun updates(): Flow<LocationReading>
}
