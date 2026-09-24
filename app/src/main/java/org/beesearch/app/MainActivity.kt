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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.beesearch.app.ui.map.CurrentTerritoryScreen
import org.beesearch.app.ui.map.OfflineMapManagementScreen
import org.beesearch.app.ui.area.AreaRoute
import org.beesearch.app.ui.area.AreaViewRoute
import org.beesearch.app.ui.help.HelpScreen
import org.beesearch.app.ui.points.PointDetailRoute
import org.beesearch.app.ui.points.PointsRoute
import org.beesearch.app.ui.objects.ObjectsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import kotlinx.coroutines.delay
import org.beesearch.app.ui.observation.BeeObservationScreen
import org.beesearch.app.ui.observation.ObservationPointPreparationScreen
import org.beesearch.app.ui.settings.SettingsScreen
import org.beesearch.app.ui.territory.TerritoryManagementScreen

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
    val initialSetup by viewModel.visibleInitialSetup.collectAsStateWithLifecycle()
    val setupSettingsSection by viewModel.setupSettingsSection.collectAsStateWithLifecycle()
    // A checklist step whose value does not exist yet opens its existing create form directly; a step
    // that is already ready only brings its section into view. Readiness stays authoritative.
    val initialSetupCreatesMissingValue = (initialSetup as? InitialSetupState.Ready)?.let { state ->
        when (setupSettingsSection) {
            SetupSettingsSection.OBSERVER -> state.observer == null
            SetupSettingsSection.TERRITORY -> state.territory == null
            null -> false
        }
    } ?: false
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val territories by viewModel.territories.collectAsStateWithLifecycle()
    val observers by viewModel.observers.collectAsStateWithLifecycle()
    val currentTerritory by viewModel.currentTerritory.collectAsStateWithLifecycle()
    val currentObserver by viewModel.currentObserver.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val areaEditorRequestToken by viewModel.areaEditorRequestToken.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val observationPointDraft by viewModel.observationPointDraft.collectAsStateWithLifecycle()
    val observationPointPreparationDraft by viewModel.observationPointPreparationDraft.collectAsStateWithLifecycle()
    val completingObservationPointId by viewModel.completingObservationPointId.collectAsStateWithLifecycle()
    val beePreparation by viewModel.beePreparation.collectAsStateWithLifecycle()
    val beeMutationInProgress by viewModel.beeMutationInProgress.collectAsStateWithLifecycle()
    val beeEventInProgressIds by viewModel.beeEventInProgressIds.collectAsStateWithLifecycle()
    val flightAzimuthInProgressIds by viewModel.flightAzimuthInProgressIds.collectAsStateWithLifecycle()
    val resumeRoute = route as? AppRoute.ResumeObservation
    val observationScreenVisible = resumeRoute != null &&
        !beePreparation.isLoading &&
        beePreparation.pointId == resumeRoute.point.id

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

    // Materialise the user-facing exchange tree once per process so the folder exists and is
    // predictable before the user goes looking for it. Idempotent: existing folders are reused.
    LaunchedEffect(application) {
        application.container.exchangeStorage.ensure()
    }

    LaunchedEffect(route) {
        if (route == AppRoute.InitialSetup) viewModel.onAutomaticSetupShown()
    }

    Bee_searchTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val currentRoute = route) {
                    AppRoute.Loading -> LoadingScreen()
                    AppRoute.InitialSetup -> org.beesearch.app.ui.settings.InitialSetupScreen(
                        state = initialSetup,
                        onObserver = { viewModel.openSetupSettings(SetupSettingsSection.OBSERVER) },
                        onTerritory = { viewModel.openSetupSettings(SetupSettingsSection.TERRITORY) },
                        onArea = viewModel::openSetupArea,
                        onMap = viewModel::openSetupArea,
                        onContinue = viewModel::leaveInitialSetup,
                    )
                    AppRoute.Settings -> SettingsScreen(
                        observers = observers,
                        currentObserverId = settings.currentObserverId,
                        territories = territories,
                        currentTerritoryId = settings.currentTerritoryId,
                        onBack = viewModel::returnToStartup,
                        onSelectObserver = viewModel::setCurrentObserver,
                        onCreateObserver = viewModel::createObserver,
                        onUpdateObserver = viewModel::updateObserver,
                        onDeleteObserver = viewModel::deleteObserver,
                        onSelectTerritory = viewModel::setCurrentTerritory,
                        onCreateTerritory = viewModel::createTerritory,
                        onUpdateTerritory = viewModel::updateTerritory,
                        onDeleteTerritory = viewModel::deleteTerritory,
                        onOpenOfflineMaps = viewModel::openOfflineMaps,
                        onOpenHelp = viewModel::openHelp,
                        onOpenInitialSetup = viewModel::openInitialSetup,
                        initialSetupSection = setupSettingsSection,
                        initialSetupCreatesMissingValue = initialSetupCreatesMissingValue,
                    )
                    AppRoute.Help -> HelpScreen(
                        exchangeStorage = application.container.exchangeStorage,
                        onBack = viewModel::returnFromHelp,
                    )
                    AppRoute.Objects -> ObjectsScreen(
                        onBack = viewModel::openCurrentTerritory,
                        onOpenArea = viewModel::openArea,
                        onOpenObservationPoints = viewModel::openPoints,
                    )
                    AppRoute.Area -> AreaRoute(
                        territory = currentTerritory,
                        areaStore = application.container.mapAreaStore,
                        areaMirror = application.container.areaExchangeMirror,
                        areaTransport = application.container.areaTransport,
                        mapPackageStore = application.container.mapPackageStore,
                        mapDiscovery = application.container.areaMapDiscovery,
                        exchangeStorage = application.container.exchangeStorage,
                        onCreate = { viewModel.openAreaSectionsEditor(returnToView = false) },
                        onViewOnMap = viewModel::openAreaView,
                        onBack = viewModel::returnFromSetupDestination,
                    )
                    AppRoute.AreaView -> AreaViewRoute(
                        territory = currentTerritory,
                        mapAreaStore = application.container.mapAreaStore,
                        mapPackageStore = application.container.mapPackageStore,
                        locationState = locationState,
                        locationPermissionGranted = locationPermissionGranted,
                        onRequestLocationPermission = requestLocationPermission,
                        onEditSections = { viewModel.openAreaSectionsEditor(returnToView = true) },
                        onBack = viewModel::openArea,
                    )
                    AppRoute.Points -> PointsRoute(
                        territories = territories,
                        currentTerritoryId = settings.currentTerritoryId,
                        repository = application.container.observationRepository,
                        observationDataMaintenance = application.container.observationDataMaintenance,
                        backupExporter = application.container.backupDocumentExporter,
                        exchangeStorage = application.container.exchangeStorage,
                        mapAreaStore = application.container.mapAreaStore,
                        mapPackageStore = application.container.mapPackageStore,
                        onBack = viewModel::openObjects,
                        onChooseTerritory = viewModel::openTerritoryManagement,
                        onOpenPoint = viewModel::openPointDetail,
                    )
                    is AppRoute.PointDetail -> PointDetailRoute(
                        pointId = currentRoute.pointId,
                        repository = application.container.observationRepository,
                        maintenance = application.container.observationDataMaintenance,
                        fileStore = application.container.attachmentFileStore,
                        weatherScheduler = application.container.weatherSyncScheduler,
                        pointExporter = application.container.observationPointDocumentExporter,
                        exchangeStorage = application.container.exchangeStorage,
                        onBack = { viewModel.closePointDetail(currentRoute) },
                        onDeleted = viewModel::openPoints,
                    )
                    AppRoute.TerritoryManagement -> TerritoryManagementScreen(
                        territories = territories,
                        currentTerritoryId = settings.currentTerritoryId,
                        onBack = viewModel::returnToStartup,
                        onOpenSettings = viewModel::openSettings,
                        onSelectTerritory = viewModel::setCurrentTerritory,
                        onCreateTerritory = viewModel::createTerritory,
                    )
                    AppRoute.OfflineMapManagement -> OfflineMapManagementScreen(
                        territory = currentTerritory,
                        mapAreaStore = application.container.mapAreaStore,
                        mapPackageStore = application.container.mapPackageStore,
                        exchangeStorage = application.container.exchangeStorage,
                        onBack = viewModel::returnToStartup,
                        onEditCoverageOnMap = viewModel::openMapWithCoverageEdit,
                    )
                    AppRoute.CurrentTerritory -> CurrentTerritoryScreen(
                        territory = currentTerritory,
                        mapAreaStore = application.container.mapAreaStore,
                        mapPackageStore = application.container.mapPackageStore,
                        locationState = locationState,
                        observationPointDraft = observationPointDraft,
                        locationPermissionGranted = locationPermissionGranted,
                        onRequestLocationPermission = requestLocationPermission,
                        onRequestCreateRecord = viewModel::requestCreateRecord,
                        onDismissCreateRecordChooser = viewModel::dismissCreateRecordChooser,
                        onCreateObservationPoint = viewModel::createObservationPointFromChooser,
                        onOpenObjects = viewModel::openObjects,
                        onOpenSettings = viewModel::openSettings,
                        onOpenOfflineMaps = viewModel::openOfflineMaps,
                        onOpenTerritories = viewModel::openTerritoryManagement,
                        areaEditorRequest = areaEditorRequestToken,
                        onAreaEditorRequestHandled = viewModel::consumeAreaEditorRequest,
                        onCoverageEditFinished = viewModel::completeAreaSectionsEditing,
                    )
                    AppRoute.PrepareObservationPoint -> observationPointPreparationDraft?.let { draft ->
                        ObservationPointPreparationScreen(
                            draft = draft,
                            fileStore = application.container.attachmentFileStore,
                            onDescriptionChanged = viewModel::updateObservationPointDraftDescription,
                            onImportPhoto = viewModel::stageObservationPointDraftPhoto,
                            onDeletePhoto = viewModel::removeObservationPointDraftPhoto,
                            onConfirmPoint = viewModel::confirmObservationPointPreparation,
                            onRecordNoBeesFound = viewModel::recordNoBeesFoundFromPreparation,
                            onAbort = viewModel::abortObservationPointPreparation,
                        )
                    } ?: LoadingScreen()
                    is AppRoute.ResumeObservation -> {
                        val stateMatchesPoint = beePreparation.pointId == currentRoute.point.id
                        if (beePreparation.isLoading || !stateMatchesPoint) {
                            LoadingScreen()
                        } else {
                            BeeObservationScreen(
                                point = currentRoute.point,
                                bees = beePreparation.bees,
                                flightCycles = beePreparation.flightCycles,
                                isStartingFirstFlight = beeMutationInProgress,
                                beeEventInProgressIds = beeEventInProgressIds,
                                flightAzimuthInProgressIds = flightAzimuthInProgressIds,
                                headingProvider = application.container.headingProvider,
                                feedback = feedback,
                                onDismissFeedback = viewModel::clearFeedback,
                                isCompleting = completingObservationPointId == currentRoute.point.id,
                                onRegisterReturn = viewModel::registerBeeReturn,
                                onStartFirstFlight = { color, position ->
                                    viewModel.startFirstFlight(currentRoute.point.id, color, position)
                                },
                                onStartNextFlight = viewModel::startNextFlight,
                                onRecordNoBeesFound = {
                                    viewModel.recordNoBeesFound(currentRoute.point.id)
                                },
                                onUndoLastBeeAction = viewModel::undoLastBeeAction,
                                onCaptureFlightAzimuth = viewModel::captureFlightAzimuth,
                                onComplete = {
                                    viewModel.completeObservationPoint(currentRoute.point.id)
                                },
                                onOpenPointDetail = {
                                    viewModel.openActivePointDetail(currentRoute.point.id)
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
