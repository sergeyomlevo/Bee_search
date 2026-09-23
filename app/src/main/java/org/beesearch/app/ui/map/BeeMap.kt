package org.beesearch.app.ui.map

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
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.beeSearchActivePmtilesMapProfile
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

    /**
     * Read-only Ареал review: the saved участки on a clean map.
     *
     * It exists so that looking at the Ареал is not the same screen as editing it: no editor panel,
     * no center target and no record controls, only the saved geometry, the normal map controls and
     * one small action that switches to the editor.
     */
    AREA_VIEW,
}

@Composable
internal fun BeeMap(
    territoryId: UUID?,
    territoryName: String?,
    areaStore: MapAreaStore,
    packageStore: MapPackageStore,
    locationState: LocationUiState,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestCreateRecord: (Double, Double) -> Unit,
    onCoverageTerritoryMissing: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    /**
     * The pending request to open the участки editor; [AreaEditorRequest.NO_REQUEST] means nothing is
     * pending.
     *
     * Only an explicit user action produces a request. It is consumed through
     * [onAreaEditorRequestHandled] as soon as the editor opens, so a later visit to the map cannot
     * replay an older request and open the editor on its own.
     */
    areaEditorRequest: Int = AreaEditorRequest.NO_REQUEST,
    /** Reports that the pending request was handled, so it cannot be replayed. */
    onAreaEditorRequestHandled: () -> Unit = {},
    mode: BeeMapMode = BeeMapMode.FIELD,
    /** Saved objects drawn in the browser mode. ObservationPoint is the only kind today. */
    savedObjectMarkers: List<MapObjectMarker> = emptyList(),
    onSelectSavedObject: (MapObjectMarker) -> Unit = {},
    /** Leaves the Ареал view mode; the host decides which screen that means. */
    onExitAreaView: () -> Unit = {},
    /** Opens the участки editor from the Ареал view mode. */
    onEditAreaSections: () -> Unit = {},
    /** The участки editor session ended, however it ended, and the host may restore its route. */
    onCoverageSessionEnded: () -> Unit = {},
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
    var coverageFragmentEditorVisible by remember { mutableStateOf(false) }
    var editingTerritoryId by remember { mutableStateOf<UUID?>(null) }
    var persistedArea by remember { mutableStateOf<MapAreaReadResult>(MapAreaReadResult.Absent) }
    var persistedCoverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var workingCoverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var coverageLoadedFor by remember { mutableStateOf<UUID?>(null) }
    var coverageLoading by remember { mutableStateOf(false) }
    var coverageViewportBounds by remember { mutableStateOf<MapGeoBounds?>(null) }
    var packageAvailability by remember { mutableStateOf<MapPackageAvailability>(MapPackageAvailability.Missing) }
    var clearSelectionConfirmationVisible by remember { mutableStateOf(false) }
    var unsavedCoverageChangesVisible by remember { mutableStateOf(false) }
    var createAreaConfirmationVisible by remember { mutableStateOf(false) }
    var areaNameInput by remember { mutableStateOf("") }
    var areaNameBlank by remember { mutableStateOf(false) }
    var developerBasemap by remember { mutableStateOf(DeveloperBasemap.ONLINE) }
    var mapCameraRevision by remember { mutableStateOf(0) }
    var coverageControlsHeightPx by remember { mutableStateOf(0) }
    val coverageCameraEdgePaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    // View mode has almost no chrome, so the framing only needs to clear the small action button.
    val areaViewEdgePaddingPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    val areaViewBottomPaddingPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val normalCameraPadding = remember { normalMapCameraPadding() }
    val reading = (locationState as? LocationUiState.Available)?.reading
    val gpsPosition = reading?.let { MapTarget(it.latitude, it.longitude) }
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
    LaunchedEffect(territoryId, areaStore, territoryName) {
        if (editingTerritoryId != null && editingTerritoryId != territoryId) {
            coverageSelectionMode = false
            coverageFragmentEditorVisible = false
            editingTerritoryId = null
            workingCoverage = emptyList()
        }
        coverageLoadedFor = null
        persistedArea = MapAreaReadResult.Absent
        persistedCoverage = emptyList()
        if (territoryId != null) {
            coverageLoading = true
            // Reading also migrates a readable legacy selection into a named Ареал.
            val read = try {
                areaStore.load(territoryId, territoryName)
            } catch (_: Exception) {
                MapAreaReadResult.Corrupt("не удалось прочитать сохранённый ареал")
            }
            persistedArea = read
            persistedCoverage = (read as? MapAreaReadResult.Present)?.area?.coverageFragments().orEmpty()
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
    // An explicit user request (the Ареал screen, the Ареал view, or Settings → Офлайн-карты →
    // «Изменить участки») opens the участки editor once. The request is consumed here, because
    // returning to the map is not a request: without that, every later visit would reload the Ареал,
    // see the old request again and open the editor by itself.
    LaunchedEffect(areaEditorRequest, territoryId, coverageLoadedFor, coverageLoading) {
        if (
            !shouldOpenAreaEditor(
                requestToken = areaEditorRequest,
                territoryId = territoryId,
                areaLoaded = !coverageLoading && coverageLoadedFor == territoryId,
            )
        ) {
            return@LaunchedEffect
        }
        editingTerritoryId = territoryId
        workingCoverage = persistedCoverage
        coverageSelectionMode = true
        // Creating a new Ареал starts on the unobstructed map. Existing-area editing keeps its
        // established single-panel workflow.
        coverageFragmentEditorVisible = persistedArea !is MapAreaReadResult.Absent
        onAreaEditorRequestHandled()
    }
    // Mode decides what the Ареал looks like here: the editor works on the draft and marks the next
    // viewport, the view mode shows exactly the stored участки, the field map draws nothing.
    val areaPresentation = mapAreaPresentation(
        mode = mode,
        editorOpen = coverageSelectionMode && coverageFragmentEditorVisible,
        draftVisible = coverageSelectionMode,
        working = workingCoverage,
        persisted = persistedCoverage,
        persistedLoaded = territoryId != null && coverageLoadedFor == territoryId && !coverageLoading,
    )
    val coverageFragments = areaPresentation.fragments
    val creatingArea = coverageSelectionMode && persistedArea is MapAreaReadResult.Absent
    val coverageViewportSummary = if (coverageSelectionMode) {
        coverageViewportBounds?.let(::coverageBoundsSummary)
    } else {
        null
    }
    val activeMapPackage = (packageAvailability as? MapPackageAvailability.Ready)?.activePackage
    val activeVectorProfile = activeMapPackage?.let(::beeSearchActivePmtilesMapProfile)
    // The editor has no unsaved work the moment it opens: the draft starts as the persisted
    // selection. Only a draft operation (add / undo last / clear all) makes it dirty.
    val coverageSelectionDirty = coverageSelectionMode &&
        isCoverageSelectionDirty(persisted = persistedCoverage, working = workingCoverage)

    /**
     * Leaves the editor. [restoreDraft] puts the persisted selection back into the draft, which is
     * how "leave without saving" discards the session without ever having written anything: the
     * store is only written by [commitCoverage] and the first-create confirmation.
     */
    fun leaveCoverageSelection(restoreDraft: Boolean) {
        if (restoreDraft) workingCoverage = persistedCoverage
        unsavedCoverageChangesVisible = false
        clearSelectionConfirmationVisible = false
        createAreaConfirmationVisible = false
        areaNameBlank = false
        map?.restoreNormalCameraPadding(normalCameraPadding)
        coverageSelectionMode = false
        coverageFragmentEditorVisible = false
        editingTerritoryId = null
        // The host may have opened this editor from the Ареал workflow; it decides where to return.
        onCoverageSessionEnded()
    }

    /** Saves edited участки of an existing Ареал, keeping its UUID and name. */
    fun saveAreaBounds(id: UUID, bounds: List<MapGeoBounds>) {
        coroutineScope.launch {
            val result = try {
                areaStore.updateBounds(id, bounds)
            } catch (_: Exception) {
                MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
            }
            when (result) {
                is MapAreaChangeResult.Saved -> {
                    if (id == latestTerritoryId) {
                        persistedArea = MapAreaReadResult.Present(result.area)
                        persistedCoverage = result.area.coverageFragments()
                    }
                    leaveCoverageSelection(restoreDraft = false)
                }

                // The stored value stays untouched: stay in the editor with the draft intact.
                is MapAreaChangeResult.Refused ->
                    Toast.makeText(appContext, result.reason, Toast.LENGTH_LONG).show()

                MapAreaChangeResult.Deleted -> leaveCoverageSelection(restoreDraft = false)
            }
        }
    }

    /** The single committed exit for both «Готово» and Back → «Сохранить». */
    fun commitCoverage() {
        val id = editingTerritoryId
        if (id == null) {
            leaveCoverageSelection(restoreDraft = false)
            return
        }
        when (val plan = planAreaCommit(persistedArea, workingCoverage, territoryName)) {
            AreaCommitPlan.ExitWithoutCreating -> leaveCoverageSelection(restoreDraft = false)

            is AreaCommitPlan.CreateWithName -> {
                // First creation asks for the name; nothing is written until it is confirmed.
                areaNameInput = plan.initialName
                areaNameBlank = false
                // The name question answers the unsaved-changes question it may have been reached
                // from, so that question must not stay stacked behind it.
                unsavedCoverageChangesVisible = false
                createAreaConfirmationVisible = true
            }

            AreaCommitPlan.UpdateBounds -> saveAreaBounds(id, workingCoverage.map { it.bounds })

            is AreaCommitPlan.Refused -> {
                Toast.makeText(appContext, plan.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Creates the Ареал after the first-save name dialog was confirmed. */
    fun createAreaFromNameDialog() {
        val name = normalizedAreaName(areaNameInput)
        if (name == null) {
            areaNameBlank = true
            return
        }
        val id = editingTerritoryId ?: return
        val draft = workingCoverage.map { it.bounds }
        coroutineScope.launch {
            val result = try {
                areaStore.create(id, name, draft)
            } catch (_: Exception) {
                MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE)
            }
            when (result) {
                is MapAreaChangeResult.Saved -> {
                    createAreaConfirmationVisible = false
                    areaNameBlank = false
                    if (id == latestTerritoryId) {
                        persistedArea = MapAreaReadResult.Present(result.area)
                        persistedCoverage = result.area.coverageFragments()
                    }
                    leaveCoverageSelection(restoreDraft = false)
                }

                // Nothing is written, so the editor keeps the draft and stays open.
                is MapAreaChangeResult.Refused -> {
                    createAreaConfirmationVisible = false
                    Toast.makeText(appContext, result.reason, Toast.LENGTH_LONG).show()
                }

                MapAreaChangeResult.Deleted -> createAreaConfirmationVisible = false
            }
        }
    }

    // Back never discards silently. With unsaved changes it asks the user to choose an outcome;
    // without them it closes the editor directly.
    BackHandler(enabled = mode == BeeMapMode.FIELD && coverageSelectionActive) {
        if (creatingArea && coverageFragmentEditorVisible) {
            coverageFragmentEditorVisible = false
        } else if (coverageSelectionDirty) {
            unsavedCoverageChangesVisible = true
        } else {
            leaveCoverageSelection(restoreDraft = true)
        }
    }

    // Viewing is not a dead end: Back returns to the screen the user came from.
    BackHandler(enabled = mode == BeeMapMode.AREA_VIEW, onBack = onExitAreaView)

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

        LaunchedEffect(map, mode, savedObjectMarkers.map(MapObjectMarker::id)) {
            if (mode != BeeMapMode.POINT_BROWSER || savedObjectMarkers.isEmpty()) {
                return@LaunchedEffect
            }
            val mapInstance = map ?: return@LaunchedEffect
            val north = savedObjectMarkers.maxOf(MapObjectMarker::latitude)
            val east = savedObjectMarkers.maxOf(MapObjectMarker::longitude)
            val south = savedObjectMarkers.minOf(MapObjectMarker::latitude)
            val west = savedObjectMarkers.minOf(MapObjectMarker::longitude)
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

        // Entering the Ареал view frames the whole saved Ареал once. The outer extent is camera
        // framing only: the участки themselves are drawn unchanged, including any overlap.
        LaunchedEffect(mode, map, coverageFragments, areaPresentation.frameWholeArea) {
            if (!areaPresentation.frameWholeArea || coverageFragments.isEmpty()) {
                return@LaunchedEffect
            }
            val mapInstance = map ?: return@LaunchedEffect
            unionBounds(coverageFragments.map(MapCoverageFragment::bounds))?.let { bounds ->
                mapInstance.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds.toLatLngBounds(),
                        areaViewEdgePaddingPx,
                        areaViewEdgePaddingPx,
                        areaViewEdgePaddingPx,
                        areaViewBottomPaddingPx,
                    ),
                )
            }
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

        if (areaPresentation.drawFragments) {
            // The same saved участки in both modes: viewing never re-derives, merges or simplifies
            // the canonical geometry.
            MapCoverageFragmentsOverlay(
                fragments = coverageFragments,
                map = map,
                cameraRevision = mapCameraRevision,
                modifier = Modifier.fillMaxSize().zIndex(1f),
            )
            if (areaPresentation.showViewportFrame) {
                MapCoverageViewportFrame(Modifier.fillMaxSize().zIndex(2f))
            }
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

        if (mode == BeeMapMode.POINT_BROWSER || mode == BeeMapMode.AREA_VIEW) {
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
            MapIdleControls(
                canRecenter = reading != null,
                canCreateRecord = reading != null &&
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
                onCreateRecord = {
                    map?.cameraPosition?.target?.let { target ->
                        onRequestCreateRecord(target.latitude, target.longitude)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(3f),
            )
        }

        if (mode == BeeMapMode.FIELD && coverageSelectionActive && coverageFragmentEditorVisible) {
            MapCoverageSelectionControls(
                fragmentCount = coverageFragments.size,
                viewportSummary = coverageViewportSummary,
                title = "Участок",
                canAddFragment = map != null,
                onAddFragment = {
                    map?.projection?.visibleRegion?.latLngBounds?.let { visibleBounds ->
                        workingCoverage = addCoverageFragment(
                            fragments = workingCoverage,
                            bounds = MapGeoBounds.fromMapLibre(visibleBounds),
                        )
                        if (creatingArea) coverageFragmentEditorVisible = false
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
                onDone = if (creatingArea) null else ::commitCoverage,
                onCancelFragment = if (creatingArea) {
                    { coverageFragmentEditorVisible = false }
                } else {
                    null
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { coverageControlsHeightPx = it.height }
                    .zIndex(3f),
            )
        } else if (mode == BeeMapMode.FIELD && creatingArea) {
            AreaCreationControls(
                fragmentCount = coverageFragments.size,
                onCreateFragment = { coverageFragmentEditorVisible = true },
                onDone = ::commitCoverage,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .zIndex(3f),
            )
        } else if (mode == BeeMapMode.AREA_VIEW && territoryId != null) {
            AreaViewControls(
                onEditSections = onEditAreaSections,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .zIndex(3f),
            )
        } else if (mode == BeeMapMode.FIELD && territoryId != null) {
            CoverageSelectionEntry(
                onEnter = {
                    if (!coverageLoading && coverageLoadedFor == territoryId) {
                        // A damaged stored value is not silently replaced by an empty editor.
                        if (persistedArea is MapAreaReadResult.Corrupt) {
                            Toast.makeText(appContext, CORRUPT_AREA_MESSAGE, Toast.LENGTH_LONG).show()
                        }
                        editingTerritoryId = territoryId
                        workingCoverage = persistedCoverage
                        coverageSelectionMode = true
                        coverageFragmentEditorVisible = true
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

        if (unsavedCoverageChangesVisible) {
            CoverageUnsavedChangesDialog(
                onSave = { commitCoverage() },
                onDiscard = { leaveCoverageSelection(restoreDraft = true) },
                onStay = { unsavedCoverageChangesVisible = false },
            )
        }

        if (createAreaConfirmationVisible) {
            AreaNameDialog(
                name = areaNameInput,
                blankName = areaNameBlank,
                onNameChange = {
                    areaNameInput = it
                    if (areaNameBlank) areaNameBlank = false
                },
                onConfirm = { createAreaFromNameDialog() },
                // Cancelling keeps the draft and every stored value untouched.
                onDismiss = {
                    createAreaConfirmationVisible = false
                    areaNameBlank = false
                },
            )
        }


        if (mode == BeeMapMode.POINT_BROWSER) {
            SavedObjectMarkersOverlay(
                markers = savedObjectMarkers,
                map = map,
                mapView = mapView,
                cameraRevision = mapCameraRevision,
                onSelectMarker = onSelectSavedObject,
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
