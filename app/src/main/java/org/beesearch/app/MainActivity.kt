@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
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
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.CurrentTerritoryScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import java.util.UUID
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
    val observers by viewModel.observers.collectAsStateWithLifecycle()
    val currentTerritory by viewModel.currentTerritory.collectAsStateWithLifecycle()
    val currentObserver by viewModel.currentObserver.collectAsStateWithLifecycle()
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
                        observationPointDraft = observationPointDraft,
                        locationPermissionGranted = locationPermissionGranted,
                        onRequestLocationPermission = requestLocationPermission,
                        onStartObservationPointCreation = viewModel::startObservationPointCreation,
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
    observers: List<Observer>,
    currentObserverId: UUID?,
    territories: List<Territory>,
    currentTerritoryId: UUID?,
    onBack: () -> Unit,
    onSelectObserver: (UUID) -> Unit,
    onCreateObserver: (String, String, String, String, String) -> Unit,
    onUpdateObserver: (Observer) -> Unit = {},
    onDeleteObserver: (Observer) -> Unit = {},
    onSelectTerritory: (UUID) -> Unit,
    onCreateTerritory: (String, String, String, String) -> Unit,
    onUpdateTerritory: (Territory) -> Unit = {},
    onDeleteTerritory: (Territory) -> Unit = {},
) {
    var addingObserver by rememberSaveable { mutableStateOf(false) }
    var addingTerritory by rememberSaveable { mutableStateOf(false) }
    var editingObserver by remember { mutableStateOf<Observer?>(null) }
    var editingTerritory by remember { mutableStateOf<Territory?>(null) }
    var deleteObserver by remember { mutableStateOf<Observer?>(null) }
    var deleteTerritory by remember { mutableStateOf<Territory?>(null) }
    var observerCode by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var middleName by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var territoryCode by rememberSaveable { mutableStateOf("") }
    var territoryName by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
            TopAppBar(title = { Text("Настройки") }, navigationIcon = {
                TextButton(onClick = {
                    if (addingObserver || addingTerritory || editingObserver != null || editingTerritory != null) {
                        addingObserver = false
                        addingTerritory = false
                        editingObserver = null
                        editingTerritory = null
                    } else onBack()
                }) { Text("Назад") }
            })
        },
    ) { padding ->
        BackHandler(enabled = addingObserver || addingTerritory || editingObserver != null || editingTerritory != null) {
            addingObserver = false; addingTerritory = false; editingObserver = null; editingTerritory = null
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Наблюдатель", style = MaterialTheme.typography.titleMedium)
            if (observers.isEmpty()) Text("Наблюдателей пока нет.")
            observers.forEach { observer ->
                ObserverRow(
                    observer, observer.id == currentObserverId,
                    onSelect = { onSelectObserver(observer.id) },
                    onEdit = { editingObserver = observer; addingObserver = false },
                    onDelete = { deleteObserver = observer },
                )
            }
            TextButton(onClick = { addingObserver = !addingObserver }) {
                Text(if (addingObserver) "Скрыть форму наблюдателя" else "+ Добавить наблюдателя")
            }
            if (addingObserver || editingObserver != null) {
                val edit = editingObserver
                Text(if (edit == null) "Добавить наблюдателя" else "Изменить наблюдателя", style = MaterialTheme.typography.titleSmall)
                val codeValue = edit?.code ?: observerCode
                val lastValue = edit?.lastName ?: lastName
                val firstValue = edit?.firstName ?: firstName
                val middleValue = edit?.middleName ?: middleName
                val contactValue = edit?.contact ?: contact
                OutlinedTextField(codeValue, { if (edit == null) observerCode = it else editingObserver = edit.copy(code = it) }, Modifier.fillMaxWidth(), label = { Text("Код") }, singleLine = true)
                OutlinedTextField(lastValue, { if (edit == null) lastName = it else editingObserver = edit.copy(lastName = it) }, Modifier.fillMaxWidth(), label = { Text("Фамилия") }, singleLine = true)
                OutlinedTextField(firstValue, { if (edit == null) firstName = it else editingObserver = edit.copy(firstName = it) }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(middleValue, { if (edit == null) middleName = it else editingObserver = edit.copy(middleName = it) }, Modifier.fillMaxWidth(), label = { Text("Отчество (необязательно)") }, singleLine = true)
                OutlinedTextField(contactValue, { if (edit == null) contact = it else editingObserver = edit.copy(contact = it) }, Modifier.fillMaxWidth(), label = { Text("Контакт (необязательно)") })
                Button(onClick = {
                    if (edit == null) { onCreateObserver(observerCode, lastName, firstName, middleName, contact); addingObserver = false }
                    else { onUpdateObserver(edit); editingObserver = null }
                }, enabled = codeValue.isNotBlank() && lastValue.isNotBlank() && firstValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (edit == null) "Добавить наблюдателя" else "Сохранить") }
            }
            HorizontalDivider()
            Text("Территории", style = MaterialTheme.typography.titleMedium)
            if (territories.isEmpty()) Text("Территорий пока нет.")
            territories.forEach { territory ->
                TerritoryRow(territory, territory.id == currentTerritoryId, { onSelectTerritory(territory.id) }, { editingTerritory = territory; addingTerritory = false }, { deleteTerritory = territory })
            }
            TextButton(onClick = { addingTerritory = !addingTerritory }) {
                Text(if (addingTerritory) "Скрыть форму территории" else "+ Добавить территорию")
            }
            if (addingTerritory || editingTerritory != null) {
                val edit = editingTerritory
                Text(if (edit == null) "Добавить территорию" else "Изменить территорию", style = MaterialTheme.typography.titleSmall)
                val codeValue = edit?.code ?: territoryCode; val nameValue = edit?.name ?: territoryName; val regionValue = edit?.region ?: region; val districtValue = edit?.district ?: district
                OutlinedTextField(codeValue, { if (edit == null) territoryCode = it else editingTerritory = edit.copy(code = it) }, Modifier.fillMaxWidth(), label = { Text("Код") }, singleLine = true)
                OutlinedTextField(nameValue, { if (edit == null) territoryName = it else editingTerritory = edit.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                OutlinedTextField(regionValue, { if (edit == null) region = it else editingTerritory = edit.copy(region = it) }, Modifier.fillMaxWidth(), label = { Text("Область / регион") }, singleLine = true)
                OutlinedTextField(districtValue, { if (edit == null) district = it else editingTerritory = edit.copy(district = it) }, Modifier.fillMaxWidth(), label = { Text("Район") }, singleLine = true)
                Button(onClick = {
                    if (edit == null) { onCreateTerritory(territoryCode, territoryName, region, district); addingTerritory = false }
                    else { onUpdateTerritory(edit); editingTerritory = null }
                }, enabled = codeValue.isNotBlank() && nameValue.isNotBlank() && regionValue.isNotBlank() && districtValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (edit == null) "Добавить территорию" else "Сохранить") }
            }
        }
    }
    deleteObserver?.let { observer -> AlertDialog(onDismissRequest = { deleteObserver = null }, title = { Text("Удалить наблюдателя?") }, text = { Text(observer.displayName) }, confirmButton = { TextButton(onClick = { onDeleteObserver(observer); deleteObserver = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteObserver = null }) { Text("Отмена") } }) }
    deleteTerritory?.let { territory -> AlertDialog(onDismissRequest = { deleteTerritory = null }, title = { Text("Удалить территорию?") }, text = { Text("${territory.code} — ${territory.name}") }, confirmButton = { TextButton(onClick = { onDeleteTerritory(territory); deleteTerritory = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteTerritory = null }) { Text("Отмена") } }) }
}

