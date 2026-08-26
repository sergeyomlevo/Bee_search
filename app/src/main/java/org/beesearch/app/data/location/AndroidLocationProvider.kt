package org.beesearch.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.beesearch.app.domain.location.LocationProvider
import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.location.LocationUnavailableException
import java.time.Instant

internal class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override fun updates(): Flow<LocationReading> = callbackFlow {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            throw LocationUnavailableException("Разрешение на местоположение не предоставлено")
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            throw LocationUnavailableException("Служба местоположения отключена")
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toReading())
            }
        }

        providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }?.let { trySend(it.toReading()) }

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                2_000L,
                1f,
                listener,
                Looper.getMainLooper(),
            )
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun Location.toReading() = LocationReading(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.toDouble(),
        timestamp = Instant.ofEpochMilli(time),
    )
}
