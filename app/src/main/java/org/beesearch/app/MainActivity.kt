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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.theme.Bee_searchTheme
import java.util.UUID
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import java.time.Instant

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
    val beeEventInProgressIds by viewModel.beeEventInProgressIds.collectAsStateWithLifecycle()
    val flightAzimuthInProgressIds by viewModel.flightAzimuthInProgressIds.collectAsStateWithLifecycle()
    val resumeRoute = route as? AppRoute.ResumeObservation
    val observationScreenVisible = resumeRoute != null &&
        !beePreparation.isLoading &&
        beePreparation.pointId == resumeRoute.point.id &&
        activePointWorkflowPhase(beePreparation.isReleaseStarted) == ActivePointWorkflowPhase.OBSERVATION

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
                        currentTerritory = currentTerritory,
                        onBack = viewModel::returnToStartup,
                        onSave = viewModel::saveObserverCode,
                        onOpenTerritories = viewModel::openTerritoryManagement,
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
                        onObserverCodeChanged = viewModel::updateObservationPointObserverCode,
                        onConfirmObservationPoint = viewModel::confirmObservationPointCreation,
                        onCancelObservationPointCreation = viewModel::cancelObservationPointCreation,
                        onOpenSettings = viewModel::openSettings,
                        onOpenTerritories = viewModel::openTerritoryManagement,
                    )
                    is AppRoute.ResumeObservation -> {
                        val stateMatchesPoint = beePreparation.pointId == currentRoute.point.id
                        when {
                            beePreparation.isLoading || !stateMatchesPoint -> LoadingScreen()
                            activePointWorkflowPhase(beePreparation.isReleaseStarted) ==
                                ActivePointWorkflowPhase.OBSERVATION -> BeeObservationScreen(
                                point = currentRoute.point,
                                bees = beePreparation.bees,
                                flightCycles = beePreparation.flightCycles,
                                beeEventInProgressIds = beeEventInProgressIds,
                                flightAzimuthInProgressIds = flightAzimuthInProgressIds,
                                headingProvider = application.container.headingProvider,
                                feedback = feedback,
                                onDismissFeedback = viewModel::clearFeedback,
                                isCompleting = completingObservationPointId == currentRoute.point.id,
                                onRegisterReturn = viewModel::registerBeeReturn,
                                onStartNextFlight = viewModel::startNextFlight,
                                onCaptureFlightAzimuth = viewModel::captureFlightAzimuth,
                                onSetFlightAzimuth = viewModel::setFlightAzimuth,
                                onComplete = {
                                    viewModel.completeObservationPoint(currentRoute.point.id)
                                },
                            )
                            else -> BeePreparationScreen(
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
                                onComplete = {
                                    viewModel.completeObservationPoint(currentRoute.point.id)
                                },
                                onOpenTerritories = viewModel::openTerritoryManagement,
                                onStartInitialGroupRelease = {
                                    viewModel.startInitialGroupRelease(currentRoute.point.id)
                                },
                            )
                        }
                    }
                }
                if (!observationScreenVisible) feedback?.let {
                    FeedbackBanner(
                        feedback = it,
                        onDismiss = { feedbackId -> viewModel.clearFeedback(feedbackId) },
                        modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
                    )
                }
            }
        }
    }
}

internal const val FEEDBACK_AUTO_DISMISS_MILLIS = 3_000L