@Composable
private fun ObserverRow(observer: Observer, isCurrent: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isCurrent, onClick = onSelect)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text((if (isCurrent) "● " else "○ ") + observer.displayName, fontWeight = FontWeight.Bold)
            Text("Код: ${observer.code}")
            observer.contact?.let { Text("Контакт: $it") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Изменить") }
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
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
    onCreateTerritory: (String, String, String, String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
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
            OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("Область / регион") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(district, { district = it }, Modifier.fillMaxWidth(), label = { Text("Район") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onCreateTerritory(code, name, region, district) },
                enabled = code.isNotBlank() && name.isNotBlank() && region.isNotBlank() && district.isNotBlank(),
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
private fun TerritoryRow(territory: Territory, isCurrent: Boolean, onSelect: () -> Unit, onEdit: () -> Unit = {}, onDelete: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(territory.code, fontWeight = FontWeight.Bold)
            Text(territory.name)
            Text(territory.region)
            Text(territory.district)
            if (isCurrent) {
                Text("Текущая территория", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onSelect) { Text("Сделать текущей") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = onEdit) { Text("Изменить") }; TextButton(onClick = onDelete) { Text("Удалить") } }
        }
    }
}


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
                region = "Владимирская область",
                district = "Гороховецкий район",
                createdAt = java.time.Instant.EPOCH,
                updatedAt = java.time.Instant.EPOCH,
            ),
            isCurrent = true,
            onSelect = {},
        )
    }
}
