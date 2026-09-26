package org.beesearch.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.AzimuthCaptureAlreadyConsumedException
import org.beesearch.app.domain.model.AzimuthCaptureRequiresOpenFlightCycleException
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeLimitReachedException
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.DuplicateTerritoryCodeException
import org.beesearch.app.domain.model.DuplicateObserverCodeException
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.InvalidEventTimeException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NoBeesFoundAlreadyRecordedException
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.ObserverRequiredException
import org.beesearch.app.domain.model.RequiredFieldException
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.model.ObserverInUseException
import org.beesearch.app.domain.model.TerritoryInUseException
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.location.LocationProvider
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.ObserverRepository
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.domain.usecase.CreateObservationPoint
import org.beesearch.app.domain.usecase.StartupDestination
import org.beesearch.app.domain.usecase.StartupRouter
import org.beesearch.app.ui.map.AreaEditorRequest
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageStore
import org.beesearch.app.ui.map.coverageFragments
import java.util.UUID
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.StagedObservationPointPhoto

sealed interface AppRoute {
    data object Loading : AppRoute
    data object Settings : AppRoute
    data object InitialSetup : AppRoute
    data object Help : AppRoute
    data object Objects : AppRoute
    data object Area : AppRoute
    data object AreaView : AppRoute
    data object Points : AppRoute
    data class PointDetail(
        val pointId: UUID,
        /** Where the screen was opened from, so Back returns there. */
        val origin: PointDetailOrigin = PointDetailOrigin.POINTS,
    ) : AppRoute
    data object TerritoryManagement : AppRoute
    data object OfflineMapManagement : AppRoute
    data class CurrentTerritory(
        /**
         * The Physical Object card the map was opened from, so Back returns to that card.
         *
         * The origin travels with the route, so leaving the map by any other navigation drops it and an
         * ordinary map opening can never return to an old object card.
         */
        val returnToObject: PhysicalObjectCardReturn? = null,
    ) : AppRoute
    /** The Дупла or Колоды list of one object type; the top level shows categories, not instances. */
    data class PhysicalObjectList(val type: PhysicalObjectType) : AppRoute
    data class CreatePhysicalObject(val target: PhysicalObjectCreationTarget) : AppRoute
    data class PhysicalObjectDetail(
        val objectId: UUID,
        /** The typed list this card was opened from, so Back returns to the right list. */
        val listType: PhysicalObjectType,
        val coordinateUpdate: PhysicalObjectCoordinateUpdate? = null,
    ) : AppRoute
    data object PrepareObservationPoint : AppRoute
    data class ResumeObservation(val point: ObservationPoint) : AppRoute
}

internal sealed interface PhysicalObjectLocationSelection {
    val label: String

    data class Create(
        val type: PhysicalObjectType,
        val territoryId: UUID,
        val observerId: UUID,
    ) : PhysicalObjectLocationSelection {
        override val label: String get() = when (type) {
            PhysicalObjectType.HOLLOW -> "Дупло"
            PhysicalObjectType.LOG_HIVE -> "Колода"
            PhysicalObjectType.APIARY -> error("Apiary creation is not part of this flow")
        }
    }

    data class Edit(
        val objectId: UUID,
        val listType: PhysicalObjectType,
        override val label: String,
    ) : PhysicalObjectLocationSelection
}

/** The Physical Object card the map was opened from. */
data class PhysicalObjectCardReturn(
    val objectId: UUID,
    val listType: PhysicalObjectType,
)

data class PhysicalObjectCoordinateUpdate(
    val requestId: UUID,
    val latitude: Double,
    val longitude: Double,
)

internal data class MapCenterRequest(
    val requestId: UUID,
    val target: MapTarget,
)

data class PhysicalObjectCreationTarget(
    val requestId: UUID,
    val type: PhysicalObjectType,
    val territoryId: UUID,
    val observerId: UUID,
    val latitude: Double,
    val longitude: Double,
)

/** Origin of the single ObservationPoint screen. */
enum class PointDetailOrigin { POINTS, OBSERVATION }

data class BeePreparationUiState(
    val pointId: UUID? = null,
    val bees: List<Bee> = emptyList(),
    val flightCycles: List<FlightCycle> = emptyList(),
    val beePresenceResult: BeePresenceResult? = null,
    val isLoading: Boolean = true,
)

internal enum class FeedbackDisplayMode {
    AUTO_DISMISS,
    PERSISTENT,
}

internal data class UiFeedback(
    val id: Long,
    val message: String,
    val displayMode: FeedbackDisplayMode,
)

internal sealed interface InitialSetupState {
    val generation: Int
    data class Loading(
        val activePoint: ObservationPoint? = null,
        override val generation: Int = 0,
    ) : InitialSetupState
    data class Ready(
        val activePoint: ObservationPoint?,
        val observer: Observer?,
        val territory: Territory?,
        val area: MapAreaReadResult,
        val map: MapPackageAvailability?,
        val offerHandled: Boolean,
        override val generation: Int = 0,
    ) : InitialSetupState {
        val complete: Boolean get() = observer != null && territory != null &&
            area is MapAreaReadResult.Present && map is MapPackageAvailability.Ready
    }
}

internal enum class SetupSettingsSection { OBSERVER, TERRITORY }

private data class InitialSetupFacts(
    val activePoint: ObservationPoint?,
    val settings: AppSettings,
    val territories: List<Territory>,
    val observers: List<Observer>,
)