@Composable
internal fun FeedbackBanner(
    feedback: UiFeedback,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = FEEDBACK_AUTO_DISMISS_MILLIS,
) {
    LaunchedEffect(feedback.id, feedback.displayMode, autoDismissMillis) {
        if (feedback.displayMode == FeedbackDisplayMode.AUTO_DISMISS) {
            delay(autoDismissMillis)
            onDismiss(feedback.id)
        }
    }

    Card(modifier = modifier.fillMaxWidth().padding(8.dp).testTag("feedback-banner")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                feedback.message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(
                onClick = { onDismiss(feedback.id) },
                modifier = Modifier.testTag("feedback-dismiss"),
            ) { Text("Закрыть") }
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
internal fun SettingsScreen(
    initialObserverCode: String?,
    currentTerritory: Territory?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onOpenTerritories: () -> Unit,
) {
    var observerCode by rememberSaveable(initialObserverCode) {
        mutableStateOf(initialObserverCode.orEmpty())
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("Настройки") }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Назад") }
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
            HorizontalDivider()
            Text("Территория", style = MaterialTheme.typography.titleMedium)
            Text(
                currentTerritory?.let { "${it.code} — ${it.name}" }
                    ?: "Текущая территория не выбрана",
            )
            OutlinedButton(
                onClick = onOpenTerritories,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Управление территориями") }
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
internal fun CurrentTerritoryScreen(
    territory: Territory?,
    locationState: LocationUiState,
    observerCode: String?,
    observationPointDraft: ObservationPointCreationDraft?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartObservationPointCreation: (Double, Double) -> Unit,
    onObserverCodeChanged: (String) -> Unit,
    onConfirmObservationPoint: () -> Unit,
    onCancelObservationPointCreation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    MapFirstScaffold(
        onOpenSettings = onOpenSettings,
    ) { mapModifier ->
        Box(modifier = mapModifier) {
            if (territory != null) {
                BeeMap(
                    locationState = locationState,
                    isCreatingObservationPoint = observationPointDraft != null,
                    locationPermissionGranted = locationPermissionGranted,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onCreateObservationPointAt = onStartObservationPointCreation,
                    modifier = Modifier.fillMaxSize().testTag(MAIN_MAP_VIEWPORT_TAG),
                )
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Текущая территория не найдена")
                        TextButton(onClick = onOpenTerritories) { Text("Выбрать территорию") }
                    }
                }
            }
        }
    }
    if (observationPointDraft != null && observerCode == null) {
        ObservationPointObserverCodeDialog(
            draft = observationPointDraft,
            onObserverCodeChanged = onObserverCodeChanged,
            onConfirm = onConfirmObservationPoint,
            onCancel = onCancelObservationPointCreation,
        )
    }
}

@Composable
internal fun ObservationPointObserverCodeDialog(
    draft: ObservationPointCreationDraft,
    onObserverCodeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!draft.isSaving) onCancel() },
        modifier = Modifier.testTag("observation-point-observer-dialog"),
        title = { Text("Код наблюдателя") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Код будет сохранён в настройках и в новой точке.")
                OutlinedTextField(
                    value = draft.observerCodeInput,
                    onValueChange = onObserverCodeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("observer_code") },
                    singleLine = true,
                    enabled = !draft.isSaving,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = draft.observerCodeInput.isNotBlank() && !draft.isSaving,
            ) {
                Text(if (draft.isSaving) "Сохранение…" else "Создать точку")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !draft.isSaving) { Text("Отмена") }
        },
    )
}

internal const val MAIN_MAP_VIEWPORT_TAG = "main-map-viewport"
internal const val MAIN_BOTTOM_PANEL_TAG = "main-bottom-panel"
internal const val RECENTER_MAP_DESCRIPTION = "Вернуться к текущему местоположению"
internal const val CREATE_OBSERVATION_POINT_DESCRIPTION = "Создать точку наблюдения"
internal const val SETTINGS_DESCRIPTION = "Настройки"

@Composable
internal fun MapFirstScaffold(
    onOpenSettings: () -> Unit,
    content: @Composable BoxScope.(Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MapBottomPanel(onOpenSettings = onOpenSettings)
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
internal fun MapBottomPanel(
    onOpenSettings: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
                .testTag(MAIN_BOTTOM_PANEL_TAG),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = SETTINGS_DESCRIPTION },
            ) {
                SettingsGlyph(Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SettingsGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = size.minDimension * 0.23f
        val spokeStartRadius = innerRadius
        val spokeEndRadius = size.minDimension * 0.46f
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
            style = Stroke(width = 2.2.dp.toPx()),
        )
        repeat(8) { index ->
            val angle = Math.PI * index / 4.0
            val start = Offset(
                x = center.x + (kotlin.math.cos(angle) * spokeStartRadius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * spokeStartRadius).toFloat(),
            )
            val end = Offset(
                x = center.x + (kotlin.math.cos(angle) * spokeEndRadius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * spokeEndRadius).toFloat(),
            )
            drawLine(color = color, start = start, end = end, strokeWidth = 2.8.dp.toPx())
        }
        drawCircle(color = color, radius = 1.8.dp.toPx(), center = center)
    }
}

