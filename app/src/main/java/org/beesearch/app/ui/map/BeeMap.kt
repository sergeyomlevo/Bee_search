package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.beesearch.app.MapCenterTarget
import org.beesearch.app.MapGpsMarker
import org.beesearch.app.MapTarget
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.visibleMapMeasurement
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun BeeMap(
    locationState: LocationUiState,
    isCreatingObservationPoint: Boolean,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCreateObservationPointAt: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewLifecycle = remember { MapViewLifecycleController() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var gpsScreenPosition by remember { mutableStateOf<Offset?>(null) }
    var firstFixCentered by remember { mutableStateOf(false) }
    var initialGpsCenterEstablished by remember { mutableStateOf(false) }
    var mapCenter by remember { mutableStateOf<MapTarget?>(null) }
    var mapZoom by remember { mutableStateOf<Double?>(null) }
    var recenteredUntilNextGesture by remember { mutableStateOf(false) }
    var coverageSelectionMode by remember { mutableStateOf(false) }
    var coverageFragments by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var clearCoverageConfirmationVisible by remember { mutableStateOf(false) }
    var mapCameraRevision by remember { mutableStateOf(0) }
    var coverageControlsHeightPx by remember { mutableStateOf(0) }
    val coverageCameraEdgePaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val normalCameraPadding = remember { normalMapCameraPadding() }
    val reading = (locationState as? LocationUiState.Available)?.reading
    val gpsPosition = reading?.let { MapTarget(it.latitude, it.longitude) }
    val measurement = if (
        !coverageSelectionMode &&
        initialGpsCenterEstablished &&
        !recenteredUntilNextGesture
    ) {
        visibleMapMeasurement(gpsPosition = gpsPosition, mapCenter = mapCenter)
    } else {
        null
    }
    val mapProfile = remember { beeSearchFieldMapProfile() }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(0f),
            factory = {
                MapLibre.getInstance(it)
                MapView(it).also { view ->
                    mapViewLifecycle.attach(view)
                    mapView = view
                    view.onCreate(null)
                    view.getMapAsync { mapInstance ->
                        map = mapInstance
                        mapInstance.setMaxZoomPreference(mapProfile.uiMaxZoom)
                        mapInstance.setStyle(Style.Builder().fromJson(mapProfile.styleJson))
                        mapZoom = mapInstance.cameraPosition.zoom
                    }
                }
            },
            onRelease = { releasedView ->
                mapViewLifecycle.release(releasedView)
            },
        )

        DisposableEffect(lifecycleOwner, mapView) {
            val currentMapView = mapView
            if (currentMapView == null) {
                onDispose { }
            } else {
                val observer = LifecycleEventObserver { _, event ->
                    mapViewLifecycle.onEvent(currentMapView, event)
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }

        LaunchedEffect(reading, map, mapView) {
            val current = reading
            if (current == null) {
                gpsScreenPosition = null
                return@LaunchedEffect
            }
            if (!firstFixCentered) {
                val mapInstance = map ?: return@LaunchedEffect
                firstFixCentered = true
                mapInstance.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        LatLng(current.latitude, current.longitude),
                        15.0,
                    ),
                )
                mapInstance.cameraPosition.target?.let { target ->
                    mapCenter = MapTarget(target.latitude, target.longitude)
                    initialGpsCenterEstablished = true
                }
            }
            gpsScreenPosition = projectedMapPosition(map, mapView, gpsPosition)
        }

        DisposableEffect(map, mapView, gpsPosition) {
            val mapInstance = map
            val currentMapView = mapView
            if (mapInstance == null || currentMapView == null) {
                onDispose { }
            } else {
                val updateMapOverlays = {
                    gpsScreenPosition = projectedMapPosition(mapInstance, currentMapView, gpsPosition)
                    mapCameraRevision += 1
                }
                val moveListener = MapLibreMap.OnCameraMoveListener(updateMapOverlays)
                val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        recenteredUntilNextGesture = false
                    }
                }
                val listener = MapLibreMap.OnCameraIdleListener {
                    mapInstance.cameraPosition.target?.let { target ->
                        mapCenter = MapTarget(target.latitude, target.longitude)
                        if (firstFixCentered) initialGpsCenterEstablished = true
                    }
                    mapZoom = mapInstance.cameraPosition.zoom
                    updateMapOverlays()
                }
                val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateMapOverlays()
                }
                mapInstance.addOnCameraMoveStartedListener(moveStartedListener)
                mapInstance.addOnCameraMoveListener(moveListener)
                mapInstance.addOnCameraIdleListener(listener)
                currentMapView.addOnLayoutChangeListener(layoutListener)
                updateMapOverlays()
                onDispose {
                    mapInstance.removeOnCameraMoveStartedListener(moveStartedListener)
                    mapInstance.removeOnCameraMoveListener(moveListener)
                    mapInstance.removeOnCameraIdleListener(listener)
                    currentMapView.removeOnLayoutChangeListener(layoutListener)
                }
            }
        }

        MapCoverageFragmentsOverlay(
            fragments = coverageFragments,
            map = map,
            cameraRevision = mapCameraRevision,
            modifier = Modifier.fillMaxSize().zIndex(1f),
        )
        if (coverageSelectionMode) {
            MapCoverageViewportFrame(Modifier.fillMaxSize().zIndex(2f))
        }

        gpsScreenPosition?.let { position ->
            MapGpsMarker(screenPosition = position, modifier = Modifier.zIndex(1f))
        }
        if (!coverageSelectionMode) {
            MapCenterTarget(Modifier.align(Alignment.Center).zIndex(2f))
        }

        if (locationPermissionGranted && locationState is LocationUiState.Available) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).zIndex(3f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CompactMapStatus(
                    accuracyMeters = locationState.reading.accuracyMeters,
                    measurement = measurement,
                )
                mapZoom?.let { zoom -> MapZoomIndicator(zoom = zoom) }
            }
        } else {
            mapZoom?.let { zoom ->
                MapZoomIndicator(
                    zoom = zoom,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(3f),
                )
            }
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .zIndex(3f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        !locationPermissionGranted -> Button(onClick = onRequestLocationPermission) {
                            Text("Разрешить доступ к местоположению")
                        }
                        locationState is LocationUiState.Unavailable -> Text(locationState.message)
                        else -> Text("Ожидание GPS…")
                    }
                }
            }
        }

        if (!coverageSelectionMode) {
            MapIdleControls(
                canRecenter = reading != null,
                canCreateObservationPoint = reading != null &&
                    mapCenter != null &&
                    initialGpsCenterEstablished &&
                    !isCreatingObservationPoint,
                onRecenter = {
                    reading?.let { current ->
                        map?.let { mapInstance ->
                            recenteredUntilNextGesture = true
                            mapInstance.animateCamera(
                                CameraUpdateFactory.newLatLng(
                                    LatLng(current.latitude, current.longitude),
                                ),
                            )
                        }
                    }
                },
                onCreateObservationPoint = {
                    map?.cameraPosition?.target?.let { target ->
                        onCreateObservationPointAt(target.latitude, target.longitude)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(3f),
            )
        }

        if (coverageSelectionMode) {
            MapCoverageSelectionControls(
                fragmentCount = coverageFragments.size,
                canAddFragment = map != null,
                onAddFragment = {
                    map?.projection?.visibleRegion?.latLngBounds?.let { visibleBounds ->
                        coverageFragments = addCoverageFragment(
                            fragments = coverageFragments,
                            bounds = MapGeoBounds.fromMapLibre(visibleBounds),
                        )
                    }
                },
                onUndo = {
                    coverageFragments = undoLastCoverageFragment(coverageFragments)
                },
                onShowAll = {
                    coverageBoundsForShowAll(coverageFragments)?.let { bounds ->
                        val reviewPadding = coverageReviewCameraPadding(
                            controlsHeightPx = coverageControlsHeightPx,
                            edgePaddingPx = coverageCameraEdgePaddingPx,
                        )
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds.toLatLngBounds(),
                                reviewPadding.left,
                                reviewPadding.top,
                                reviewPadding.right,
                                reviewPadding.bottom,
                            ),
                        )
                    }
                },
                onClear = { clearCoverageConfirmationVisible = true },
                onDone = {
                    // MapLibre retains the padding passed to newLatLngBounds. It is valid for
                    // coverage review, but would otherwise shift the normal map camera center.
                    map?.restoreNormalCameraPadding(normalCameraPadding)
                    coverageSelectionMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { coverageControlsHeightPx = it.height }
                    .zIndex(3f),
            )
        } else {
            CoverageSelectionEntry(
                onEnter = { coverageSelectionMode = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).zIndex(3f),
            )
        }

        if (clearCoverageConfirmationVisible) {
            ClearCoverageSelectionDialog(
                onConfirm = {
                    coverageFragments = clearCoverageFragments()
                    clearCoverageConfirmationVisible = false
                },
                onDismiss = { clearCoverageConfirmationVisible = false },
            )
        }
    }
}