internal fun startupDestinationFor(state: InitialSetupState): StartupDestination = when (state) {
    is InitialSetupState.Loading -> state.activePoint?.let(StartupDestination::ResumeObservation)
        ?: StartupDestination.Loading
    is InitialSetupState.Ready -> StartupRouter.decide(
        activePoint = state.activePoint,
        currentTerritoryId = state.territory?.id,
        territories = listOfNotNull(state.territory),
        currentObserverId = state.observer?.id,
        observers = listOfNotNull(state.observer),
        setupComplete = state.complete,
        offerHandled = state.offerHandled,
    )
}

internal fun visibleInitialSetupFor(state: InitialSetupState, generation: Int): InitialSetupState =
    if (state.generation == generation) state else InitialSetupState.Loading(generation = generation)

/**
 * Where a saved Observer or Territory returns after it was opened from the Initial Setup checklist.
 *
 * A step that the checklist opened returns to the checklist, so the recalculated readiness is visible.
 * The same step opened outside the checklist keeps the ordinary startup route.
 */
internal fun setupStepReturnRoute(setupStepPending: Boolean): AppRoute? =
    if (setupStepPending) AppRoute.InitialSetup else null

/** The same contextual return for a setup destination that has its own ordinary destination. */
internal fun setupDestinationReturnRoute(setupStepPending: Boolean): AppRoute =
    if (setupStepPending) AppRoute.InitialSetup else AppRoute.Objects