@Composable
private fun BeeMap(
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
    val reading = (locationState as? LocationUiState.Available)?.reading
    val gpsPosition = reading?.let { MapTarget(it.latitude, it.longitude) }
    val measurement = if (initialGpsCenterEstablished) {
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
                        mapInstance.setStyle(mapProfile.styleUrl)
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
                val updateGpsPosition = {
                    gpsScreenPosition = projectedMapPosition(mapInstance, currentMapView, gpsPosition)
                }
                val moveListener = MapLibreMap.OnCameraMoveListener(updateGpsPosition)
                val listener = MapLibreMap.OnCameraIdleListener {
                    mapInstance.cameraPosition.target?.let { target ->
                        mapCenter = MapTarget(target.latitude, target.longitude)
                        if (firstFixCentered) initialGpsCenterEstablished = true
                    }
                    updateGpsPosition()
                }
                val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateGpsPosition()
                }
                mapInstance.addOnCameraMoveListener(moveListener)
                mapInstance.addOnCameraIdleListener(listener)
                currentMapView.addOnLayoutChangeListener(layoutListener)
                updateGpsPosition()
                onDispose {
                    mapInstance.removeOnCameraMoveListener(moveListener)
                    mapInstance.removeOnCameraIdleListener(listener)
                    currentMapView.removeOnLayoutChangeListener(layoutListener)
                }
            }
        }

        gpsScreenPosition?.let { position ->
            MapGpsMarker(screenPosition = position, modifier = Modifier.zIndex(1f))
        }
        MapCenterTarget(Modifier.align(Alignment.Center).zIndex(2f))

        if (locationPermissionGranted && locationState is LocationUiState.Available) {
            CompactMapStatus(
                accuracyMeters = locationState.reading.accuracyMeters,
                measurement = measurement,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).zIndex(3f),
            )
        } else {
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

        MapIdleControls(
            canRecenter = reading != null,
            canCreateObservationPoint = reading != null &&
                mapCenter != null &&
                initialGpsCenterEstablished &&
                !isCreatingObservationPoint,
            onRecenter = {
                reading?.let { current ->
                    map?.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            LatLng(current.latitude, current.longitude),
                            15.0,
                        ),
                    )
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

@Composable
internal fun CompactMapStatus(
    accuracyMeters: Double,
    measurement: MapMeasurement?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactGpsAccuracy(accuracyMeters = accuracyMeters)
        if (measurement != null) {
            MapMeasurementOverlay(measurement = measurement)
        }
    }
}

@Composable
internal fun MapMeasurementOverlay(
    measurement: MapMeasurement,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("map-measurement-overlay"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = formatMapMeasurement(measurement),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CompactGpsAccuracy(
    accuracyMeters: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("gps-accuracy-overlay"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = "Точность ${accuracyMeters.formatMeters()} м",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MapIdleControls(
    canRecenter: Boolean,
    canCreateObservationPoint: Boolean,
    onRecenter: () -> Unit,
    onCreateObservationPoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(
            onClick = onRecenter,
            enabled = canRecenter,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = RECENTER_MAP_DESCRIPTION }
                .testTag("map-recenter"),
        ) { RecenterGlyph() }
        Button(
            onClick = onCreateObservationPoint,
            enabled = canCreateObservationPoint,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = CREATE_OBSERVATION_POINT_DESCRIPTION }
                .testTag("create-observation-point"),
        ) { AddPointGlyph() }
    }
}

@Composable
private fun RecenterGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.28f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
        val inset = 1.dp.toPx()
        drawLine(color, Offset(center.x, inset), Offset(center.x, center.y - radius), 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y + radius), Offset(center.x, size.height - inset), 2.dp.toPx())
        drawLine(color, Offset(inset, center.y), Offset(center.x - radius, center.y), 2.dp.toPx())
        drawLine(color, Offset(center.x + radius, center.y), Offset(size.width - inset, center.y), 2.dp.toPx())
        drawCircle(color = color, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
private fun AddPointGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val half = size.minDimension * 0.30f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), 2.5.dp.toPx())
        drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), 2.5.dp.toPx())
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
    onStartInitialGroupRelease: () -> Unit = {},
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
                    item(key = "bee-selector") {
                        BeeSelector(
                            bees = bees,
                            enabled = !isMutating,
                            onAddBee = onAddBee,
                        )
                    }
                }

                item {
                    Button(
                        onClick = onStartInitialGroupRelease,
                        enabled = bees.isNotEmpty() &&
                            beePresenceResult == BeePresenceResult.BEES_FOUND &&
                            !isMutating &&
                            !isCompleting,
                        modifier = Modifier.fillMaxWidth().testTag("initial-group-release"),
                    ) { Text(if (isMutating) "Сохранение…" else "Выпустить всех") }
                }
                item {
                    Text(
                        if (bees.isEmpty()) {
                            "Добавьте хотя бы одну пчелу для первого группового выпуска."
                        } else {
                            "Все подготовленные пчёлы получат одно общее время первого выпуска."
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
internal fun BeeObservationScreen(
    point: ObservationPoint,
    bees: List<Bee>,
    flightCycles: List<FlightCycle>,
    beeEventInProgressIds: Set<UUID>,
    flightAzimuthInProgressIds: Set<UUID> = emptySet(),
    headingProvider: HeadingProvider = HeadingProvider {
        flowOf(HeadingState.Unavailable("Датчик направления недоступен"))
    },
    feedback: UiFeedback? = null,
    onDismissFeedback: (Long) -> Unit = {},
    isCompleting: Boolean,
    onRegisterReturn: (UUID) -> Unit,
    onStartNextFlight: (UUID) -> Unit,
    onSetFlightAzimuth: (UUID, Double?, () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    onCaptureFlightAzimuth: (UUID, Double, () -> Unit) -> Unit =
        { cycleId, value, onSuccess -> onSetFlightAzimuth(cycleId, value, onSuccess) },
    onComplete: () -> Unit,
    nowProvider: () -> Instant = { Instant.now() },
    undoTimeoutMillis: Long = FEEDBACK_AUTO_DISMISS_MILLIS,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
    var undoSequence by remember { mutableLongStateOf(0L) }
    var azimuthUndo by remember { mutableStateOf<AzimuthUndo?>(null) }
    var nowEpochMillis by remember { mutableLongStateOf(nowProvider().toEpochMilli()) }
    val cards = remember(bees, flightCycles) {
        buildBeeObservationCards(bees, flightCycles)
    }
    val headingFlow = remember(headingProvider, point.latitude, point.longitude) {
        headingProvider.updates(
            HeadingReference(
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeMeters = 0.0,
            ),
        )
    }
    val headingState by headingFlow.collectAsStateWithLifecycle(
        initialValue = HeadingState.Initializing,
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                nowEpochMillis = nowProvider().toEpochMilli()
                delay(1_000)
            }
        }
    }

    LaunchedEffect(azimuthUndo?.id, undoTimeoutMillis) {
        val undoId = azimuthUndo?.id ?: return@LaunchedEffect
        delay(undoTimeoutMillis)
        if (azimuthUndo?.id == undoId) azimuthUndo = null
    }

    LaunchedEffect(feedback?.id, feedback?.displayMode, azimuthUndo?.id) {
        val ordinaryFeedback = feedback?.takeIf {
            it.displayMode == FeedbackDisplayMode.AUTO_DISMISS
        }
        if (azimuthUndo != null && ordinaryFeedback != null) {
            onDismissFeedback(ordinaryFeedback.id)
        }
    }

    if (showCompletionConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showCompletionConfirmation = false },
            title = { Text("Завершить наблюдение?") },
            text = {
                Text(
                    "Точка наблюдения будет завершена. " +
                        "Незаконченные полёты сохранятся как есть.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionConfirmation = false
                        onComplete()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-field-observation-completion"),
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompletionConfirmation = false },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("cancel-field-observation-completion"),
                ) { Text("Отмена") }
            },
        )
    }

    val persistentFeedback = feedback?.takeIf {
        it.displayMode == FeedbackDisplayMode.PERSISTENT
    }
    val ordinaryTransientFeedback = feedback?.takeIf {
        it.displayMode == FeedbackDisplayMode.AUTO_DISMISS
    }
    val currentUndo = azimuthUndo

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            .testTag("observation-header"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("observation-header-feedback-slot"),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            when {
                                persistentFeedback != null -> ObservationHeaderTitle(point.pointNumber)
                                currentUndo != null -> {
                                    val undo = currentUndo
                                    AzimuthUndoBanner(
                                        undo = undo,
                                        isUndoing = undo.flightCycleId in flightAzimuthInProgressIds,
                                        onUndo = {
                                            val undoId = undo.id
                                            onSetFlightAzimuth(undo.flightCycleId, null) {
                                                if (azimuthUndo?.id == undoId) azimuthUndo = null
                                            }
                                        },
                                    )
                                }
                                ordinaryTransientFeedback != null -> ObservationTransientFeedbackBanner(
                                    feedback = ordinaryTransientFeedback,
                                    onDismiss = onDismissFeedback,
                                )
                                else -> ObservationHeaderTitle(point.pointNumber)
                            }
                        }
                        TextButton(
                            onClick = { showCompletionConfirmation = true },
                            enabled = !isCompleting &&
                                beeEventInProgressIds.isEmpty() &&
                                flightAzimuthInProgressIds.isEmpty(),
                            modifier = Modifier.testTag("complete-field-observation"),
                        ) { Text(if (isCompleting) "Завершение…" else "Завершить") }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("bee-observation-list"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(cards, key = { it.bee.id }) { card ->
                    BeeObservationCard(
                        card = card,
                        now = Instant.ofEpochMilli(nowEpochMillis),
                        headingState = headingState,
                        isEventInProgress = card.bee.id in beeEventInProgressIds,
                        isAzimuthInProgress = card.latestCycle?.id in flightAzimuthInProgressIds,
                        onRegisterReturn = { onRegisterReturn(card.bee.id) },
                        onStartNextFlight = { onStartNextFlight(card.bee.id) },
                        onCaptureAzimuth = { cycle, value ->
                            onCaptureFlightAzimuth(cycle.id, value.toDouble()) {
                                undoSequence += 1
                                azimuthUndo = AzimuthUndo(
                                    id = undoSequence,
                                    flightCycleId = cycle.id,
                                    azimuthDeg = value,
                                )
                            }
                        },
                    )
                }
            }
        }
        persistentFeedback?.let {
            FeedbackBanner(
                feedback = persistentFeedback,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .zIndex(5f),
            )
        }
    }
}