private fun MapLibreMap.restoreNormalCameraPadding(padding: MapCameraPadding) {
    cameraPosition = CameraPosition.Builder(cameraPosition)
        .padding(
            padding.left.toDouble(),
            padding.top.toDouble(),
            padding.right.toDouble(),
            padding.bottom.toDouble(),
        )
        .build()
}

internal const val MAP_ZOOM_INDICATOR_TAG = "map-zoom-indicator"

@Composable
internal fun MapZoomIndicator(
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    val formattedZoom = formatMapZoom(zoom)
    androidx.compose.material3.Surface(
        modifier = modifier
            .semantics { contentDescription = "Текущий масштаб карты $formattedZoom" }
            .testTag(MAP_ZOOM_INDICATOR_TAG),
        shape = androidx.compose.material3.MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = formattedZoom,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

internal fun formatMapZoom(zoom: Double): String {
    require(zoom.isFinite())
    return String.format(Locale.ROOT, "z %.1f", zoom)
}

private fun projectedMapPosition(
    map: MapLibreMap?,
    mapView: MapView?,
    target: MapTarget?,
): Offset? {
    if (map == null || mapView == null || target == null || mapView.width <= 0 || mapView.height <= 0) return null
    val point = map.projection.toScreenLocation(LatLng(target.latitude, target.longitude))
    return Offset(point.x, point.y).takeIf {
        it.x in 0f..mapView.width.toFloat() && it.y in 0f..mapView.height.toFloat()
    }
}

private class MapViewLifecycleController {
    private var mapView: MapView? = null
    private var started = false
    private var resumed = false

    fun attach(view: MapView) {
        check(mapView == null || mapView === view) { "A MapView is already attached" }
        mapView = view
        started = false
        resumed = false
    }

    fun onEvent(view: MapView, event: Lifecycle.Event) {
        if (mapView !== view) return
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) {
                view.onStart()
                started = true
            }
            Lifecycle.Event.ON_RESUME -> if (!resumed) {
                view.onResume()
                resumed = true
            }
            Lifecycle.Event.ON_PAUSE -> pauseIfNeeded(view)
            Lifecycle.Event.ON_STOP -> stopIfNeeded(view)
            Lifecycle.Event.ON_DESTROY -> release(view)
            else -> Unit
        }
    }

    fun release(view: MapView) {
        if (mapView !== view) return
        pauseIfNeeded(view)
        stopIfNeeded(view)
        view.onDestroy()
        mapView = null
    }

    private fun pauseIfNeeded(view: MapView) {
        if (!resumed) return
        view.onPause()
        resumed = false
    }

    private fun stopIfNeeded(view: MapView) {
        pauseIfNeeded(view)
        if (!started) return
        view.onStop()
        started = false
    }
}
