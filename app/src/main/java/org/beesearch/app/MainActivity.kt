@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.theme.Bee_searchTheme
import java.util.UUID
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private val locationPermissionState = androidx.compose.runtime.mutableStateOf(false)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionState.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationPermissionState.value = hasLocationPermission()
        setContent {
            BeeSearchApp(
                locationPermissionGranted = locationPermissionState.value,
                requestLocationPermission = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        locationPermissionState.value = hasLocationPermission()
    }

    private fun hasLocationPermission() = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

@Composable
private fun BeeSearchApp(
    locationPermissionGranted: Boolean,
    requestLocationPermission: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as BeeSearchApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleState by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState)
    }
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application))
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val territories by viewModel.territories.collectAsStateWithLifecycle()
    val currentTerritory by viewModel.currentTerritory.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val observationPointDraft by viewModel.observationPointDraft.collectAsStateWithLifecycle()
    val completingObservationPointId by viewModel.completingObservationPointId.collectAsStateWithLifecycle()
    val beePreparation by viewModel.beePreparation.collectAsStateWithLifecycle()
    val beeMutationInProgress by viewModel.beeMutationInProgress.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleState = lifecycleOwner.lifecycle.currentState
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationTrackingActive = route == AppRoute.CurrentTerritory &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(locationPermissionGranted, locationTrackingActive) {
        viewModel.setLocationTracking(
            permissionGranted = locationPermissionGranted,
            active = locationTrackingActive,
        )
    }

    Bee_searchTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val currentRoute = route) {
                    AppRoute.Loading -> LoadingScreen()
                    AppRoute.Settings -> SettingsScreen(
                        initialObserverCode = settings.observerCode,
                        onBack = viewModel::returnToStartup,
                        onSave = viewModel::saveObserverCode,
                    )
                    AppRoute.TerritoryManagement -> TerritoryManagementScreen(
                        territories = territories,
                        currentTerritoryId = settings.currentTerritoryId,
                        onBack = viewModel::returnToStartup,
                        onOpenSettings = viewModel::openSettings,
                        onSelectTerritory = viewModel::setCurrentTerritory,
                        onCreateTerritory = viewModel::createTerritory,
                    )
                    AppRoute.CurrentTerritory -> CurrentTerritoryScreen(
                        territory = currentTerritory,
                        locationState = locationState,
                        observerCode = settings.observerCode,
                        observationPointDraft = observationPointDraft,
                        locationPermissionGranted = locationPermissionGranted,
                        onRequestLocationPermission = requestLocationPermission,
                        onStartObservationPointCreation = viewModel::startObservationPointCreation,
                        onObservationPointCoordinatesChanged = viewModel::updateObservationPointCoordinates,
                        onObserverCodeChanged = viewModel::updateObservationPointObserverCode,
                        onGpsRecenterRequested = viewModel::requestObservationPointGpsRecenter,
                        onConfirmObservationPoint = viewModel::confirmObservationPointCreation,
                        onCancelObservationPointCreation = viewModel::cancelObservationPointCreation,
                        onOpenSettings = viewModel::openSettings,
                        onOpenTerritories = viewModel::openTerritoryManagement,
                    )
                    is AppRoute.ResumeObservation -> BeePreparationScreen(
                        point = currentRoute.point,
                        preparation = beePreparation,
                        isMutating = beeMutationInProgress,
                        isCompleting = completingObservationPointId == currentRoute.point.id,
                        onAddBee = { color, position ->
                            viewModel.addPreparedBee(currentRoute.point.id, color, position)
                        },
                        onRemoveBee = viewModel::removePreparedBee,
                        onRecordNoBeesFound = {
                            viewModel.recordNoBeesFound(currentRoute.point.id)
                        },
                        onComplete = { viewModel.completeObservationPoint(currentRoute.point.id) },
                        onOpenTerritories = viewModel::openTerritoryManagement,
                    )
                }
                feedback?.let {
                    FeedbackBanner(
                        message = it,
                        onDismiss = viewModel::clearFeedback,
                        modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Загрузка сохранённых данных")
    }
}

