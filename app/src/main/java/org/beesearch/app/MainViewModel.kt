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
import java.util.UUID

sealed interface AppRoute {
    data object Loading : AppRoute
    data object Settings : AppRoute
    data object Help : AppRoute
    data object Data : AppRoute
    data object Objects : AppRoute
    data object Points : AppRoute
    data class PointDetail(val pointId: UUID) : AppRoute
    data object TerritoryManagement : AppRoute
    data object OfflineMapManagement : AppRoute
    data object CurrentTerritory : AppRoute
    data object PrepareObservationPoint : AppRoute
    data class ResumeObservation(val point: ObservationPoint) : AppRoute
}

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

internal class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val territoryRepository: TerritoryRepository,
    private val observerRepository: ObserverRepository,
    private val observationRepository: ObservationRepository,
    private val createObservationPoint: CreateObservationPoint,
    private val locationProvider: LocationProvider,
    private val territoryCoverageDeletion: TerritoryCoverageDeletion,
) : ViewModel() {
    private val manualRoute = MutableStateFlow<AppRoute?>(null)
    private val _coverageEditNonce = MutableStateFlow(0)
    private val _feedback = MutableStateFlow<UiFeedback?>(null)
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.PermissionRequired)
    private val _observationPointDraft = MutableStateFlow<ObservationPointCreationDraft?>(null)
    private val _observationPointPreparationDraft = MutableStateFlow<ObservationPointPreparationDraft?>(null)
    private val _completingObservationPointId = MutableStateFlow<UUID?>(null)
    private val _beeMutationInProgress = MutableStateFlow(false)
    private val _beeEventInProgressIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val _flightAzimuthInProgressIds = MutableStateFlow<Set<UUID>>(emptySet())
    private var locationJob: kotlinx.coroutines.Job? = null
    private var nextFeedbackId = 0L

    val feedback: StateFlow<UiFeedback?> = _feedback.asStateFlow()
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()
    val observationPointDraft: StateFlow<ObservationPointCreationDraft?> =
        _observationPointDraft.asStateFlow()
    val observationPointPreparationDraft: StateFlow<ObservationPointPreparationDraft?> =
        _observationPointPreparationDraft.asStateFlow()
    val completingObservationPointId: StateFlow<UUID?> = _completingObservationPointId.asStateFlow()
    val beeMutationInProgress: StateFlow<Boolean> = _beeMutationInProgress.asStateFlow()
    val beeEventInProgressIds: StateFlow<Set<UUID>> = _beeEventInProgressIds.asStateFlow()
    val flightAzimuthInProgressIds: StateFlow<Set<UUID>> =
        _flightAzimuthInProgressIds.asStateFlow()
    val coverageEditNonce: StateFlow<Int> = _coverageEditNonce.asStateFlow()
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
    val startupDestination: StateFlow<StartupDestination> = combine(
        activePoint,
        settings,
        territories,
        observers,
    ) { point, appSettings, allTerritories, allObservers ->
        StartupRouter.decide(
            activePoint = point,
            currentTerritoryId = appSettings.currentTerritoryId,
            territories = allTerritories,
            currentObserverId = appSettings.currentObserverId,
            observers = allObservers,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupDestination.Loading)
    val route: StateFlow<AppRoute> = combine(startupDestination, manualRoute) { destination, manual ->
        manual ?: destination.toRoute()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Loading)

    fun openSettings() {
        manualRoute.value = AppRoute.Settings
        clearFeedback()
    }

    fun openHelp() {
        manualRoute.value = AppRoute.Help
        clearFeedback()
    }

    fun openData() {
        manualRoute.value = AppRoute.Data
        clearFeedback()
    }

    fun openObjects() {
        manualRoute.value = AppRoute.Objects
        clearFeedback()
    }

    fun openPoints() {
        manualRoute.value = AppRoute.Points
        clearFeedback()
    }

    fun openPointDetail(pointId: UUID) {
        manualRoute.value = AppRoute.PointDetail(pointId)
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
        manualRoute.value = AppRoute.CurrentTerritory
        _coverageEditNonce.value += 1
        clearFeedback()
    }

    fun openCurrentTerritory() {
        manualRoute.value = AppRoute.CurrentTerritory
        clearFeedback()
    }

    fun openResumeObservation(point: ObservationPoint) {
        manualRoute.value = AppRoute.ResumeObservation(point)
        clearFeedback()
    }

    fun returnToStartup() {
        manualRoute.value = null
        clearFeedback()
    }

    fun setCurrentTerritory(territoryId: UUID) {
        launchOperation {
            settingsRepository.setCurrentTerritoryId(territoryId)
            manualRoute.value = null
            showSuccessFeedback("Текущая территория изменена")
        }
    }

    fun setCurrentObserver(observerId: UUID) {
        launchOperation {
            settingsRepository.setCurrentObserverId(observerId)
            manualRoute.value = null
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
            manualRoute.value = null
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
            manualRoute.value = null
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
        val existingPoint = activePoint.value
        if (existingPoint != null) {
            openResumeObservation(existingPoint)
            return
        }
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
        val draft = _observationPointDraft.value ?: return
        _observationPointDraft.value = null
        _observationPointPreparationDraft.value = ObservationPointPreparationDraft(
            point = draft.toNewObservationPoint(),
        )
        manualRoute.value = AppRoute.PrepareObservationPoint
        clearFeedback()
    }

    fun abortObservationPointPreparation() {
        val draft = _observationPointPreparationDraft.value ?: return
        if (draft.isSaving) return
        _observationPointPreparationDraft.value = null
        manualRoute.value = null
        clearFeedback()
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
        if (draft.isSaving) return
        _observationPointPreparationDraft.value = draft.copy(isSaving = true)
        viewModelScope.launch {
            try {
                operation(draft.point)
                _observationPointPreparationDraft.value = null
                manualRoute.value = null
                showSuccessFeedback(successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ObservationPointAlreadyActiveException) {
                _observationPointPreparationDraft.value = null
                manualRoute.value = activePoint.value?.let(AppRoute::ResumeObservation)
                showPersistentFeedback(userMessageFor(error, fallbackMessage))
            } catch (error: Exception) {
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
            observationRepository.startFirstFlight(pointId, markColor, markPosition)
            showSuccessFeedback("Вылет сохранён")
        }
    }

    fun registerBeeReturn(beeId: UUID) {
        launchBeeEvent(beeId, fallback = "Не удалось сохранить возвращение пчелы") {
            observationRepository.registerBeeReturn(beeId)
            showSuccessFeedback("Прилёт сохранён")
        }
    }

    fun startNextFlight(beeId: UUID) {
        launchBeeEvent(beeId, fallback = "Не удалось сохранить вылет пчелы") {
            observationRepository.startNextFlight(beeId)
            showSuccessFeedback("Вылет сохранён")
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
        StartupDestination.ReadyForMap -> AppRoute.CurrentTerritory
        StartupDestination.SettingsRequired -> AppRoute.Settings
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
                        locationProvider = application.container.locationProvider,
                        territoryCoverageDeletion = TerritoryCoverageDeletion(
                            territoryRepository = application.container.territoryRepository,
                            areaStore = application.container.mapAreaStore,
                        ),
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