internal class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val territoryRepository: TerritoryRepository,
    private val observerRepository: ObserverRepository,
    private val observationRepository: ObservationRepository,
    private val createObservationPoint: CreateObservationPoint,
    private val attachmentFileStore: ObservationAttachmentFileStore,
    private val locationProvider: LocationProvider,
    private val territoryCoverageDeletion: TerritoryCoverageDeletion,
    private val mapAreaStore: MapAreaStore,
    private val mapPackageStore: MapPackageStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val manualRoute = MutableStateFlow<AppRoute?>(null)
    private val setupRefresh = MutableStateFlow(0)
    private val _setupSettingsSection = MutableStateFlow<SetupSettingsSection?>(null)
    val setupSettingsSection: StateFlow<SetupSettingsSection?> = _setupSettingsSection.asStateFlow()
    private var setupReturnPending = false

    /**
     * The pending request to open the участки editor.
     *
     * It is a one-shot command: the map that handles it consumes it, so a later return to the map
     * cannot replay an old request and open the editor without a user action.
     */
    private val areaEditorRequest = AreaEditorRequest()
    private val _feedback = MutableStateFlow<UiFeedback?>(null)
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.PermissionRequired)
    private val _observationPointDraft = MutableStateFlow<ObservationPointCreationDraft?>(null)
    private val _physicalObjectLocationSelection =
        MutableStateFlow<PhysicalObjectLocationSelection?>(null)
    private val _mapCenterRequest = MutableStateFlow<MapCenterRequest?>(null)
    private val _observationPointPreparationDraft = MutableStateFlow<ObservationPointPreparationDraft?>(null)
    private val _completingObservationPointId = MutableStateFlow<UUID?>(null)
    private val _beeMutationInProgress = MutableStateFlow(false)
    private val _beeEventInProgressIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val _flightAzimuthInProgressIds = MutableStateFlow<Set<UUID>>(emptySet())
    private var locationJob: kotlinx.coroutines.Job? = null
    private var nextFeedbackId = 0L

    /** Where the участки editor should return to once the Ареал workflow opened it. */
    private var areaEditReturnRoute: AppRoute? = null

    val feedback: StateFlow<UiFeedback?> = _feedback.asStateFlow()
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()
    val observationPointDraft: StateFlow<ObservationPointCreationDraft?> =
        _observationPointDraft.asStateFlow()
    val physicalObjectLocationSelection: StateFlow<PhysicalObjectLocationSelection?> =
        _physicalObjectLocationSelection.asStateFlow()
    internal val mapCenterRequest: StateFlow<MapCenterRequest?> = _mapCenterRequest.asStateFlow()
    val observationPointPreparationDraft: StateFlow<ObservationPointPreparationDraft?> =
        _observationPointPreparationDraft.asStateFlow()
    val completingObservationPointId: StateFlow<UUID?> = _completingObservationPointId.asStateFlow()
    val beeMutationInProgress: StateFlow<Boolean> = _beeMutationInProgress.asStateFlow()
    val beeEventInProgressIds: StateFlow<Set<UUID>> = _beeEventInProgressIds.asStateFlow()
    val flightAzimuthInProgressIds: StateFlow<Set<UUID>> =
        _flightAzimuthInProgressIds.asStateFlow()
    val areaEditorRequestToken: StateFlow<Int> = areaEditorRequest.token
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(currentTerritoryId = null, currentObserverId = null),
    )
    val territories: StateFlow<List<Territory>> = territoryRepository.observeTerritories().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val observers: StateFlow<List<Observer>> = observerRepository.observeObservers().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val activePoint: StateFlow<ObservationPoint?> = observationRepository.observeActivePoint().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    val beePreparation: StateFlow<BeePreparationUiState> = activePoint.flatMapLatest { point ->
        if (point == null) {
            flowOf(BeePreparationUiState(isLoading = false))
        } else {
            combine(
                observationRepository.observeBees(point.id),
                observationRepository.observeFlightCyclesForPoint(point.id),
            ) { bees, flightCycles ->
                BeePreparationUiState(
                    pointId = point.id,
                    bees = bees,
                    flightCycles = flightCycles,
                    beePresenceResult = point.beePresenceResult,
                    isLoading = false,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeePreparationUiState())
    val currentTerritory: StateFlow<Territory?> = combine(settings, territories) { appSettings, allTerritories ->
        appSettings.currentTerritoryId?.let { id -> allTerritories.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currentObserver: StateFlow<Observer?> = combine(settings, observers) { appSettings, allObservers ->
        appSettings.currentObserverId?.let { id -> allObservers.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val setupFacts = combine(
        observationRepository.observeActivePoint(),
        settingsRepository.settings,
        territoryRepository.observeTerritories(),
        observerRepository.observeObservers(),
    ) { point, appSettings, allTerritories, allObservers ->
        InitialSetupFacts(point, appSettings, allTerritories, allObservers)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val initialSetup: StateFlow<InitialSetupState> = combine(setupFacts, setupRefresh) { facts, generation ->
        facts to generation
    }.flatMapLatest { (facts, generation) ->
            flow {
                emit(InitialSetupState.Loading(facts.activePoint, generation))
                val territory = facts.settings.currentTerritoryId?.let { id ->
                    facts.territories.firstOrNull { it.id == id }
                }
                val observer = facts.settings.currentObserverId?.let { id ->
                    facts.observers.firstOrNull { it.id == id }
                }
                val area = if (territory == null) MapAreaReadResult.Absent else try {
                    mapAreaStore.load(territory.id, territory.name)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    MapAreaReadResult.Corrupt("Не удалось прочитать ареал")
                }
                val map = if (territory != null && area is MapAreaReadResult.Present) try {
                    mapPackageStore.loadActive(territory.id, area.area.coverageFragments())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    MapPackageAvailability.Unavailable("Не удалось проверить офлайн-карту")
                } else null
                emit(InitialSetupState.Ready(facts.activePoint, observer, territory, area, map,
                    facts.settings.initialSetupOfferHandled, generation))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialSetupState.Loading())
    val visibleInitialSetup: StateFlow<InitialSetupState> = combine(initialSetup, setupRefresh) { state, generation ->
        visibleInitialSetupFor(state, generation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialSetupState.Loading())
    val startupDestination: StateFlow<StartupDestination> = initialSetup.map(::startupDestinationFor)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupDestination.Loading)
    val route: StateFlow<AppRoute> = combine(startupDestination, manualRoute) { destination, manual ->
        manual ?: destination.toRoute()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Loading)

    fun openSettings() {
        _physicalObjectLocationSelection.value = null
        _mapCenterRequest.value = null
        setupReturnPending = false
        _setupSettingsSection.value = null
        manualRoute.value = AppRoute.Settings
        clearFeedback()
    }

    fun openHelp() {
        manualRoute.value = AppRoute.Help
        clearFeedback()
    }

    fun returnFromHelp() {
        manualRoute.value = AppRoute.Settings
        clearFeedback()
    }

    fun openObjects() {
        _physicalObjectLocationSelection.value = null
        _mapCenterRequest.value = null
        setupReturnPending = false
        manualRoute.value = AppRoute.Objects
        clearFeedback()
    }

    fun openPoints() {
        setupReturnPending = false
        manualRoute.value = AppRoute.Points
        clearFeedback()
    }

    fun openArea() {
        manualRoute.value = AppRoute.Area
        clearFeedback()
    }

    /** The Ареал on a clean map: geometry without the editor. */
    fun openAreaView() {
        manualRoute.value = AppRoute.AreaView
        clearFeedback()
    }

    /**
     * Opens the участки editor for the Ареал workflow.
     *
     * The Ареал screen and the Ареал view are two different places a user can start editing from, so
     * the origin is remembered and [completeAreaSectionsEditing] returns there. Other entry points to
     * the same editor (the map's own coverage button, the offline-map screen) record no origin and
     * keep the previous behaviour of staying on the map.
     *
     * This is the only kind of call that may open the editor: ordinary navigation never requests it.
     */
    fun openAreaSectionsEditor(returnToView: Boolean) {
        areaEditReturnRoute = if (returnToView) AppRoute.AreaView else AppRoute.Area
        manualRoute.value = AppRoute.CurrentTerritory()
        areaEditorRequest.request()
        clearFeedback()
    }

    /** The editor session ended: go back to where the Ареал workflow started, if it did. */
    fun completeAreaSectionsEditing() {
        val origin = areaEditReturnRoute ?: return
        areaEditReturnRoute = null
        manualRoute.value = origin
        clearFeedback()
    }

    /**
     * The map opened the editor for the pending request, so the request is finished.
     *
     * Without this the request would stay pending and every later entry to the map would treat it as a
     * fresh command and reopen the editor on its own.
     */
    fun consumeAreaEditorRequest() {
        areaEditorRequest.consume()
    }

    /** Opens the point screen from the Points browser. */
    fun openPointDetail(pointId: UUID) {
        manualRoute.value = AppRoute.PointDetail(pointId, PointDetailOrigin.POINTS)
        clearFeedback()
    }

    /** Opens the same point screen for the active observation; Back returns to the observation. */
    fun openActivePointDetail(pointId: UUID) {
        manualRoute.value = AppRoute.PointDetail(pointId, PointDetailOrigin.OBSERVATION)
        clearFeedback()
    }

    fun closePointDetail(route: AppRoute.PointDetail) {
        manualRoute.value = when (route.origin) {
            PointDetailOrigin.POINTS -> AppRoute.Points
            PointDetailOrigin.OBSERVATION -> activePoint.value?.let(AppRoute::ResumeObservation)
                ?: AppRoute.CurrentTerritory()
        }
        clearFeedback()
    }

    fun openTerritoryManagement() {
        manualRoute.value = AppRoute.TerritoryManagement
        clearFeedback()
    }

    fun openOfflineMaps() {
        manualRoute.value = AppRoute.OfflineMapManagement
        clearFeedback()
    }

    fun openMapWithCoverageEdit() {
        setupReturnPending = false
        manualRoute.value = AppRoute.CurrentTerritory()
        areaEditorRequest.request()
        clearFeedback()
    }

    fun openCurrentTerritory() {
        setupReturnPending = false
        manualRoute.value = AppRoute.CurrentTerritory()
        clearFeedback()
    }

    fun openResumeObservation(point: ObservationPoint) {
        manualRoute.value = AppRoute.ResumeObservation(point)
        clearFeedback()
    }

    fun returnToStartup() {
        returnToSetupOrStartup()
        clearFeedback()
    }

    private fun returnToSetupOrStartup() {
        if (setupReturnPending) setupRefresh.value += 1
        manualRoute.value = setupStepReturnRoute(setupReturnPending)
    }

    fun openInitialSetup() {
        setupReturnPending = false
        setupRefresh.value += 1
        manualRoute.value = AppRoute.InitialSetup
        clearFeedback()
    }

    /**
     * The automatic offer is on screen: pin its route while authoritative facts settle.
     *
     * The device-local offer flag is deliberately not written here. It means "the user handled the
     * offer", so writing it while merely showing the checklist made the next launch skip the offer and
     * fall through to the map's territory blocker. Only [leaveInitialSetup] handles the offer.
     */
    fun onAutomaticSetupShown() {
        if (manualRoute.value != null) return
        manualRoute.value = AppRoute.InitialSetup
    }

    fun leaveInitialSetup() {
        setupReturnPending = false
        viewModelScope.launch {
            settingsRepository.setInitialSetupOfferHandled(true)
            // Keep the map explicit until the DataStore Flow reaches startup routing: dropping
            // the override immediately could briefly replay the old automatic setup route.
            manualRoute.value = AppRoute.CurrentTerritory()
        }
    }

    fun openSetupSettings(section: SetupSettingsSection) {
        setupReturnPending = true
        _setupSettingsSection.value = section
        manualRoute.value = AppRoute.Settings
    }

    fun openSetupArea() {
        setupReturnPending = true
        manualRoute.value = AppRoute.Area
    }

    fun returnFromSetupDestination() {
        if (setupReturnPending) setupRefresh.value += 1
        manualRoute.value = setupDestinationReturnRoute(setupReturnPending)
    }

    fun setCurrentTerritory(territoryId: UUID) {
        launchOperation {
            settingsRepository.setCurrentTerritoryId(territoryId)
            returnToSetupOrStartup()
            showSuccessFeedback("Текущая территория изменена")
        }
    }

    fun setCurrentObserver(observerId: UUID) {
        launchOperation {
            settingsRepository.setCurrentObserverId(observerId)
            returnToSetupOrStartup()
            showSuccessFeedback("Текущий наблюдатель изменён")
        }
    }

    fun createTerritory(code: String, name: String, region: String, district: String) {
        val validationError = when {
            code.isBlank() -> "Введите код территории"
            name.isBlank() -> "Введите название территории"
            region.isBlank() -> "Введите область или регион"
            district.isBlank() -> "Введите район"
            territories.value.any { it.code.trim() == code.trim() } -> "Территория с таким кодом уже существует"
            else -> null
        }
        if (validationError != null) {
            showPersistentFeedback(validationError)
            return
        }

        launchOperation {
            val territory = territoryRepository.createTerritory(code, name, region, district)
            settingsRepository.setCurrentTerritoryId(territory.id)
            returnToSetupOrStartup()
            showSuccessFeedback("Территория создана и выбрана текущей")
        }
    }

    fun createObserver(
        code: String,
        lastName: String,
        firstName: String,
        middleName: String,
        contact: String,
    ) {
        val validationError = when {
            code.isBlank() -> "Введите код наблюдателя"
            lastName.isBlank() -> "Введите фамилию"
            firstName.isBlank() -> "Введите имя"
            observers.value.any { it.code.trim() == code.trim() } -> "Наблюдатель с таким кодом уже существует"
            else -> null
        }
        if (validationError != null) {
            showPersistentFeedback(validationError)
            return
        }
        launchOperation {
            val observer = observerRepository.createObserver(
                code, lastName, firstName, middleName, contact,
            )
            settingsRepository.setCurrentObserverId(observer.id)
            returnToSetupOrStartup()
            showSuccessFeedback("Наблюдатель создан и выбран текущим")
        }
    }

    fun updateObserver(observer: Observer) {
        val validationError = validateObserver(observer.code, observer.lastName, observer.firstName, observer.id)
        if (validationError != null) { showPersistentFeedback(validationError); return }
        launchOperation { observerRepository.updateObserver(observer) ; showSuccessFeedback("Наблюдатель изменён") }
    }

    fun deleteObserver(observer: Observer) {
        launchOperation {
            observerRepository.deleteObserver(observer.id)
            if (settings.value.currentObserverId == observer.id) settingsRepository.setCurrentObserverId(null)
            showSuccessFeedback("Наблюдатель удалён")
        }
    }

    fun updateTerritory(territory: Territory) {
        val validationError = validateTerritory(territory.code, territory.name, territory.region, territory.district, territory.id)
        if (validationError != null) { showPersistentFeedback(validationError); return }
        launchOperation { territoryRepository.updateTerritory(territory); showSuccessFeedback("Территория изменена") }
    }

    fun deleteTerritory(territory: Territory) {
        launchOperation {
            territoryCoverageDeletion.delete(territory.id)
            if (settings.value.currentTerritoryId == territory.id) settingsRepository.setCurrentTerritoryId(null)
            showSuccessFeedback("Территория удалена")
        }
    }

    fun requestCreateRecord(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return
        val territory = currentTerritory.value
        if (territory == null) {
            showPersistentFeedback("Сначала выберите текущую территорию")
            return
        }
        val observer = currentObserver.value
        if (observer == null) {
            showPersistentFeedback("Сначала выберите текущего наблюдателя")
            return
        }
        val reading = (locationState.value as? LocationUiState.Available)?.reading
        if (reading == null) {
            showPersistentFeedback("Дождитесь GPS-позиции")
            return
        }

        val draft = ObservationPointCreationDraft(
            territoryId = territory.id,
            observerId = observer.id,
            originalGps = reading,
        ).withSelectedMapCenter(MapTarget(latitude, longitude))
        _observationPointDraft.value = draft
        clearFeedback()
    }

    fun dismissCreateRecordChooser() {
        _observationPointDraft.value = null
        clearFeedback()
    }

    fun createObservationPointFromChooser() {
        val existingPoint = activePoint.value
        if (existingPoint != null) {
            _observationPointDraft.value = null
            openResumeObservation(existingPoint)
            return
        }
        val draft = _observationPointDraft.value ?: return
        _observationPointDraft.value = null
        _observationPointPreparationDraft.value = ObservationPointPreparationDraft(
            point = draft.toNewObservationPoint(),
        )
        manualRoute.value = AppRoute.PrepareObservationPoint
        clearFeedback()
    }

    fun createHollowFromChooser() = startPhysicalObjectLocationSelection(PhysicalObjectType.HOLLOW)

    fun createLogHiveFromChooser() = startPhysicalObjectLocationSelection(PhysicalObjectType.LOG_HIVE)

    private fun startPhysicalObjectLocationSelection(type: PhysicalObjectType) {
        val draft = _observationPointDraft.value ?: return
        _observationPointDraft.value = null
        _physicalObjectLocationSelection.value = PhysicalObjectLocationSelection.Create(
            type = type,
            territoryId = draft.territoryId,
            observerId = draft.observerId,
        )
        clearFeedback()
    }

    fun confirmPhysicalObjectLocation(latitude: Double, longitude: Double) {
        val selection = _physicalObjectLocationSelection.value ?: return
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return
        _physicalObjectLocationSelection.value = null
        manualRoute.value = when (selection) {
            is PhysicalObjectLocationSelection.Create -> AppRoute.CreatePhysicalObject(
                PhysicalObjectCreationTarget(
                    requestId = UUID.randomUUID(),
                    type = selection.type,
                    territoryId = selection.territoryId,
                    observerId = selection.observerId,
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
            is PhysicalObjectLocationSelection.Edit -> AppRoute.PhysicalObjectDetail(
                objectId = selection.objectId,
                listType = selection.listType,
                coordinateUpdate = PhysicalObjectCoordinateUpdate(
                    requestId = UUID.randomUUID(),
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        }
        clearFeedback()
    }

    fun cancelPhysicalObjectLocationSelection() {
        val selection = _physicalObjectLocationSelection.value
        _physicalObjectLocationSelection.value = null
        _mapCenterRequest.value = null
        if (selection is PhysicalObjectLocationSelection.Edit) {
            manualRoute.value = AppRoute.PhysicalObjectDetail(selection.objectId, selection.listType)
        }
        clearFeedback()
    }

    fun editPhysicalObjectCoordinates(objectId: UUID, designation: String, latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return
        _physicalObjectLocationSelection.value = PhysicalObjectLocationSelection.Edit(
            objectId = objectId,
            listType = physicalObjectCardOnScreen()?.listType ?: PhysicalObjectType.HOLLOW,
            label = designation,
        )
        _mapCenterRequest.value = MapCenterRequest(UUID.randomUUID(), MapTarget(latitude, longitude))
        manualRoute.value = AppRoute.CurrentTerritory()
        clearFeedback()
    }

    /**
     * Opens the map centred on this object and remembers the card it was opened from, so Back returns
     * to that card. Every other way of opening the map produces a route without this origin.
     */
    fun showPhysicalObjectOnMap(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return
        val card = physicalObjectCardOnScreen()?.let { PhysicalObjectCardReturn(it.objectId, it.listType) }
        _physicalObjectLocationSelection.value = null
        _mapCenterRequest.value = MapCenterRequest(UUID.randomUUID(), MapTarget(latitude, longitude))
        manualRoute.value = AppRoute.CurrentTerritory(returnToObject = card)
        clearFeedback()
    }

    /** Back on a map opened from a Physical Object card returns to that card. */
    fun returnFromPhysicalObjectMap() {
        val card = (manualRoute.value as? AppRoute.CurrentTerritory)?.returnToObject ?: return
        manualRoute.value = AppRoute.PhysicalObjectDetail(card.objectId, card.listType)
        clearFeedback()
    }

    fun consumeMapCenterRequest(requestId: UUID) {
        if (_mapCenterRequest.value?.requestId == requestId) _mapCenterRequest.value = null
    }

    fun consumePhysicalObjectCoordinateUpdate(requestId: UUID) {
        val route = manualRoute.value as? AppRoute.PhysicalObjectDetail ?: return
        if (route.coordinateUpdate?.requestId == requestId) {
            manualRoute.value = AppRoute.PhysicalObjectDetail(route.objectId, route.listType)
        }
    }

    fun closePhysicalObjectCreation() {
        manualRoute.value = AppRoute.CurrentTerritory()
        clearFeedback()
    }

    /** Opens the Дупла or Колоды list of a single object type. */
    fun openPhysicalObjectList(type: PhysicalObjectType) {
        manualRoute.value = AppRoute.PhysicalObjectList(type)
        clearFeedback()
    }

    fun closePhysicalObjectList() {
        manualRoute.value = AppRoute.Objects
        clearFeedback()
    }

    fun openPhysicalObjectDetail(objectId: UUID, listType: PhysicalObjectType) {
        manualRoute.value = AppRoute.PhysicalObjectDetail(objectId, listType)
        clearFeedback()
    }

    /** Back from a card returns to the typed list it was opened from, not straight to `Объекты`. */
    fun closePhysicalObjectDetail() {
        manualRoute.value = physicalObjectCardOnScreen()
            ?.let { AppRoute.PhysicalObjectList(it.listType) }
            ?: AppRoute.Objects
        clearFeedback()
    }

    /**
     * The Physical Object card the user is on.
     *
     * Card actions read their list context from the route itself, so no second copy of the object or
     * list state is kept and an ordinary map opening cannot inherit it.
     */
    private fun physicalObjectCardOnScreen(): AppRoute.PhysicalObjectDetail? =
        manualRoute.value as? AppRoute.PhysicalObjectDetail

    fun abortObservationPointPreparation() {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving || draft.isPhotoSaving) return
        _observationPointPreparationDraft.value = draft.copy(isSaving = true)
        viewModelScope.launch {
            if (attachmentFileStore.discardDraft(draft.draftSessionId)) {
                _observationPointPreparationDraft.value = null
                manualRoute.value = null
                clearFeedback()
            } else {
                _observationPointPreparationDraft.value = draft
                showPersistentFeedback("Не удалось удалить временные фотографии")
            }
        }
    }

    fun updateObservationPointDraftDescription(value: String) {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving || draft.isPhotoSaving) return
        _observationPointPreparationDraft.value = draft.copy(description = value)
    }

    fun stageObservationPointDraftPhoto(
        source: () -> InputStream,
        originalFileName: String?,
        mimeType: String?,
        onComplete: (() -> Unit)? = null,
    ) {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving || draft.isPhotoSaving) return
        _observationPointPreparationDraft.value = draft.copy(isPhotoSaving = true)
        viewModelScope.launch {
            runCatching {
                attachmentFileStore.stageDraftPhoto(
                    draftSessionId = draft.draftSessionId,
                    attachmentId = UUID.randomUUID(),
                    originalFileName = originalFileName,
                    mimeType = mimeType,
                    createdAt = Instant.now(clock),
                    source = source,
                )
            }.onSuccess { photo ->
                _observationPointPreparationDraft.value =
                    _observationPointPreparationDraft.value?.copy(
                        photos = draft.photos + photo,
                        isPhotoSaving = false,
                    )
            }.onFailure { error ->
                _observationPointPreparationDraft.value = draft.copy(isPhotoSaving = false)
                showPersistentFeedback(userMessageFor(error, "Не удалось добавить фотографию"))
            }
            onComplete?.invoke()
        }
    }

    fun removeObservationPointDraftPhoto(photo: StagedObservationPointPhoto) {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving || draft.isPhotoSaving || photo !in draft.photos) return
        _observationPointPreparationDraft.value = draft.copy(isPhotoSaving = true)
        viewModelScope.launch {
            if (attachmentFileStore.deleteDraftPhoto(photo)) {
                _observationPointPreparationDraft.value = draft.copy(
                    photos = draft.photos - photo,
                    isPhotoSaving = false,
                )
            } else {
                _observationPointPreparationDraft.value = draft.copy(isPhotoSaving = false)
                showPersistentFeedback("Не удалось удалить временную фотографию")
            }
        }
    }

    fun confirmObservationPointPreparation() {
        persistObservationPointPreparation(
            successMessage = "Точка наблюдения сохранена",
            fallbackMessage = "Не удалось сохранить точку наблюдения",
        ) { point ->
            createObservationPoint.create(point)
        }
    }

    fun recordNoBeesFoundFromPreparation() {
        persistObservationPointPreparation(
            successMessage = "Отсутствие пчёл сохранено. Точка наблюдения завершена",
            fallbackMessage = "Не удалось сохранить отсутствие пчёл",
        ) { point ->
            createObservationPoint.createWithNoBeesFound(point)
        }
    }

    private fun persistObservationPointPreparation(
        successMessage: String,
        fallbackMessage: String,
        operation: suspend (org.beesearch.app.domain.model.NewObservationPoint) -> Unit,
    ) {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving || draft.isPhotoSaving) return
        _observationPointPreparationDraft.value = draft.copy(isSaving = true)
        viewModelScope.launch {
            var activation: org.beesearch.app.data.media.DraftAttachmentActivation? = null
            try {
                activation = attachmentFileStore.prepareDraftActivation(
                    draftSessionId = draft.draftSessionId,
                    observationPointId = draft.point.id,
                    photos = draft.photos,
                )
                operation(
                    draft.point.copy(
                        description = draft.description.trimEnd().ifBlank { null },
                        attachments = activation.attachments,
                    ),
                )
                // Room now owns the attachment metadata. Final files must not be rolled back if
                // removing an already-empty draft directory fails or this cleanup is cancelled.
                val committedActivation = activation
                activation = null
                try {
                    committedActivation.commit()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The database and final attachment files are already consistent. A leftover
                    // empty draft directory is harmless and can be removed by a later cleanup.
                }
                _observationPointPreparationDraft.value = null
                manualRoute.value = null
                showSuccessFeedback(successMessage)
            } catch (error: CancellationException) {
                runCatching { activation?.rollback() }
                throw error
            } catch (error: ObservationPointAlreadyActiveException) {
                runCatching { activation?.rollback() }
                runCatching { attachmentFileStore.discardDraft(draft.draftSessionId) }
                _observationPointPreparationDraft.value = null
                manualRoute.value = activePoint.value?.let(AppRoute::ResumeObservation)
                showPersistentFeedback(userMessageFor(error, fallbackMessage))
            } catch (error: Exception) {
                runCatching { activation?.rollback() }
                _observationPointPreparationDraft.value = draft
                showPersistentFeedback(userMessageFor(error, fallbackMessage))
            }
        }
    }

    fun completeObservationPoint(pointId: UUID) {
        if (_completingObservationPointId.value != null) return
        _completingObservationPointId.value = pointId
        viewModelScope.launch {
            try {
                observationRepository.completeObservationPoint(pointId)
                manualRoute.value = null
                showSuccessFeedback("Точка наблюдения завершена")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось завершить точку наблюдения"))
            } finally {
                _completingObservationPointId.value = null
            }
        }
    }

    fun recordNoBeesFound(pointId: UUID) {
        if (_completingObservationPointId.value != null) return
        _completingObservationPointId.value = pointId
        viewModelScope.launch {
            try {
                observationRepository.recordNoBeesFound(pointId)
                manualRoute.value = null
                showSuccessFeedback("Отсутствие пчёл сохранено. Точка наблюдения завершена")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить отсутствие пчёл"))
            } finally {
                _completingObservationPointId.value = null
            }
        }
    }

    fun startFirstFlight(pointId: UUID, markColor: String, markPosition: MarkPosition) {
        val preparation = beePreparation.value
        if (preparation.isLoading || preparation.pointId != pointId) return
        if (preparation.bees.any { it.markColor == markColor && it.markPosition == markPosition }) {
            showPersistentFeedback("Такая метка уже добавлена")
            return
        }
        launchBeeMutation(fallback = "Не удалось сохранить первый вылет") {
            // No success confirmation: the bee card itself shows the new flight immediately, and a
            // transient banner above the list would move the next card under the finger.
            observationRepository.startFirstFlight(pointId, markColor, markPosition)
        }
    }

    fun registerBeeReturn(beeId: UUID) {
        launchBeeEvent(beeId, fallback = "Не удалось сохранить возвращение пчелы") {
            // The card switches to the at-point state by itself; a confirmation would only shift it.
            observationRepository.registerBeeReturn(beeId)
        }
    }

    fun startNextFlight(beeId: UUID) {
        launchBeeEvent(beeId, fallback = "Не удалось сохранить вылет пчелы") {
            observationRepository.startNextFlight(beeId)
        }
    }

    fun undoLastBeeAction(beeId: UUID) {
        launchBeeEvent(beeId, fallback = "Не удалось отменить последнее действие") {
            observationRepository.undoLastBeeAction(beeId)
        }
    }

    fun setFlightAzimuth(
        flightCycleId: UUID,
        azimuthDeg: Double?,
        onSuccess: () -> Unit = {},
    ) {
        if (flightCycleId in _flightAzimuthInProgressIds.value) return
        _flightAzimuthInProgressIds.value = _flightAzimuthInProgressIds.value + flightCycleId
        viewModelScope.launch {
            try {
                observationRepository.setFlightAzimuth(flightCycleId, azimuthDeg)
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить азимут"))
            } finally {
                _flightAzimuthInProgressIds.value = _flightAzimuthInProgressIds.value - flightCycleId
            }
        }
    }

    fun captureFlightAzimuth(
        flightCycleId: UUID,
        azimuthDeg: Double,
        onSuccess: () -> Unit = {},
    ) {
        if (flightCycleId in _flightAzimuthInProgressIds.value) return
        _flightAzimuthInProgressIds.value = _flightAzimuthInProgressIds.value + flightCycleId
        viewModelScope.launch {
            try {
                observationRepository.captureFlightAzimuth(flightCycleId, azimuthDeg)
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить азимут"))
            } finally {
                _flightAzimuthInProgressIds.value = _flightAzimuthInProgressIds.value - flightCycleId
            }
        }
    }

    fun clearFeedback(feedbackId: Long? = null) {
        if (feedbackId == null || _feedback.value?.id == feedbackId) {
            _feedback.value = null
        }
    }

    fun setLocationTracking(permissionGranted: Boolean, active: Boolean) {
        if (!permissionGranted) {
            stopLocationTracking()
            _locationState.value = LocationUiState.PermissionRequired
            return
        }
        if (!active) {
            stopLocationTracking()
            _locationState.value = LocationUiState.WaitingForFix
            return
        }
        if (locationJob?.isActive == true) return
        _locationState.value = LocationUiState.WaitingForFix
        locationJob = viewModelScope.launch {
            try {
                locationProvider.updates().collect { reading ->
                    _locationState.value = LocationUiState.Available(reading)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _locationState.value = LocationUiState.Unavailable(
                    error.message ?: "Не удалось получить местоположение",
                )
            }
        }
    }

    private fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun launchOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DuplicateTerritoryCodeException) {
                showPersistentFeedback("Территория с таким кодом уже существует")
            } catch (error: DuplicateObserverCodeException) {
                showPersistentFeedback("Наблюдатель с таким кодом уже существует")
            } catch (error: ObserverInUseException) {
                showPersistentFeedback("Наблюдатель используется в данных наблюдений и не может быть удалён")
            } catch (error: TerritoryInUseException) {
                showPersistentFeedback("Территория используется в данных наблюдений и не может быть удалена")
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить изменения"))
            }
        }
    }

    private fun validateObserver(code: String, lastName: String, firstName: String, id: UUID?): String? = when {
        code.isBlank() -> "Введите код наблюдателя"
        lastName.isBlank() -> "Введите фамилию"
        firstName.isBlank() -> "Введите имя"
        observers.value.any { it.id != id && it.code.trim() == code.trim() } -> "Наблюдатель с таким кодом уже существует"
        else -> null
    }

    private fun validateTerritory(code: String, name: String, region: String, district: String, id: UUID?): String? = when {
        code.isBlank() -> "Введите код территории"
        name.isBlank() -> "Введите название территории"
        region.isBlank() -> "Введите область или регион"
        district.isBlank() -> "Введите район"
        territories.value.any { it.id != id && it.code.trim() == code.trim() } -> "Территория с таким кодом уже существует"
        else -> null
    }

    private fun launchBeeMutation(
        fallback: String = "Не удалось изменить набор пчёл",
        operation: suspend () -> Unit,
    ) {
        if (_beeMutationInProgress.value) return
        _beeMutationInProgress.value = true
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DuplicateBeeMarkException) {
                showPersistentFeedback("Такая метка уже добавлена")
            } catch (error: BeeLimitReachedException) {
                showPersistentFeedback("На точке уже отслеживается максимум 10 пчёл")
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, fallback))
            } finally {
                _beeMutationInProgress.value = false
            }
        }
    }

    private fun launchBeeEvent(
        beeId: UUID,
        fallback: String,
        operation: suspend () -> Unit,
    ) {
        if (beeId in _beeEventInProgressIds.value) return
        _beeEventInProgressIds.value = _beeEventInProgressIds.value + beeId
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, fallback))
            } finally {
                _beeEventInProgressIds.value = _beeEventInProgressIds.value - beeId
            }
        }
    }

    private fun showSuccessFeedback(message: String) {
        showFeedback(message, FeedbackDisplayMode.AUTO_DISMISS)
    }

    private fun showPersistentFeedback(message: String) {
        showFeedback(message, FeedbackDisplayMode.PERSISTENT)
    }

    private fun showFeedback(message: String, displayMode: FeedbackDisplayMode) {
        nextFeedbackId += 1
        _feedback.value = UiFeedback(
            id = nextFeedbackId,
            message = message,
            displayMode = displayMode,
        )
    }

    override fun onCleared() {
        stopLocationTracking()
        super.onCleared()
    }

    private fun StartupDestination.toRoute(): AppRoute = when (this) {
        StartupDestination.Loading -> AppRoute.Loading
        is StartupDestination.ResumeObservation -> AppRoute.ResumeObservation(point)
        StartupDestination.ReadyForMap -> AppRoute.CurrentTerritory()
        StartupDestination.SettingsRequired -> AppRoute.Settings
        StartupDestination.InitialSetup -> AppRoute.InitialSetup
    }

    companion object {
        fun factory(application: BeeSearchApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        settingsRepository = application.container.settingsRepository,
                        territoryRepository = application.container.territoryRepository,
                        observerRepository = application.container.observerRepository,
                        observationRepository = application.container.observationRepository,
                        createObservationPoint = application.container.createObservationPoint,
                        attachmentFileStore = application.container.attachmentFileStore,
                        locationProvider = application.container.locationProvider,
                        territoryCoverageDeletion = TerritoryCoverageDeletion(
                            territoryRepository = application.container.territoryRepository,
                            areaStore = application.container.mapAreaStore,
                        ),
                        mapAreaStore = application.container.mapAreaStore,
                        mapPackageStore = application.container.mapPackageStore,
                    ) as T
                }
            }
    }
}