@Composable
private fun SettingsScreen(
    initialObserverCode: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var observerCode by rememberSaveable(initialObserverCode) {
        mutableStateOf(initialObserverCode.orEmpty())
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Настройки") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Назад") }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Код наблюдателя", style = MaterialTheme.typography.titleMedium)
            Text("Код сохраняется на устройстве и копируется в новые ObservationPoint.")
            OutlinedTextField(
                value = observerCode,
                onValueChange = { observerCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("observer_code") },
                singleLine = true,
            )
            Button(
                onClick = { onSave(observerCode) },
                enabled = observerCode.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun TerritoryManagementScreen(
    territories: List<Territory>,
    currentTerritoryId: UUID?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTerritory: (UUID) -> Unit,
    onCreateTerritory: (String, String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Территории") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Назад") }
        }, actions = {
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
        })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Новая территория", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Код территории") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название территории") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onCreateTerritory(code, name) },
                enabled = code.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать") }
            Spacer(Modifier.height(16.dp))
            Text("Сохранённые территории", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (territories.isEmpty()) {
                Text("Территорий пока нет.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(territories, key = { it.id }) { territory ->
                        TerritoryRow(
                            territory = territory,
                            isCurrent = territory.id == currentTerritoryId,
                            onSelect = { onSelectTerritory(territory.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerritoryRow(territory: Territory, isCurrent: Boolean, onSelect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(territory.code, fontWeight = FontWeight.Bold)
            Text(territory.name)
            if (isCurrent) {
                Text("Текущая территория", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onSelect) { Text("Сделать текущей") }
            }
        }
    }
}

@Composable
private fun CurrentTerritoryScreen(
    territory: Territory?,
    locationState: LocationUiState,
    observerCode: String?,
    observationPointDraft: ObservationPointCreationDraft?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartObservationPointCreation: () -> Unit,
    onObservationPointCoordinatesChanged: (Double, Double) -> Unit,
    onObserverCodeChanged: (String) -> Unit,
    onGpsRecenterRequested: () -> Unit,
    onConfirmObservationPoint: () -> Unit,
    onCancelObservationPointCreation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    val isCreatingObservationPoint = observationPointDraft != null
    Scaffold(topBar = {
        if (!isCreatingObservationPoint) {
            TopAppBar(
                title = { Text("Bee Search") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Настройки") }
                },
            )
        }
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(if (isCreatingObservationPoint) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isCreatingObservationPoint) {
                if (territory == null) {
                    Text("Текущая территория не найдена")
                } else {
                    Text(
                        text = "${territory.code} — ${territory.name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (territory != null) {
                BeeMap(
                    locationState = locationState,
                    observerCode = observerCode,
                    observationPointDraft = observationPointDraft,
                    locationPermissionGranted = locationPermissionGranted,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onObservationPointCoordinatesChanged = onObservationPointCoordinatesChanged,
                    onObserverCodeChanged = onObserverCodeChanged,
                    onGpsRecenterRequested = onGpsRecenterRequested,
                    onConfirmObservationPoint = onConfirmObservationPoint,
                    onCancelObservationPointCreation = onCancelObservationPointCreation,
                    modifier = if (isCreatingObservationPoint) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().weight(1f)
                    },
                )
            }
            if (!isCreatingObservationPoint) {
                Button(
                    onClick = onStartObservationPointCreation,
                    enabled = locationState is LocationUiState.Available,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Новая точка")
                }
                Button(onClick = onOpenTerritories, modifier = Modifier.fillMaxWidth()) {
                    Text("Управление территориями")
                }
            }
        }
    }
}

@Composable
private fun BeeMap(
    locationState: LocationUiState,
    observerCode: String?,
    observationPointDraft: ObservationPointCreationDraft?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onObservationPointCoordinatesChanged: (Double, Double) -> Unit,
    onObserverCodeChanged: (String) -> Unit,
    onGpsRecenterRequested: () -> Unit,
    onConfirmObservationPoint: () -> Unit,
    onCancelObservationPointCreation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewLifecycle = remember { MapViewLifecycleController() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var locationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var firstFixCentered by remember { mutableStateOf(false) }
    var handledGpsRecenterRequestId by rememberSaveable(observationPointDraft?.originalGps?.timestamp) {
        mutableLongStateOf(0L)
    }
    val reading = (locationState as? LocationUiState.Available)?.reading

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
                        // Temporary MapLibre demo style for this milestone; D008 source/style remains open.
                        mapInstance.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                            val source = GeoJsonSource(
                                "bee-current-location",
                                Feature.fromGeometry(Point.fromLngLat(0.0, 0.0)),
                            )
                            style.addSource(source)
                            style.addLayer(
                                CircleLayer("bee-current-location-layer", "bee-current-location").withProperties(
                                    PropertyFactory.circleRadius(5f),
                                    PropertyFactory.circleColor("#1976D2"),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(1.5f),
                                ),
                            )
                            locationSource = source
                        }
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

        LaunchedEffect(reading, locationSource, map) {
            val current = reading ?: return@LaunchedEffect
            locationSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(current.longitude, current.latitude)))
            if (!firstFixCentered) {
                map?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        LatLng(current.latitude, current.longitude),
                        15.0,
                    ),
                )
                firstFixCentered = true
            }
        }

        LaunchedEffect(observationPointDraft?.originalGps?.timestamp, map) {
            val draft = observationPointDraft ?: return@LaunchedEffect
            map?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    LatLng(draft.selectedLatitude, draft.selectedLongitude),
                    17.0,
                ),
            )
        }

        LaunchedEffect(observationPointDraft?.gpsRecenterRequestId, map) {
            val draft = observationPointDraft ?: return@LaunchedEffect
            val requestId = draft.gpsRecenterRequestId
            if (requestId <= handledGpsRecenterRequestId) return@LaunchedEffect
            val mapInstance = map ?: return@LaunchedEffect
            mapInstance.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                    LatLng(draft.originalGps.latitude, draft.originalGps.longitude),
                ),
            )
            handledGpsRecenterRequestId = requestId
        }

        DisposableEffect(map, observationPointDraft != null) {
            val mapInstance = map
            if (mapInstance == null || observationPointDraft == null) {
                onDispose { }
            } else {
                val listener = MapLibreMap.OnCameraIdleListener {
                    mapInstance.cameraPosition.target?.let { target ->
                        onObservationPointCoordinatesChanged(target.latitude, target.longitude)
                    }
                }
                mapInstance.addOnCameraIdleListener(listener)
                onDispose { mapInstance.removeOnCameraIdleListener(listener) }
            }
        }

        if (observationPointDraft != null) {
            ObservationPointCrosshair(Modifier.align(Alignment.Center).zIndex(2f))
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(if (observationPointDraft == null) 12.dp else 8.dp)
                .zIndex(3f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (observationPointDraft == null) 12.dp else 8.dp),
                horizontalAlignment = if (observationPointDraft == null) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    !locationPermissionGranted -> Button(onClick = onRequestLocationPermission) {
                        Text("Разрешить доступ к местоположению")
                    }
                    observationPointDraft != null -> {
                        val offsetMeters = geodesicDistanceMeters(
                            fromLatitude = observationPointDraft.originalGps.latitude,
                            fromLongitude = observationPointDraft.originalGps.longitude,
                            toLatitude = observationPointDraft.selectedLatitude,
                            toLongitude = observationPointDraft.selectedLongitude,
                        )
                        Text(
                            text = "Точность GPS: " +
                                "±${observationPointDraft.originalGps.accuracyMeters.formatMeters()} м",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Смещение от GPS: ${formatManualOffsetMeters(offsetMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (observerCode == null) {
                            OutlinedTextField(
                                value = observationPointDraft.observerCodeInput,
                                onValueChange = onObserverCodeChanged,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Код наблюдателя") },
                                singleLine = true,
                                enabled = !observationPointDraft.isSaving,
                            )
                        }
                    }
                    locationState is LocationUiState.Available -> {
                        Text(
                            "Точность GPS: ${locationState.reading.accuracyMeters.formatMeters()} м",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    locationState is LocationUiState.Unavailable -> Text(locationState.message)
                    else -> Text("Ожидание GPS…")
                }
            }
        }

        if (observationPointDraft == null) {
            Button(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enabled = reading != null,
                onClick = {
                    reading?.let { current ->
                        map?.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                LatLng(current.latitude, current.longitude),
                                15.0,
                            ),
                        )
                    }
                },
            ) { Text("Центр") }
        } else {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).zIndex(3f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onGpsRecenterRequested,
                        enabled = !observationPointDraft.isSaving,
                        modifier = Modifier.weight(1f),
                    ) { Text("К GPS") }
                    Button(
                        onClick = onCancelObservationPointCreation,
                        enabled = !observationPointDraft.isSaving,
                        modifier = Modifier.weight(1f),
                    ) { Text("Отмена") }
                }
                Button(
                    onClick = {
                        map?.cameraPosition?.target?.let { target ->
                            onObservationPointCoordinatesChanged(target.latitude, target.longitude)
                        }
                        onConfirmObservationPoint()
                    },
                    enabled = !observationPointDraft.isSaving &&
                        (observerCode != null || observationPointDraft.observerCodeInput.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (observationPointDraft.isSaving) "Сохранение…" else "Подтвердить точку")
                }
            }
        }
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

