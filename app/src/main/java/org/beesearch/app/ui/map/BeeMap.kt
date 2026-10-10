package org.beesearch.app.ui.map

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import java.util.UUID
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.beesearch.app.MapCenterTarget
import org.beesearch.app.MapGpsMarker
import org.beesearch.app.MapGpsToTargetGuide
import org.beesearch.app.MapTarget
import org.beesearch.app.MapCenterRequest
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.beeSearchActivePmtilesMapProfile
import org.beesearch.app.beeSearchDevHybridMapProfile
import org.beesearch.app.beeSearchDevSentinelMapProfile
import org.beesearch.app.devMapBasemapsEnabled
import org.beesearch.app.devSentinelArchive
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
    territoryCode: String? = null,
    areaStore: MapAreaStore,
    packageStore: MapPackageStore,
    locationState: LocationUiState,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestCreateRecord: (Double, Double) -> Unit,
    /** Reuses the map-centre target to confirm a physical object's position. */
    locationSelectionLabel: String? = null,
    mapCenterRequest: MapCenterRequest? = null,
    onMapCenterRequestHandled: (UUID) -> Unit = {},
    onConfirmLocationSelection: (Double, Double) -> Unit = { _, _ -> },
    onCancelLocationSelection: () -> Unit = {},
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
    /**
     * Research objects of the unified data map.
     *
     * The host passes objects that already satisfy the display state — visible types with each
     * type's own period applied by the temporal query layer — so the map never filters research
     * dates itself and never mutates a stored date.
     */
    researchObjectMarkers: List<MapObjectMarker> = emptyList(),
    selectedResearchObjectId: UUID? = null,
    onSelectResearchObject: (MapObjectMarker) -> Unit = {},
    initialCamera: MapCameraContext? = null,
    onCameraChanged: (MapCameraContext) -> Unit = {},
    selectedResearchObjectPreview: (@Composable BoxScope.() -> Unit)? = null,
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
    var gpsProjectedPosition by remember { mutableStateOf<Offset?>(null) }
    var firstFixCentered by remember { mutableStateOf(initialCamera != null) }
    var initialGpsCenterEstablished by remember { mutableStateOf(initialCamera != null) }
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
    // Temporary DEV-only Sentinel basemap: the staged archive is read straight from the app's
    // external files directory, so nothing is copied and no package lifecycle is involved.
    val sentinelArchive = remember(appContext) {
        if (devMapBasemapsEnabled) devSentinelArchive(appContext) else null
    }
    val sentinelProfile = remember(sentinelArchive) {
        sentinelArchive?.let(::beeSearchDevSentinelMapProfile)
    }
    val latestTerritoryId by rememberUpdatedState(territoryId)
    val latestCameraChanged by rememberUpdatedState(onCameraChanged)
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
            if (
                (developerBasemap == DeveloperBasemap.ACTIVE_VECTOR ||
                    developerBasemap == DeveloperBasemap.HYBRID) &&
                packageAvailability !is MapPackageAvailability.Ready
            ) {
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
    // Temporary DEV-only hybrid: both packages must be present, and they stay independent sources.
    val hybridProfile = remember(sentinelArchive, activeMapPackage?.pmtilesFile?.absolutePath) {
        if (devMapBasemapsEnabled && sentinelArchive != null && activeMapPackage != null) {
            beeSearchDevHybridMapProfile(sentinelArchive, activeMapPackage.pmtilesFile)
        } else {
            null
        }
    }
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
    BackHandler(
        enabled = mode == BeeMapMode.FIELD && locationSelectionLabel != null,
        onBack = onCancelLocationSelection,
    )

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
                        initialCamera?.let { saved ->
                            mapInstance.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(saved.latitude, saved.longitude)).zoom(saved.zoom)
                                .bearing(saved.bearing).tilt(saved.tilt).build()
                            mapCenter = MapTarget(saved.latitude, saved.longitude)
                        }
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
                gpsProjectedPosition = null
                return@LaunchedEffect
            }
            val current = reading
            if (current == null) {
                gpsScreenPosition = null
                gpsProjectedPosition = null
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
            gpsProjectedPosition = projectedMapPosition(map, mapView, gpsPosition)
            gpsScreenPosition = gpsProjectedPosition?.takeIf { isMapPositionVisible(it, mapView) }
        }

        LaunchedEffect(map, mapCenterRequest?.requestId) {
            val request = mapCenterRequest ?: return@LaunchedEffect
            val mapInstance = map ?: return@LaunchedEffect
            firstFixCentered = true
            initialGpsCenterEstablished = true
            recenteredUntilNextGesture = true
            mapInstance.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(request.target.latitude, request.target.longitude),
                    mapInstance.cameraPosition.zoom.coerceAtLeast(15.0),
                ),
            )
            mapCenter = request.target
            onMapCenterRequestHandled(request.requestId)
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
                    if (mode == BeeMapMode.FIELD) {
                        mapInstance.cameraPosition.toMapCameraContext()?.let(latestCameraChanged)
                    }
                    gpsProjectedPosition = projectedMapPosition(mapInstance, currentMapView, gpsPosition)
                    gpsScreenPosition = gpsProjectedPosition?.takeIf {
                        isMapPositionVisible(it, currentMapView)
                    }
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
            developerBasemap == DeveloperBasemap.ACTIVE_VECTOR ||
            developerBasemap == DeveloperBasemap.SENTINEL ||
            developerBasemap == DeveloperBasemap.HYBRID
        ) {
            MapBasemapSourceSelector(
                modeLabel = when (developerBasemap) {
                    DeveloperBasemap.ONLINE -> "Онлайн карта"
                    DeveloperBasemap.ACTIVE_VECTOR -> "Векторная карта"
                    DeveloperBasemap.SENTINEL -> "Спутник Sentinel"
                    DeveloperBasemap.HYBRID -> "Гибрид"
                },
                onSelectOnline = { developerBasemap = DeveloperBasemap.ONLINE },
                onSelectVectorMap = {
                    if (packageAvailability is MapPackageAvailability.Ready) {
                        developerBasemap = DeveloperBasemap.ACTIVE_VECTOR
                    } else {
                        onOpenOfflineMaps()
                    }
                },
                devSentinelAvailable = sentinelProfile != null,
                onSelectSentinel = {
                    if (sentinelProfile != null) {
                        developerBasemap = DeveloperBasemap.SENTINEL
                    }
                },
                devHybridAvailable = hybridProfile != null,
                onSelectHybrid = {
                    if (hybridProfile != null) {
                        developerBasemap = DeveloperBasemap.HYBRID
                    }
                },
                modifier = if (mode == BeeMapMode.FIELD && !coverageSelectionActive) {
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 28.dp)
                        .zIndex(3f)
                } else {
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 60.dp, end = 16.dp)
                        .zIndex(3f)
                },
            )
        }

        FieldMapGpsTargetGuide(
            gpsProjectedPosition = gpsProjectedPosition,
            isFieldMap = mode == BeeMapMode.FIELD,
            coverageSelectionActive = coverageSelectionActive,
            locationSelectionActive = locationSelectionLabel != null,
            measurementAvailable = measurement != null,
            modifier = Modifier.fillMaxSize().zIndex(1f),
        )
        gpsScreenPosition?.let { position ->
            MapGpsMarker(screenPosition = position, modifier = Modifier.zIndex(1f))
        }
        if (mode == BeeMapMode.FIELD && !coverageSelectionActive) {
            MapCenterTarget(Modifier.align(Alignment.Center).zIndex(2f))
        }

        if (mode == BeeMapMode.AREA_VIEW) {
            mapZoom?.let { zoom ->
                MapZoomIndicator(
                    zoom = zoom,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(3f),
                )
            }
        } else if (locationPermissionGranted && locationState is LocationUiState.Available) {
            CompactMapStatus(
                territoryCode = territoryCode,
                accuracyMeters = locationState.reading.accuracyMeters,
                zoom = mapZoom,
                measurement = measurement,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).zIndex(3f),
            )
        } else {
            mapZoom?.let { zoom ->
                MapZoomIndicator(
                    zoom = zoom,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(3f),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .zIndex(3f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                territoryCode?.takeIf(String::isNotBlank)?.let { TerritoryCodeBadge(it) }
                Card {
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
        }

        if (mode == BeeMapMode.FIELD && !coverageSelectionActive && locationSelectionLabel == null) {
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

        if (mode == BeeMapMode.FIELD && !coverageSelectionActive && locationSelectionLabel != null) {
            PhysicalObjectLocationControls(
                canConfirm = map?.cameraPosition?.target != null,
                onConfirm = {
                    map?.cameraPosition?.target?.let { target ->
                        onConfirmLocationSelection(target.latitude, target.longitude)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(3f),
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
        // Unified data map: research objects of the current Territory, already filtered by the
        // display state (visible types and each type's own period). The overlay stays out of the way
        // of the dedicated placement and участки editing modes, which own the map gestures.
        if (mode == BeeMapMode.FIELD &&
            !coverageSelectionActive &&
            locationSelectionLabel == null &&
            researchObjectMarkers.isNotEmpty()
        ) {
            SavedObjectMarkersOverlay(
                markers = researchObjectMarkers,
                map = map,
                mapView = mapView,
                cameraRevision = mapCameraRevision,
                onSelectMarker = onSelectResearchObject,
                modifier = Modifier.fillMaxSize().zIndex(2f),
                selectedObjectId = selectedResearchObjectId,
            )
            selectedResearchObjectPreview?.invoke(this)
        }
    }

    LaunchedEffect(map, developerBasemap, activeMapPackage?.pmtilesFile?.absolutePath, sentinelArchive?.absolutePath) {
        val mapInstance = map ?: return@LaunchedEffect
        val profile = when (developerBasemap) {
            DeveloperBasemap.ONLINE -> onlineMapProfile
            DeveloperBasemap.ACTIVE_VECTOR -> activeVectorProfile ?: onlineMapProfile
            DeveloperBasemap.SENTINEL -> sentinelProfile ?: onlineMapProfile
            DeveloperBasemap.HYBRID -> hybridProfile ?: onlineMapProfile
        }
        Log.d("BeeMap", "style request mode=$developerBasemap profile=${profile.profileId} path=${profile.datasetVersion} hash=${profile.styleJson.hashCode()}")
        mapInstance.setMaxZoomPreference(profile.uiMaxZoom)
        // Leaving a deeper mode only clamps the zoom: centre, bearing and tilt are preserved.
        val camera = mapInstance.cameraPosition
        if (camera.zoom > profile.uiMaxZoom) {
            mapInstance.cameraPosition = CameraPosition.Builder(camera).zoom(profile.uiMaxZoom).build()
        }
        mapInstance.setStyle(Style.Builder().fromJson(profile.styleJson)) {
            Log.d("BeeMap", "style loaded profile=${profile.profileId} center=${mapInstance.cameraPosition.target} zoom=${mapInstance.cameraPosition.zoom}")
        }
    }
}

private enum class DeveloperBasemap {
    ONLINE,
    ACTIVE_VECTOR,

    /** Temporary DEV-only Sentinel-2 raster package; never reachable outside the debug build. */
    SENTINEL,

    /** Temporary DEV-only Sentinel-2 raster with the offline vector overlay on top. */
    HYBRID,
}

@Composable
internal fun MapBasemapSourceSelector(
    modeLabel: String,
    onSelectOnline: () -> Unit,
    onSelectVectorMap: () -> Unit,
    devSentinelAvailable: Boolean,
    onSelectSentinel: () -> Unit,
    devHybridAvailable: Boolean,
    onSelectHybrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val developerControlFontSize = (14f / LocalDensity.current.fontScale).sp
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .testTag("map-basemap-source-selector"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Источник карты: $modeLabel" },
            ) {
                LayersGlyph(Modifier.size(26.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                val itemModifier = Modifier.height(48.dp)
                val itemPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                DropdownMenuItem(text = { Text("Онлайн карта", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectOnline() }, contentPadding = itemPadding, modifier = itemModifier)
                DropdownMenuItem(text = { Text("Векторная карта", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectVectorMap() }, contentPadding = itemPadding, modifier = itemModifier)
                if (devMapBasemapsEnabled && devSentinelAvailable) {
                    DropdownMenuItem(text = { Text("Спутник Sentinel", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectSentinel() }, contentPadding = itemPadding, modifier = itemModifier)
                }
                if (devMapBasemapsEnabled && devHybridAvailable) {
                    DropdownMenuItem(text = { Text("Гибрид", fontSize = developerControlFontSize) }, onClick = { menuExpanded = false; onSelectHybrid() }, contentPadding = itemPadding, modifier = itemModifier)
                }
            }
        }
    }
}

@Composable
private fun LayersGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val centerX = size.width / 2f
        fun layer(top: Float) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX, top)
                lineTo(right, top + size.height * 0.2f)
                lineTo(centerX, top + size.height * 0.4f)
                lineTo(left, top + size.height * 0.2f)
                close()
            }
            drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        layer(size.height * 0.04f)
        layer(size.height * 0.3f)
        layer(size.height * 0.56f)
    }
}

@Composable
internal fun TerritoryCodeBadge(code: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("current-territory-code"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
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
    return "z ${zoom.roundToInt()}"
}

internal fun shouldShowGpsTargetGuide(
    isFieldMap: Boolean,
    coverageSelectionActive: Boolean,
    locationSelectionActive: Boolean,
    measurementAvailable: Boolean,
): Boolean = isFieldMap &&
    !coverageSelectionActive &&
    (locationSelectionActive || measurementAvailable)

@Composable
internal fun FieldMapGpsTargetGuide(
    gpsProjectedPosition: Offset?,
    isFieldMap: Boolean,
    coverageSelectionActive: Boolean,
    locationSelectionActive: Boolean,
    measurementAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    if (
        gpsProjectedPosition != null &&
        shouldShowGpsTargetGuide(
            isFieldMap = isFieldMap,
            coverageSelectionActive = coverageSelectionActive,
            locationSelectionActive = locationSelectionActive,
            measurementAvailable = measurementAvailable,
        )
    ) {
        MapGpsToTargetGuide(
            gpsScreenPosition = gpsProjectedPosition,
            modifier = modifier,
        )
    }
}

private fun projectedMapPosition(
    map: MapLibreMap?,
    mapView: MapView?,
    target: MapTarget?,
): Offset? {
    if (map == null || mapView == null || target == null || mapView.width <= 0 || mapView.height <= 0) return null
    val point = map.projection.toScreenLocation(LatLng(target.latitude, target.longitude))
    return Offset(point.x, point.y)
}

private fun isMapPositionVisible(position: Offset, mapView: MapView?): Boolean =
    mapView != null &&
        position.x in 0f..mapView.width.toFloat() &&
        position.y in 0f..mapView.height.toFloat()

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
