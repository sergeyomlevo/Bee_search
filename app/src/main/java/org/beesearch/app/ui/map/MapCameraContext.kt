package org.beesearch.app.ui.map

import org.maplibre.android.camera.CameraPosition

/** Transient viewport, independent of persisted research filters and navigation commands. */
internal data class MapCameraContext(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
)

internal fun CameraPosition.toMapCameraContext(): MapCameraContext? = target?.let {
    MapCameraContext(it.latitude, it.longitude, zoom, bearing, tilt)
}
