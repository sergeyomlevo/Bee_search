package org.beesearch.app.data.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import org.beesearch.app.domain.location.LocationProvider
import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.location.LocationUnavailableException

internal class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext

    override fun updates(): Flow<LocationUiState> = flow {
        // Each collection owns its receiver and listeners; cancellation cannot remove another session.
        emitAll(locationTrackingUpdates(AndroidTrackingPlatform(appContext)))
    }.flowOn(Dispatchers.Main.immediate)
}

private class AndroidTrackingPlatform(private val context: Context) : LocationTrackingPlatform {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val listeners = mutableListOf<LocationListener>()
    private var receiver: BroadcastReceiver? = null

    override fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    override fun enabledProviders(): Set<String> = if (!manager.isLocationEnabled) emptySet() else {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }.toSet()
    }

    override fun registerStateChanges(onChange: () -> Unit) {
        val registeredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.MODE_CHANGED_ACTION ||
                    intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION
                ) onChange()
            }
        }
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        // System notifications; never trust extras, always re-query LocationManager.
        ContextCompat.registerReceiver(context, registeredReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = registeredReceiver
    }

    override fun unregisterStateChanges() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    override fun requestUpdates(provider: String, onLocation: (LocationReading) -> Unit) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(LocationReading(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.toDouble(),
                    timestamp = Instant.ofEpochMilli(location.time),
                ))
            }
        }
        // Track even a partially registered request so exception/cancellation cleanup is complete.
        listeners += listener
        try {
            manager.requestLocationUpdates(provider, 2_000L, 1f, listener, Looper.getMainLooper())
        } catch (error: SecurityException) {
            // Permission can be revoked between reconciliation and the platform call.
            throw LocationUnavailableException("Разрешение на местоположение не предоставлено")
        }
    }

    override fun removeUpdates() {
        listeners.forEach(manager::removeUpdates)
        listeners.clear()
    }
}