internal fun userMessageFor(error: Throwable, fallback: String): String = when (error) {
    is RequiredFieldException -> "Заполните обязательное поле"
    is ObserverRequiredException -> "Сначала выберите текущего наблюдателя"
    is TerritoryRequiredException -> "Сначала выберите текущую территорию"
    is EntityNotFoundException -> "Нужные данные не найдены. Обновите экран и повторите действие"
    is DuplicateTerritoryCodeException -> "Территория с таким кодом уже существует"
    is DuplicateObserverCodeException -> "Наблюдатель с таким кодом уже существует"
    is ObservationPointAlreadyActiveException ->
        "Сначала завершите текущую точку наблюдения"
    is ObservationPointNotActiveException ->
        "Эта точка наблюдения уже завершена или больше не активна"
    is BeeLimitReachedException -> "На точке уже отслеживается максимум 10 пчёл"
    is DuplicateBeeMarkException -> "Такая метка уже добавлена"
    is BeePresenceResultRequiredException ->
        "Сначала добавьте пчёл или отметьте, что пчёлы отсутствуют"
    is NoBeesFoundAlreadyRecordedException ->
        "На этой точке уже отмечено, что пчёлы отсутствуют"
    is BeesAlreadyFoundException ->
        "Пчёлы уже добавлены. Нельзя отметить, что они отсутствуют"
    is OpenFlightCycleExistsException -> "У этой пчелы уже есть незавершённый вылет"
    is OpenFlightCycleNotFoundException -> "У этой пчелы нет текущего вылета"
    is InvalidEventTimeException -> "Время события противоречит предыдущему событию"
    is InvalidAzimuthException -> "Азимут должен быть от 0° до 359,9°"
    is AzimuthCaptureRequiresOpenFlightCycleException ->
        "Азимут можно зафиксировать только во время текущего вылета"
    is AzimuthCaptureAlreadyConsumedException ->
        "Возможность зафиксировать азимут этого вылета уже использована"
    else -> fallback
}
