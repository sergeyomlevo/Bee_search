package org.beesearch.app.ui.map

import android.util.Log
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import java.util.Locale
import java.util.UUID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.beesearch.app.MapCenterTarget
import org.beesearch.app.MapGpsMarker
import org.beesearch.app.MapTarget
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.beeSearchLocalForestPmtilesMapProfile
import org.beesearch.app.beeSearchLocalSapunovoPmtilesMapProfile
import org.beesearch.app.beeSearchLocalSapunovoDiagnosticProfile
import org.beesearch.app.beeSearchLocalSapunovoLabelDiagnosticProfile
import org.beesearch.app.BuildConfig
import org.beesearch.app.beeSearchLocalCyclOSMMapProfile
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
    territoryId: UUID?,
    coverageStore: MapCoverageStore,
    locationState: LocationUiState,
    isCreatingObservationPoint: Boolean,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCreateObservationPointAt: (Double, Double) -> Unit,
    onCoverageTerritoryMissing: () -> Unit = {},
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
    var editingTerritoryId by remember { mutableStateOf<UUID?>(null) }
    var persistedCoverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var workingCoverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var coverageLoadedFor by remember { mutableStateOf<UUID?>(null) }
    var coverageLoading by remember { mutableStateOf(false) }
    var clearCoverageConfirmationVisible by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    val initialDeveloperBasemap = if (
        BuildConfig.DEBUG && activity?.intent?.getStringExtra("beeMapDiagnostic") == "label"
    ) {
        DeveloperBasemap.LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC
    } else {
        DeveloperBasemap.ONLINE
    }
    var developerBasemap by remember { mutableStateOf(initialDeveloperBasemap) }
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
    val appContext = LocalContext.current.applicationContext
    val onlineMapProfile = remember { beeSearchFieldMapProfile() }
    val localMapProfile = remember(appContext) { beeSearchLocalCyclOSMMapProfile(appContext) }
    val localVectorProfile = remember(appContext) { beeSearchLocalForestPmtilesMapProfile(appContext) }
    val localSapunovoProfile = remember(appContext) { beeSearchLocalSapunovoPmtilesMapProfile(appContext) }
    val localSapunovoDiagnosticProfile = remember(appContext) { beeSearchLocalSapunovoDiagnosticProfile(appContext) }
    val localSapunovoLabelDiagnosticProfile = remember(appContext) { beeSearchLocalSapunovoLabelDiagnosticProfile(appContext) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(territoryId, coverageStore) {
        if (editingTerritoryId != null && editingTerritoryId != territoryId) {
            coverageSelectionMode = false
            editingTerritoryId = null
            workingCoverage = emptyList()
        }
        coverageLoadedFor = null
        persistedCoverage = emptyList()
        if (territoryId != null) {
            coverageLoading = true
            persistedCoverage = try {
                coverageStore.load(territoryId)
            } catch (_: Exception) {
                emptyList()
            }
            coverageLoadedFor = territoryId
            coverageLoading = false
        } else {
            coverageLoading = false
        }
    }
    val coverageFragments = when {
        coverageSelectionMode -> workingCoverage
        territoryId != null && coverageLoadedFor == territoryId && !coverageLoading -> persistedCoverage
        else -> emptyList()
    }
    BackHandler(enabled = coverageSelectionMode) {
        coverageSelectionMode = false
        editingTerritoryId = null
        workingCoverage = emptyList()
    }

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
                        mapInstance.setMaxZoomPreference(onlineMapProfile.uiMaxZoom)
                        mapInstance.setStyle(Style.Builder().fromJson(onlineMapProfile.styleJson))
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

        if (org.beesearch.app.BuildConfig.DEBUG) {
            MapBasemapDeveloperSwitch(
                mode = developerBasemap,
                onSelectOnline = { developerBasemap = DeveloperBasemap.ONLINE },
                onSelectForest = {
                    Log.d("BeeMap", "selector VECTOR FOREST")
                    developerBasemap = DeveloperBasemap.LOCAL_VECTOR
                },
                onSelectSapunovo = {
                    Log.d("BeeMap", "selector VECTOR SAPUNOVO")
                    developerBasemap = DeveloperBasemap.LOCAL_SAPUNOVO_VECTOR
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .zIndex(3f),
            )
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
                        workingCoverage = addCoverageFragment(
                            fragments = workingCoverage,
                            bounds = MapGeoBounds.fromMapLibre(visibleBounds),
                        )
                    }
                },
                onUndo = {
                    workingCoverage = undoLastCoverageFragment(workingCoverage)
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
                    val id = editingTerritoryId
                    if (id != null) {
                        coroutineScope.launch { coverageStore.replace(id, workingCoverage) }
                        if (id == territoryId) persistedCoverage = workingCoverage
                    }
                    coverageSelectionMode = false
                    editingTerritoryId = null
                },
                onCancel = {
                    coverageSelectionMode = false
                    editingTerritoryId = null
                    workingCoverage = emptyList()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { coverageControlsHeightPx = it.height }
                    .zIndex(3f),
            )
        } else {
            CoverageSelectionEntry(
                onEnter = {
                    if (territoryId == null) {
                        onCoverageTerritoryMissing()
                    } else if (coverageLoading || coverageLoadedFor != territoryId) {
                        // Wait until the current Territory's persisted geometry is loaded.
                    } else {
                        editingTerritoryId = territoryId
                        workingCoverage = persistedCoverage
                        coverageSelectionMode = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).zIndex(3f),
            )
        }

        if (clearCoverageConfirmationVisible) {
            ClearCoverageSelectionDialog(
                onConfirm = {
                    workingCoverage = clearCoverageFragments()
                    clearCoverageConfirmationVisible = false
                },
                onDismiss = { clearCoverageConfirmationVisible = false },
            )
        }
    }

    LaunchedEffect(map, developerBasemap) {
        val mapInstance = map ?: return@LaunchedEffect
        val profile = when (developerBasemap) {
            DeveloperBasemap.ONLINE -> onlineMapProfile
            DeveloperBasemap.LOCAL_RASTER -> localMapProfile
            DeveloperBasemap.LOCAL_VECTOR -> localVectorProfile
            DeveloperBasemap.LOCAL_SAPUNOVO_VECTOR -> localSapunovoProfile
            DeveloperBasemap.LOCAL_SAPUNOVO_DIAGNOSTIC -> localSapunovoDiagnosticProfile
            DeveloperBasemap.LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC -> localSapunovoLabelDiagnosticProfile
        }
        Log.d("BeeMap", "style request mode=$developerBasemap profile=${profile.profileId} path=${profile.datasetVersion} hash=${profile.styleJson.hashCode()}")
        mapInstance.setMaxZoomPreference(profile.uiMaxZoom)
        val fitBounds = when (developerBasemap) {
            DeveloperBasemap.LOCAL_VECTOR -> FOREST_CUTLINES_BOUNDS
            DeveloperBasemap.LOCAL_SAPUNOVO_VECTOR -> SAPUNOVO_FIELDS_WATER_BOUNDS
            else -> null
        }
        mapInstance.setStyle(Style.Builder().fromJson(profile.styleJson)) {
            Log.d("BeeMap", "style loaded profile=${profile.profileId} center=${mapInstance.cameraPosition.target} zoom=${mapInstance.cameraPosition.zoom}")
            fitBounds?.let { bounds ->
                Log.d("BeeMap", "fit bounds profile=${profile.profileId} bounds=$bounds")
                mapInstance.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48))
            }
        }
    }
}