@Composable
private fun ObservationHeaderTitle(pointNumber: Int) {
    Text(
        "Наблюдение · точка $pointNumber",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ObservationTransientFeedbackBanner(
    feedback: UiFeedback,
    onDismiss: (Long) -> Unit,
    autoDismissMillis: Long = FEEDBACK_AUTO_DISMISS_MILLIS,
) {
    LaunchedEffect(feedback.id, autoDismissMillis) {
        delay(autoDismissMillis)
        onDismiss(feedback.id)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("observation-transient-banner"),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = feedback.message,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("observation-transient-text"),
            )
        }
    }
}

private data class AzimuthUndo(
    val id: Long,
    val flightCycleId: UUID,
    val azimuthDeg: Int,
)

@Composable
private fun BeeObservationCard(
    card: BeeObservationCardModel,
    now: Instant,
    headingState: HeadingState,
    isEventInProgress: Boolean,
    isAzimuthInProgress: Boolean,
    onRegisterReturn: () -> Unit,
    onStartNextFlight: () -> Unit,
    onCaptureAzimuth: (FlightCycle, Int) -> Unit,
) {
    val state = card.fieldState
    val stateStartedAt = card.stateStartedAt
    val stateText = when (state) {
        BeeFieldState.IN_FLIGHT -> "В полёте"
        BeeFieldState.AT_POINT -> "На точке"
        null -> "Ожидание данных"
    }
    val actionText = when (state) {
        BeeFieldState.IN_FLIGHT -> "ПРИЛЕТЕЛА"
        BeeFieldState.AT_POINT -> "УЛЕТЕЛА"
        null -> "НЕДОСТУПНО"
    }
    val latestCycle = card.latestCycle
    val openCycle = latestCycle?.takeIf { it.returnTime == null }
    val liveHeading = headingState as? HeadingState.Available
    val captureEnabled = openCycle != null &&
        !openCycle.azimuthCaptureConsumed &&
        openCycle.azimuthDeg == null &&
        liveHeading != null &&
        liveHeading.accuracy != HeadingAccuracy.UNRELIABLE &&
        !isEventInProgress &&
        !isAzimuthInProgress

    Card(modifier = Modifier.fillMaxWidth().testTag("bee-card-${card.bee.id}")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ObservationBeeMark(
                    markColor = card.bee.markColor,
                    markPosition = card.bee.markPosition,
                )
                Text(
                    stateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .testTag("bee-state-${card.bee.id}"),
                )
                Text(
                    stateStartedAt?.let { formatElapsedTime(it, now) } ?: "--:--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("bee-timer-${card.bee.id}"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                        .clickable(
                            enabled = captureEnabled,
                            role = Role.Button,
                            onClick = {
                                if (openCycle != null && liveHeading != null) {
                                    onCaptureAzimuth(openCycle, liveHeading.trueHeadingDeg)
                                }
                            },
                        )
                        .padding(horizontal = 6.dp)
                        .testTag("bee-azimuth-${card.bee.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            isAzimuthInProgress -> "…°"
                            latestCycle?.azimuthDeg != null -> formatAzimuthDegrees(latestCycle.azimuthDeg)
                            openCycle == null -> "—°"
                            latestCycle?.azimuthCaptureConsumed == true -> "—°"
                            liveHeading != null && liveHeading.accuracy == HeadingAccuracy.UNRELIABLE -> "! —"
                            liveHeading != null && liveHeading.accuracy == HeadingAccuracy.LOW ->
                                "! ${liveHeading.trueHeadingDeg}°"
                            liveHeading != null -> "${liveHeading.trueHeadingDeg}°"
                            headingState is HeadingState.Initializing -> "…°"
                            else -> "нет"
                        },
                        color = when {
                            latestCycle?.azimuthDeg != null -> MaterialTheme.colorScheme.onSurface
                            liveHeading?.accuracy == HeadingAccuracy.LOW ||
                                liveHeading?.accuracy == HeadingAccuracy.UNRELIABLE ->
                                MaterialTheme.colorScheme.error
                            captureEnabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = headingContentDescription(
                                persistedAzimuth = latestCycle?.azimuthDeg,
                                headingState = headingState,
                                isInFlight = openCycle != null,
                                captureConsumed = latestCycle?.azimuthCaptureConsumed == true,
                            )
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        when (state) {
                            BeeFieldState.IN_FLIGHT -> onRegisterReturn()
                            BeeFieldState.AT_POINT -> onStartNextFlight()
                            null -> Unit
                        }
                    },
                    enabled = state != null && !isEventInProgress && !isAzimuthInProgress,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 132.dp, minHeight = 48.dp)
                        .testTag("bee-action-${card.bee.id}"),
                ) {
                    Text(if (isEventInProgress) "СОХРАНЕНИЕ…" else actionText)
                }
            }
        }
    }
}

