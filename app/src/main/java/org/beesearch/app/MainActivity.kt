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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
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
        locationPermissionGranted = granted
        locationPermissionState.value = granted
    }
    private var locationPermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationPermissionGranted = hasLocationPermission()
        locationPermissionState.value = locationPermissionGranted
        setContent {
            BeeSearchApp(
                locationPermissionGranted = locationPermissionState.value,
                requestLocationPermission = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
            )
        }
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
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application))
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val territories by viewModel.territories.collectAsStateWithLifecycle()
    val currentTerritory by viewModel.currentTerritory.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val observationPointDraft by viewModel.observationPointDraft.collectAsStateWithLifecycle()
    val completingObservationPointId by viewModel.completingObservationPointId.collectAsStateWithLifecycle()

    LaunchedEffect(route, locationPermissionGranted) {
        if (route == AppRoute.CurrentTerritory) {
            if (!locationPermissionGranted) requestLocationPermission()
            viewModel.setLocationPermission(locationPermissionGranted)
        } else {
            viewModel.setLocationPermission(false)
        }
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
                    is AppRoute.ResumeObservation -> ResumeObservationScreen(
                        point = currentRoute.point,
                        isCompleting = completingObservationPointId == currentRoute.point.id,
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
        )

        DisposableEffect(lifecycleOwner, mapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView?.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                    Lifecycle.Event.ON_STOP -> mapView?.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

private fun Double.formatMeters(): String = "%.1f".format(this)

@Composable
internal fun ResumeObservationScreen(
    point: ObservationPoint,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    var showCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
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
    Scaffold(topBar = { TopAppBar(title = { Text("Незавершённое наблюдение") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Наблюдение восстановлено из Room.", style = MaterialTheme.typography.titleMedium)
            Text("Код наблюдателя: ${point.observerCode}")
            Text("Координаты: ${point.latitude}, ${point.longitude}")
            point.code?.let { Text("Код точки: $it") }
            Text("Рабочий экран регистрации событий будет следующим этапом. Сохранённая точка не потеряна.")
            Button(
                onClick = { showCompletionConfirmation = true },
                enabled = !isCompleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isCompleting) "Завершение…" else "Завершить наблюдение")
            }
            Button(onClick = onOpenTerritories, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть территории")
            }
        }
    }
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