private enum class DeveloperBasemap {
    ONLINE,
    LOCAL_RASTER,
    LOCAL_VECTOR,
    LOCAL_SAPUNOVO_VECTOR,
    LOCAL_SAPUNOVO_DIAGNOSTIC,
    LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC;

    fun next(): DeveloperBasemap = when (this) {
        ONLINE -> LOCAL_RASTER
        LOCAL_RASTER -> LOCAL_VECTOR
        LOCAL_VECTOR -> LOCAL_SAPUNOVO_VECTOR
        LOCAL_SAPUNOVO_VECTOR -> ONLINE
        LOCAL_SAPUNOVO_DIAGNOSTIC -> ONLINE
        LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC -> ONLINE
    }
}

@Composable
private fun MapBasemapDeveloperSwitch(
    mode: DeveloperBasemap,
    onSelectOnline: () -> Unit,
    onSelectForest: () -> Unit,
    onSelectSapunovo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val developerControlFontSize = (14f / LocalDensity.current.fontScale).sp
    var menuExpanded by remember { mutableStateOf(false) }
    val modeLabel = when (mode) {
        DeveloperBasemap.ONLINE -> "ONLINE"
        DeveloperBasemap.LOCAL_RASTER -> "RASTER"
        DeveloperBasemap.LOCAL_VECTOR -> "VECTOR FOREST"
        DeveloperBasemap.LOCAL_SAPUNOVO_VECTOR -> "VECTOR SAPUNOVO"
        DeveloperBasemap.LOCAL_SAPUNOVO_DIAGNOSTIC -> "SAPUNOVO DIAG"
        DeveloperBasemap.LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC -> "SAPUNOVO LABEL DIAG"
    }
    Surface(
        modifier = modifier
            .testTag("map-basemap-developer-switch")
            .semantics {
                contentDescription = when (mode) {
                    DeveloperBasemap.ONLINE -> "Онлайн OSM карта, переключить на локальную CyclOSM"
                    DeveloperBasemap.LOCAL_RASTER -> "Локальная CyclOSM raster карта, переключить на локальный vector PMTiles"
                    DeveloperBasemap.LOCAL_VECTOR -> "Локальная vector PMTiles карта, переключить на онлайн OSM"
                    DeveloperBasemap.LOCAL_SAPUNOVO_VECTOR -> "Локальная vector PMTiles карта Sapunovo, переключить на онлайн OSM"
                    DeveloperBasemap.LOCAL_SAPUNOVO_DIAGNOSTIC -> "Диагностическая локальная vector PMTiles карта Sapunovo"
                    DeveloperBasemap.LOCAL_SAPUNOVO_LABEL_DIAGNOSTIC -> "Скрытая glyph-диагностика Sapunovo"
                }
            },
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text(
                        "$modeLabel ▾",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = developerControlFontSize,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    val itemModifier = Modifier.height(40.dp)
                    val itemPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    DropdownMenuItem(text = { Text("ONLINE", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectOnline() }, contentPadding = itemPadding, modifier = itemModifier)
                    DropdownMenuItem(text = { Text("VECTOR FOREST", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectForest() }, contentPadding = itemPadding, modifier = itemModifier)
                    DropdownMenuItem(text = { Text("VECTOR SAPUNOVO", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectSapunovo() }, contentPadding = itemPadding, modifier = itemModifier)
                }
            }
        }
    }
}

// Descriptor source: tools/map-poc/areas.json (sapunovo-fields-water).
private val SAPUNOVO_FIELDS_WATER_BOUNDS = org.maplibre.android.geometry.LatLngBounds.Builder()
    .include(LatLng(56.0933, 42.6413))
    .include(LatLng(56.1203, 42.6893))
    .build()

private val FOREST_CUTLINES_BOUNDS = org.maplibre.android.geometry.LatLngBounds.Builder()
    .include(LatLng(56.0615, 42.7460))
    .include(LatLng(56.0885, 42.7940))
    .build()

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
