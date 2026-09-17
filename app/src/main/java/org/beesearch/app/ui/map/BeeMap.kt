package org.beesearch.app.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
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
import org.beesearch.app.ObservationPointCreationDraft
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.beeSearchActivePmtilesMapProfile
import org.beesearch.app.BuildConfig
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.visibleMapMeasurement
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

internal enum class BeeMapMode {
    FIELD,
    POINT_BROWSER,
}

@Composable
internal fun BeeMap(
    territoryId: UUID?,
    coverageStore: MapCoverageStore,
    packageStore: MapPackageStore,
    locationState: LocationUiState,
    observationPointDraft: ObservationPointCreationDraft?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartObservationPointCreation: () -> Unit,
    onConfirmObservationPointCreation: (Double, Double) -> Unit,
    onCancelObservationPointCreation: () -> Unit,
    onCoverageTerritoryMissing: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    coverageEditNonce: Int = 0,
    mode: BeeMapMode = BeeMapMode.FIELD,
    savedObservationPoints: List<ObservationPointSummary> = emptyList(),
    onSelectSavedObservationPoint: (UUID) -> Unit = {},
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
    var coverageViewportBounds by remember { mutableStateOf<MapGeoBounds?>(null) }
    var packageAvailability by remember { mutableStateOf<MapPackageAvailability>(MapPackageAvailability.Missing) }
    var clearSelectionConfirmationVisible by remember { mutableStateOf(false) }
    var developerBasemap by remember { mutableStateOf(DeveloperBasemap.ONLINE) }
    var mapCameraRevision by remember { mutableStateOf(0) }
    var coverageControlsHeightPx by remember { mutableStateOf(0) }
    val coverageCameraEdgePaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val normalCameraPadding = remember { normalMapCameraPadding() }
    val reading = (locationState as? LocationUiState.Available)?.reading
    val gpsPosition = reading?.let { MapTarget(it.latitude, it.longitude) }
    val isCreatingObservationPoint = observationPointDraft != null
    val coverageSelectionActive = coverageSelectionMode
    val measurement = if (
        !coverageSelectionActive &&
        initialGpsCenterEstablished &&
        !recenteredUntilNextGesture
    ) {
        visibleMapMeasurement(gpsPosition = gpsPosition, mapCenter = mapCenter)
    } else {
        null
    }
    val appContext = LocalContext.current.applicationContext
    val latestTerritoryId by rememberUpdatedState(territoryId)
    val onlineMapProfile = remember { beeSearchFieldMapProfile() }
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
    LaunchedEffect(territoryId, persistedCoverage, coverageLoadedFor, coverageLoading, packageStore) {
        if (territoryId == null || coverageLoading || coverageLoadedFor != territoryId) {
            packageAvailability = MapPackageAvailability.Missing
        } else {
            packageAvailability = packageStore.loadActive(territoryId, persistedCoverage)
            if (developerBasemap == DeveloperBasemap.ACTIVE_VECTOR && packageAvailability !is MapPackageAvailability.Ready) {
                developerBasemap = DeveloperBasemap.ONLINE
            }
        }
    }
    // External request (Settings → Офлайн-карты → «Изменить участок») opens the
    // spatial coverage-selection mode for the current territory.
    LaunchedEffect(coverageEditNonce, territoryId, coverageLoadedFor, coverageLoading) {
        if (
            coverageEditNonce > 0 &&
            territoryId != null &&
            !coverageLoading &&
            coverageLoadedFor == territoryId
        ) {
            editingTerritoryId = territoryId
            workingCoverage = persistedCoverage
            coverageSelectionMode = true
        }
    }
    val coverageFragments = when {
        coverageSelectionMode -> workingCoverage
        territoryId != null && coverageLoadedFor == territoryId && !coverageLoading -> persistedCoverage
        else -> emptyList()
    }
    val coverageViewportSummary = if (coverageSelectionMode) {
        coverageViewportBounds?.let(::coverageBoundsSummary)
    } else {
        null
    }
    val selectedCoverageSummary = if (coverageSelectionMode && workingCoverage.size == 1) {
        coverageBoundsSummary(workingCoverage.single().bounds)
    } else {
        null
    }
    val activeMapPackage = (packageAvailability as? MapPackageAvailability.Ready)?.activePackage
    val activeVectorProfile = activeMapPackage?.let(::beeSearchActivePmtilesMapProfile)
    BackHandler(enabled = mode == BeeMapMode.FIELD && coverageSelectionActive) {
        coverageSelectionMode = false
        editingTerritoryId = null
        workingCoverage = emptyList()
    }
    BackHandler(enabled = mode == BeeMapMode.FIELD && isCreatingObservationPoint && !coverageSelectionActive) {
        onCancelObservationPointCreation()
    }

    LaunchedEffect(coverageSelectionMode, map) {
        coverageViewportBounds = if (coverageSelectionMode) {
            map?.projection?.visibleRegion?.latLngBounds?.let(MapGeoBounds::fromMapLibre)
        } else {
            null
        }
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

        LaunchedEffect(reading, map, mapView, mode) {
            if (mode != BeeMapMode.FIELD) {
                gpsScreenPosition = null
                return@LaunchedEffect
            }
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

        LaunchedEffect(map, mode, savedObservationPoints.map(ObservationPointSummary::id)) {
            if (mode != BeeMapMode.POINT_BROWSER || savedObservationPoints.isEmpty()) {
                return@LaunchedEffect
            }
            val mapInstance = map ?: return@LaunchedEffect
            val north = savedObservationPoints.maxOf(ObservationPointSummary::latitude)
            val east = savedObservationPoints.maxOf(ObservationPointSummary::longitude)
            val south = savedObservationPoints.minOf(ObservationPointSummary::latitude)
            val west = savedObservationPoints.minOf(ObservationPointSummary::longitude)
            if (north == south && east == west) {
                mapInstance.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(north, east), 15.0),
                )
            } else {
                mapInstance.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        MapGeoBounds(north = north, east = east, south = south, west = west)
                            .toLatLngBounds(),
                        80,
                    ),
                )
            }
        }

        LaunchedEffect(map, observationPointDraft?.originalGps) {
            val originalGps = observationPointDraft?.originalGps ?: return@LaunchedEffect
            val mapInstance = map ?: return@LaunchedEffect
            recenteredUntilNextGesture = true
            mapInstance.animateCamera(
                CameraUpdateFactory.newLatLng(
                    LatLng(originalGps.latitude, originalGps.longitude),
                ),
            )
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
                    if (coverageSelectionMode) {
                        coverageViewportBounds = MapGeoBounds.fromMapLibre(
                            mapInstance.projection.visibleRegion.latLngBounds,
                        )
                    }
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

        if (coverageSelectionActive) {
            MapCoverageFragmentsOverlay(
                fragments = coverageFragments,
                map = map,
                cameraRevision = mapCameraRevision,
                modifier = Modifier.fillMaxSize().zIndex(1f),
            )
            MapCoverageViewportFrame(Modifier.fillMaxSize().zIndex(2f))
        }

        if (
            developerBasemap == DeveloperBasemap.ONLINE ||
            developerBasemap == DeveloperBasemap.ACTIVE_VECTOR
        ) {
            MapBasemapSourceSelector(
                vectorMapSelected = developerBasemap == DeveloperBasemap.ACTIVE_VECTOR,
                onSelectOnline = { developerBasemap = DeveloperBasemap.ONLINE },
                onSelectVectorMap = {
                    if (packageAvailability is MapPackageAvailability.Ready) {
                        developerBasemap = DeveloperBasemap.ACTIVE_VECTOR
                    } else {
                        onOpenOfflineMaps()
                    }
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
        if (mode == BeeMapMode.FIELD && !coverageSelectionActive) {
            MapCenterTarget(Modifier.align(Alignment.Center).zIndex(2f))
        }

        if (mode == BeeMapMode.POINT_BROWSER) {
            mapZoom?.let { zoom ->
                MapZoomIndicator(
                    zoom = zoom,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(3f),
                )
            }
        } else if (locationPermissionGranted && locationState is LocationUiState.Available) {
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

        if (mode == BeeMapMode.FIELD && !coverageSelectionActive) {
            if (observationPointDraft == null) {
                MapIdleControls(
                    canRecenter = reading != null,
                    canCreateObservationPoint = reading != null &&
                        mapCenter != null &&
                        initialGpsCenterEstablished,
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
                    onCreateObservationPoint = onStartObservationPointCreation,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(3f),
                )
            } else {
                MapCreationControls(
                    canRecenter = map != null,
                    canConfirm = map?.cameraPosition?.target != null,
                    isSaving = false,
                    onRecenter = {
                        map?.let { mapInstance ->
                            val originalGps = observationPointDraft.originalGps
                            recenteredUntilNextGesture = true
                            mapInstance.animateCamera(
                                CameraUpdateFactory.newLatLng(
                                    LatLng(originalGps.latitude, originalGps.longitude),
                                ),
                            )
                        }
                    },
                    onConfirm = {
                        map?.cameraPosition?.target?.let { target ->
                            onConfirmObservationPointCreation(target.latitude, target.longitude)
                        }
                    },
                    onCancel = onCancelObservationPointCreation,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(3f),
                )
            }
        }

        if (mode == BeeMapMode.FIELD && coverageSelectionActive) {
            MapCoverageSelectionControls(
                fragmentCount = coverageFragments.size,
                viewportSummary = coverageViewportSummary,
                selectedSummary = selectedCoverageSummary,
                showDevBoundsExport = BuildConfig.DEBUG,
                title = "Участок",
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
                onClear = { clearSelectionConfirmationVisible = true },
                onDone = {
                    val id = editingTerritoryId
                    if (id != null) {
                        val selectedCoverage = workingCoverage
                        coroutineScope.launch {
                            try {
                                coverageStore.replace(id, selectedCoverage)
                                if (id == latestTerritoryId) persistedCoverage = selectedCoverage
                                map?.restoreNormalCameraPadding(normalCameraPadding)
                                coverageSelectionMode = false
                                editingTerritoryId = null
                            } catch (_: Exception) {
                                Toast.makeText(
                                    appContext,
                                    "Не удалось сохранить участок",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
                onCopySelectedBounds = {
                    workingCoverage.singleOrNull()?.let { selected ->
                        val text = formatMapPackageBuilderBounds(selected.bounds)
                        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Bee Search map bbox", text))
                        Toast.makeText(appContext, "Bbox скопирован", Toast.LENGTH_SHORT).show()
                    }
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
        } else if (mode == BeeMapMode.FIELD && !isCreatingObservationPoint && territoryId != null) {
            CoverageSelectionEntry(
                onEnter = {
                    if (!coverageLoading && coverageLoadedFor == territoryId) {
                        editingTerritoryId = territoryId
                        workingCoverage = persistedCoverage
                        coverageSelectionMode = true
                    } else {
                        onCoverageTerritoryMissing()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .zIndex(3f),
            )
        }

        if (clearSelectionConfirmationVisible) {
            ClearCoverageSelectionDialog(
                onConfirm = {
                    workingCoverage = clearCoverageFragments()
                    clearSelectionConfirmationVisible = false
                },
                onDismiss = { clearSelectionConfirmationVisible = false },
            )
        }


        if (mode == BeeMapMode.POINT_BROWSER) {
            SavedObservationPointMarkersOverlay(
                points = savedObservationPoints,
                map = map,
                mapView = mapView,
                cameraRevision = mapCameraRevision,
                onSelectPoint = onSelectSavedObservationPoint,
                modifier = Modifier.fillMaxSize().zIndex(2f),
            )
        }
    }

    LaunchedEffect(map, developerBasemap, activeMapPackage?.pmtilesFile?.absolutePath) {
        val mapInstance = map ?: return@LaunchedEffect
        val profile = when (developerBasemap) {
            DeveloperBasemap.ONLINE -> onlineMapProfile
            DeveloperBasemap.ACTIVE_VECTOR -> activeVectorProfile ?: onlineMapProfile
        }
        Log.d("BeeMap", "style request mode=$developerBasemap profile=${profile.profileId} path=${profile.datasetVersion} hash=${profile.styleJson.hashCode()}")
        mapInstance.setMaxZoomPreference(profile.uiMaxZoom)
        mapInstance.setStyle(Style.Builder().fromJson(profile.styleJson)) {
            Log.d("BeeMap", "style loaded profile=${profile.profileId} center=${mapInstance.cameraPosition.target} zoom=${mapInstance.cameraPosition.zoom}")
        }
    }
}

private enum class DeveloperBasemap {
    ONLINE,
    ACTIVE_VECTOR,
}

@Composable
private fun MapBasemapSourceSelector(
    vectorMapSelected: Boolean,
    onSelectOnline: () -> Unit,
    onSelectVectorMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val developerControlFontSize = (14f / LocalDensity.current.fontScale).sp
    var menuExpanded by remember { mutableStateOf(false) }
    val modeLabel = if (vectorMapSelected) "Векторная карта" else "Онлайн карта"
    Surface(
        modifier = modifier
            .testTag("map-basemap-source-selector")
            .semantics {
                contentDescription = "Источник карты: $modeLabel"
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
                    DropdownMenuItem(text = { Text("Онлайн карта", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectOnline() }, contentPadding = itemPadding, modifier = itemModifier)
                    DropdownMenuItem(text = { Text("Векторная карта", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectVectorMap() }, contentPadding = itemPadding, modifier = itemModifier)
                }
            }
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
