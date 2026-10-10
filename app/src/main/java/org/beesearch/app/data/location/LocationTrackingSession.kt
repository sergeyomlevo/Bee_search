package org.beesearch.app.data.location

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.location.LocationUiState

/** Platform operations for one tracking collection, serialized on Android's main looper. */
internal interface LocationTrackingPlatform {
    fun hasPermission(): Boolean
    fun enabledProviders(): Set<String>
    fun registerStateChanges(onChange: () -> Unit)
    fun unregisterStateChanges()
    fun requestUpdates(provider: String, onLocation: (LocationReading) -> Unit)
    fun removeUpdates()
}

/** OFF is a recoverable state, not the end of the foreground tracking session. */
internal fun locationTrackingUpdates(platform: LocationTrackingPlatform): Flow<LocationUiState> = callbackFlow {
    var receiverRegistered = false
    var stopped = false
    var providers: Set<String>? = null
    var generation = 0

    fun reconcile() {
        if (stopped) return
        try {
            if (!platform.hasPermission()) {
                generation++
                platform.removeUpdates()
                providers = emptySet()
                trySend(LocationUiState.PermissionRequired)
                return
            }
            val enabled = platform.enabledProviders()
            if (enabled == providers) return
            generation++
            platform.removeUpdates()
            providers = enabled
            if (enabled.isEmpty()) {
                trySend(LocationUiState.Unavailable("Служба местоположения отключена"))
            } else {
                trySend(LocationUiState.WaitingForFix)
                val requestGeneration = generation
                enabled.forEach { provider ->
                    platform.requestUpdates(provider) { reading ->
                        // Discard queued callbacks from removed requests and a just-disabled provider.
                        if (!stopped && requestGeneration == generation &&
                            platform.hasPermission() && provider in platform.enabledProviders()
                        ) {
                            trySend(LocationUiState.Available(reading))
                        }
                    }
                }
            }
        } catch (error: Exception) {
            close(error)
        }
    }

    try {
        if (!platform.hasPermission()) {
            trySend(LocationUiState.PermissionRequired)
            close()
        } else {
            // Subscribe before querying, so OFF→ON cannot fall into a registration gap.
            platform.registerStateChanges(::reconcile)
            receiverRegistered = true
            reconcile()
        }
        awaitClose { }
    } finally {
        stopped = true
        generation++
        try {
            platform.removeUpdates()
        } finally {
            if (receiverRegistered) platform.unregisterStateChanges()
        }
    }
}