@Composable
private fun ObservationBeeMark(
    markColor: String,
    markPosition: MarkPosition,
) {
    val background = markColorValue(markColor)
    val foreground = if (markColor == "WHITE" || markColor == "YELLOW") Color.Black else Color.White
    val positionText = when (markPosition) {
        MarkPosition.NONE -> null
        MarkPosition.RIGHT_WING -> "КП"
        MarkPosition.LEFT_WING -> "КЛ"
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(background, CircleShape)
            .border(
                BorderStroke(if (markColor == "WHITE") 2.dp else 1.dp, Color(0xFF2B211D)),
                CircleShape,
            )
            .semantics {
                contentDescription = BeeMarkCatalog.displayName(markColor, markPosition)
            }
            .testTag("bee-mark-$markColor-${markPosition.name}"),
        contentAlignment = Alignment.Center,
    ) {
        positionText?.let {
            Text(
                text = it,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AzimuthUndoBanner(
    undo: AzimuthUndo,
    isUndoing: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("azimuth-undo-banner"),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${undo.azimuthDeg}° сохранён",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = onUndo,
                enabled = !isUndoing,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("azimuth-undo"),
            ) {
                Text(
                    if (isUndoing) "ОТМЕНА…" else "ОТМЕНИТЬ",
                    color = MaterialTheme.colorScheme.inversePrimary,
                    fontWeight = FontWeight.Bold,
                )
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
    var pendingColor by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPositionName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedColorValue = selectedColor
    val selectedPosition = selectedPositionName?.let(MarkPosition::valueOf)
    val selectedCombination = if (selectedColorValue != null && selectedPosition != null) {
        BeeMarkCombination(selectedColorValue, selectedPosition)
    } else {
        null
    }
    val pendingCombination = if (pendingColor != null && pendingPositionName != null) {
        BeeMarkCombination(pendingColor!!, MarkPosition.valueOf(pendingPositionName!!))
    } else {
        null
    }

    fun updateSelection(combination: BeeMarkCombination?) {
        selectedColor = combination?.markColor
        selectedPositionName = combination?.markPosition?.name
    }

    fun clearPendingAddition() {
        pendingColor = null
        pendingPositionName = null
    }

    LaunchedEffect(used) {
        when {
            pendingCombination != null && pendingCombination in used -> {
                updateSelection(
                    BeeSelectorSelectionLogic.nextAfterAdded(pendingCombination, available),
                )
                clearPendingAddition()
            }
            pendingCombination == null &&
                selectedCombination != null &&
                selectedCombination !in available -> updateSelection(null)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Добавить пчелу", style = MaterialTheme.typography.titleMedium)
        if (available.isEmpty()) {
            Text("Все поддерживаемые сочетания меток уже добавлены.")
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().testTag("add-bee"),
            ) { Text("Добавить") }
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
                            clearPendingAddition()
                            updateSelection(
                                BeeSelectorSelectionLogic.firstAvailableForColor(
                                    color.value,
                                    available,
                                ),
                            )
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
                        onClick = {
                            clearPendingAddition()
                            selectedColor?.let { color ->
                                updateSelection(
                                    BeeSelectorSelectionLogic.manualPosition(
                                        color,
                                        position,
                                        available,
                                    ),
                                )
                            }
                        },
                        enabled = enabled && combination in available,
                        modifier = Modifier.testTag("mark-position-${position.name}"),
                        label = { Text(BeeMarkCatalog.positionDisplayName(position)) },
                    )
                }
            }
            Button(
                onClick = {
                    selectedCombination?.let { combination ->
                        pendingColor = combination.markColor
                        pendingPositionName = combination.markPosition.name
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
    val color = markColorValue(markColor)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .semantics { contentDescription = "Цвет метки: $markColor" },
    )
}

private fun markColorValue(markColor: String): Color = when (markColor) {
    "WHITE" -> Color.White
    "YELLOW" -> Color(0xFFFFD54F)
    "BLUE" -> Color(0xFF1565C0)
    "RED" -> Color(0xFFC62828)
    "GREEN" -> Color(0xFF2E7D32)
    else -> Color.Gray
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