private fun Double.formatMeters(): String = "%.1f".format(this)

@Composable
internal fun BeePreparationScreen(
    point: ObservationPoint,
    preparation: BeePreparationUiState,
    isMutating: Boolean,
    isCompleting: Boolean,
    onAddBee: (String, MarkPosition) -> Unit,
    onRemoveBee: (UUID) -> Unit,
    onRecordNoBeesFound: () -> Unit,
    onComplete: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    var showCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoBeesConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showCompletionConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showCompletionConfirmation = false },
            title = { Text("Завершить наблюдение?") },
            text = {
                Text(
                    "Точка станет завершённым историческим наблюдением. " +
                        "После успешного сохранения можно будет создать следующую точку.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionConfirmation = false
                        onComplete()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-complete-observation"),
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompletionConfirmation = false },
                    enabled = !isCompleting,
                ) { Text("Отмена") }
            },
        )
    }
    if (showNoBeesConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showNoBeesConfirmation = false },
            title = { Text("Пчёлы отсутствуют?") },
            text = {
                Text(
                    "Точка наблюдения будет сохранена с результатом " +
                        "«пчёлы отсутствуют» и завершена.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoBeesConfirmation = false
                        onRecordNoBeesFound()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-no-bees"),
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoBeesConfirmation = false },
                    enabled = !isCompleting,
                ) { Text("Отмена") }
            },
        )
    }
    val stateMatchesPoint = preparation.pointId == point.id
    val bees = if (stateMatchesPoint) preparation.bees else emptyList()
    val isLoading = preparation.isLoading || !stateMatchesPoint
    val isReleaseStarted = stateMatchesPoint && preparation.isReleaseStarted
    val beePresenceResult = if (stateMatchesPoint) preparation.beePresenceResult else null
    val canRecordNoBees = !isLoading &&
        !isReleaseStarted &&
        bees.isEmpty() &&
        beePresenceResult == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Подготовка пчёл") },
                actions = {
                    TextButton(
                        onClick = { showCompletionConfirmation = true },
                        enabled = !isCompleting &&
                            !isMutating &&
                            beePresenceResult != null,
                    ) { Text(if (isCompleting) "Завершение…" else "Завершить") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("bee-preparation-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = point.code?.let { "Активная точка $it" } ?: "Активная точка наблюдения",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            } else {
                item {
                    PreparationReadinessCard(
                        beeCount = bees.size,
                        isReleaseStarted = isReleaseStarted,
                    )
                }

                item {
                    Text("Подготовленные пчёлы", style = MaterialTheme.typography.titleMedium)
                }

                if (bees.isEmpty()) {
                    item { Text("Пока не добавлено ни одной пчелы.") }
                } else {
                    items(bees, key = { it.id }) { bee ->
                        PreparedBeeRow(
                            bee = bee,
                            canRemove = !isReleaseStarted && !isMutating,
                            onRemove = { onRemoveBee(bee.id) },
                        )
                    }
                }

                if (canRecordNoBees) {
                    item {
                        OutlinedButton(
                            onClick = { showNoBeesConfirmation = true },
                            enabled = !isCompleting && !isMutating,
                            modifier = Modifier.fillMaxWidth().testTag("record-no-bees"),
                        ) { Text("Пчёлы отсутствуют") }
                    }
                }

                if (isReleaseStarted) {
                    item {
                        Text(
                            "Первый выпуск уже начат. Состав пчёл зафиксирован.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    item {
                        BeeSelector(
                            bees = bees,
                            enabled = !isMutating,
                            onAddBee = onAddBee,
                        )
                    }
                }

                item {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Выпустить всех") }
                }
                item {
                    Text(
                        if (bees.isEmpty()) {
                            "Добавьте хотя бы одну пчелу. Первый выпуск будет реализован следующим этапом."
                        } else {
                            "Набор готов. Первый групповой выпуск будет реализован следующим этапом."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    TextButton(onClick = onOpenTerritories, modifier = Modifier.fillMaxWidth()) {
                        Text("Управление территориями")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparationReadinessCard(beeCount: Int, isReleaseStarted: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when {
                    isReleaseStarted -> "Первый выпуск начат"
                    beeCount > 0 -> "Готово к выпуску: $beeCount"
                    else -> "Набор ещё не готов"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (beeCount > 0) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            if (!isReleaseStarted) {
                Text("Добавляйте только фактически подготовленных пчёл.")
            }
        }
    }
}

@Composable
private fun PreparedBeeRow(bee: Bee, canRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarkColorSwatch(bee.markColor)
            Text(
                BeeMarkCatalog.displayName(bee.markColor, bee.markPosition),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onRemove, enabled = canRemove) { Text("Удалить") }
        }
    }
}

@Composable
internal fun BeeSelector(
    bees: List<Bee>,
    enabled: Boolean,
    onAddBee: (String, MarkPosition) -> Unit,
) {
    val used = bees.mapTo(mutableSetOf()) { BeeMarkCombination(it.markColor, it.markPosition) }
    val available = BeeMarkCatalog.availableCombinations(used)
    var selectedColor by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPositionName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedColorValue = selectedColor
    val selectedPosition = selectedPositionName?.let(MarkPosition::valueOf)
    val selectedCombination = if (selectedColorValue != null && selectedPosition != null) {
        BeeMarkCombination(selectedColorValue, selectedPosition)
    } else {
        null
    }

    LaunchedEffect(available) {
        if (selectedCombination != null && selectedCombination !in available) {
            selectedColor = null
            selectedPositionName = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Добавить пчелу", style = MaterialTheme.typography.titleMedium)
        if (available.isEmpty()) {
            Text("Все поддерживаемые сочетания меток уже добавлены.")
        } else {
            Text("Цвет", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BeeMarkCatalog.colors.forEach { color ->
                    val hasAvailablePosition = available.any { it.markColor == color.value }
                    FilterChip(
                        selected = selectedColor == color.value,
                        onClick = {
                            selectedColor = color.value
                            if (selectedPosition?.let {
                                    BeeMarkCombination(color.value, it) !in available
                                } == true
                            ) {
                                selectedPositionName = null
                            }
                        },
                        enabled = enabled && hasAvailablePosition,
                        modifier = Modifier.testTag("mark-color-${color.value}"),
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MarkColorSwatch(color.value, size = 18)
                                Text(color.displayName)
                            }
                        },
                    )
                }
            }
            Text("Положение метки", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BeeMarkCatalog.positions.forEach { position ->
                    val combination = selectedColor?.let { BeeMarkCombination(it, position) }
                    FilterChip(
                        selected = selectedPosition == position,
                        onClick = { selectedPositionName = position.name },
                        enabled = enabled && combination in available,
                        modifier = Modifier.testTag("mark-position-${position.name}"),
                        label = { Text(BeeMarkCatalog.positionDisplayName(position)) },
                    )
                }
            }
            Button(
                onClick = {
                    selectedCombination?.let { combination ->
                        onAddBee(combination.markColor, combination.markPosition)
                    }
                },
                enabled = enabled && selectedCombination in available,
                modifier = Modifier.fillMaxWidth().testTag("add-bee"),
            ) { Text(if (enabled) "Добавить" else "Сохранение…") }
        }
    }
}

@Composable
private fun MarkColorSwatch(markColor: String, size: Int = 28) {
    val color = when (markColor) {
        "WHITE" -> Color.White
        "YELLOW" -> Color(0xFFFFD54F)
        "BLUE" -> Color(0xFF1976D2)
        "RED" -> Color(0xFFD32F2F)
        "GREEN" -> Color(0xFF388E3C)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .semantics { contentDescription = "Цвет метки: $markColor" },
    )
}

@Preview(showBackground = true)
@Composable
private fun TerritoryRowPreview() {
    Bee_searchTheme {
        TerritoryRow(
            territory = Territory(
                id = UUID.randomUUID(),
                code = "KLYAZMA-01",
                name = "Клязьминская пойма",
                createdAt = java.time.Instant.EPOCH,
                updatedAt = java.time.Instant.EPOCH,
            ),
            isCurrent = true,
            onSelect = {},
        )
    }
}
