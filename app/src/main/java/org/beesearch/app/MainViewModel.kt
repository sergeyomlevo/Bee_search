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
import org.beesearch.app.domain.model.BeeHasFlightHistoryException
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.DuplicateTerritoryCodeException
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.InitialFlightCycleRequiredException
import org.beesearch.app.domain.model.InitialReleaseAlreadyStartedException
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.InvalidEventTimeException
import org.beesearch.app.domain.model.InvalidObserverCodeException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NoBeesFoundAlreadyRecordedException
import org.beesearch.app.domain.model.NoPreparedBeesException
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.beesearch.app.domain.model.ObserverCodeRequiredException
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.location.LocationProvider
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.domain.usecase.CreateObservationPoint
import org.beesearch.app.domain.usecase.StartupDestination
import org.beesearch.app.domain.usecase.StartupRouter
import java.util.UUID

sealed interface AppRoute {
    data object Loading : AppRoute
    data object Settings : AppRoute
    data object TerritoryManagement : AppRoute
    data object CurrentTerritory : AppRoute
    data class ResumeObservation(val point: ObservationPoint) : AppRoute
}

data class BeePreparationUiState(
    val pointId: UUID? = null,
    val bees: List<Bee> = emptyList(),
    val flightCycles: List<FlightCycle> = emptyList(),
    val beePresenceResult: BeePresenceResult? = null,
    val isReleaseStarted: Boolean = false,
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
    private val observationRepository: ObservationRepository,
    private val createObservationPoint: CreateObservationPoint,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val manualRoute = MutableStateFlow<AppRoute?>(null)
    private val _feedback = MutableStateFlow<UiFeedback?>(null)
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.PermissionRequired)
    private val _observationPointDraft = MutableStateFlow<ObservationPointCreationDraft?>(null)
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
    val completingObservationPointId: StateFlow<UUID?> = _completingObservationPointId.asStateFlow()
    val beeMutationInProgress: StateFlow<Boolean> = _beeMutationInProgress.asStateFlow()
    val beeEventInProgressIds: StateFlow<Set<UUID>> = _beeEventInProgressIds.asStateFlow()
    val flightAzimuthInProgressIds: StateFlow<Set<UUID>> =
        _flightAzimuthInProgressIds.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(currentTerritoryId = null, observerCode = null),
    )
    val territories: StateFlow<List<Territory>> = territoryRepository.observeTerritories().stateIn(
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
                    isReleaseStarted = flightCycles.isNotEmpty(),
                    isLoading = false,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeePreparationUiState())
    val currentTerritory: StateFlow<Territory?> = combine(settings, territories) { appSettings, allTerritories ->
        appSettings.currentTerritoryId?.let { id -> allTerritories.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val startupDestination: StateFlow<StartupDestination> = combine(
        activePoint,
        settings,
        territories,
    ) { point, appSettings, allTerritories ->
        StartupRouter.decide(point, appSettings.currentTerritoryId, allTerritories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupDestination.Loading)
    val route: StateFlow<AppRoute> = combine(startupDestination, manualRoute) { destination, manual ->
        manual ?: destination.toRoute()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Loading)

    fun openSettings() {
        manualRoute.value = AppRoute.Settings
        clearFeedback()
    }

    fun openTerritoryManagement() {
        manualRoute.value = AppRoute.TerritoryManagement
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

    fun saveObserverCode(value: String) {
        launchOperation {
            settingsRepository.saveObserverCode(value)
            showSuccessFeedback("Код наблюдателя сохранён")
        }
    }

    fun setCurrentTerritory(territoryId: UUID) {
        launchOperation {
            settingsRepository.setCurrentTerritoryId(territoryId)
            manualRoute.value = null
            showSuccessFeedback("Текущая территория изменена")
        }
    }

    fun createTerritory(code: String, name: String) {
        val validationError = when {
            code.isBlank() -> "Введите код территории"
            name.isBlank() -> "Введите название территории"
            territories.value.any { it.code == code } -> "Территория с таким кодом уже существует"
            else -> null
        }
        if (validationError != null) {
            showPersistentFeedback(validationError)
            return
        }

        launchOperation {
            val territory = territoryRepository.createTerritory(code, name)
            settingsRepository.setCurrentTerritoryId(territory.id)
            manualRoute.value = null
            showSuccessFeedback("Территория создана и выбрана текущей")
        }
    }

    fun startObservationPointCreation() {
        val existingPoint = activePoint.value
        if (existingPoint != null) {
            openResumeObservation(existingPoint)
            return
        }
        val territory = currentTerritory.value
        if (territory == null) {
            showPersistentFeedback("Сначала выберите текущую территорию")
            return
        }
        val reading = (locationState.value as? LocationUiState.Available)?.reading
        if (reading == null) {
            showPersistentFeedback("Дождитесь GPS-позиции")
            return
        }

        _observationPointDraft.value = ObservationPointCreationDraft(
            territoryId = territory.id,
            originalGps = reading,
            observerCodeInput = settings.value.observerCode.orEmpty(),
        )
        clearFeedback()
    }

    fun updateObservationPointCoordinates(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return
        _observationPointDraft.value = _observationPointDraft.value?.withSelectedCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
    }

    fun updateObservationPointObserverCode(value: String) {
        _observationPointDraft.value = _observationPointDraft.value?.copy(observerCodeInput = value)
    }

    fun requestObservationPointGpsRecenter() {
        _observationPointDraft.value = _observationPointDraft.value?.requestGpsRecenter()
    }

    fun cancelObservationPointCreation() {
        _observationPointDraft.value = null
        clearFeedback()
    }

    fun confirmObservationPointCreation() {
        val draft = _observationPointDraft.value ?: return
        if (draft.isSaving) return
        val observerCodeMissing = settings.value.observerCode == null
        if (observerCodeMissing && draft.observerCodeInput.isBlank()) {
            showPersistentFeedback("Введите код наблюдателя")
            return
        }

        _observationPointDraft.value = draft.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val point = draft.toNewObservationPoint()
                if (observerCodeMissing) {
                    createObservationPoint.saveObserverCodeAndCreate(draft.observerCodeInput, point)
                } else {
                    createObservationPoint.create(point)
                }
                _observationPointDraft.value = null
                manualRoute.value = null
                showSuccessFeedback("Точка наблюдения сохранена")
            } catch (error: CancellationException) {
                throw error
            } catch (error: ObservationPointAlreadyActiveException) {
                _observationPointDraft.value = null
                manualRoute.value = activePoint.value?.let(AppRoute::ResumeObservation)
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить точку наблюдения"))
            } catch (error: Exception) {
                _observationPointDraft.value = draft.copy(isSaving = false)
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить точку наблюдения"))
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

    fun addPreparedBee(pointId: UUID, markColor: String, markPosition: MarkPosition) {
        val preparation = beePreparation.value
        if (preparation.isLoading || preparation.pointId != pointId) return
        if (preparation.isReleaseStarted) {
            showPersistentFeedback("Первый выпуск уже начат: набор пчёл зафиксирован")
            return
        }
        if (preparation.bees.any { it.markColor == markColor && it.markPosition == markPosition }) {
            showPersistentFeedback("Такая метка уже добавлена")
            return
        }
        launchBeeMutation {
            observationRepository.addBee(pointId, markColor, markPosition)
            showSuccessFeedback("Пчела добавлена")
        }
    }

    fun removePreparedBee(beeId: UUID) {
        launchBeeMutation {
            observationRepository.removePreparedBee(beeId)
            showSuccessFeedback("Пчела удалена из подготовки")
        }
    }

    fun startInitialGroupRelease(pointId: UUID) {
        val preparation = beePreparation.value
        if (preparation.isLoading || preparation.pointId != pointId) return
        if (preparation.isReleaseStarted) {
            showPersistentFeedback("Первый групповой выпуск уже выполнен")
            return
        }
        launchBeeMutation(fallback = "Не удалось выполнить первый групповой выпуск") {
            observationRepository.startInitialGroupRelease(pointId)
            showSuccessFeedback("Первый групповой выпуск сохранён")
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
            } catch (error: Exception) {
                showPersistentFeedback(userMessageFor(error, "Не удалось сохранить изменения"))
            }
        }
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
            } catch (error: InitialReleaseAlreadyStartedException) {
                showPersistentFeedback("Первый выпуск уже начат: набор пчёл зафиксирован")
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
        is StartupDestination.CurrentTerritory -> AppRoute.CurrentTerritory
        StartupDestination.TerritoryManagement -> AppRoute.TerritoryManagement
    }

    companion object {
        fun factory(application: BeeSearchApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        settingsRepository = application.container.settingsRepository,
                        territoryRepository = application.container.territoryRepository,
                        observationRepository = application.container.observationRepository,
                        createObservationPoint = application.container.createObservationPoint,
                        locationProvider = application.container.locationProvider,
                    ) as T
                }
            }
    }
}

internal fun userMessageFor(error: Throwable, fallback: String): String = when (error) {
    is InvalidObserverCodeException -> "Введите непустой код наблюдателя"
    is ObserverCodeRequiredException -> "Сначала сохраните код наблюдателя"
    is EntityNotFoundException -> "Нужные данные не найдены. Обновите экран и повторите действие"
    is DuplicateTerritoryCodeException -> "Территория с таким кодом уже существует"
    is ObservationPointAlreadyActiveException ->
        "Сначала завершите текущую точку наблюдения"
    is ObservationPointNotActiveException ->
        "Эта точка наблюдения уже завершена или больше не активна"
    is InitialReleaseAlreadyStartedException ->
        "Первый выпуск уже начат: набор пчёл зафиксирован"
    is NoPreparedBeesException -> "Сначала добавьте хотя бы одну пчелу"
    is BeeHasFlightHistoryException ->
        "Нельзя удалить пчелу, для которой уже началось наблюдение"
    is DuplicateBeeMarkException -> "Такая метка уже добавлена"
    is BeePresenceResultRequiredException ->
        "Сначала добавьте пчёл или отметьте, что пчёлы отсутствуют"
    is NoBeesFoundAlreadyRecordedException ->
        "На этой точке уже отмечено, что пчёлы отсутствуют"
    is BeesAlreadyFoundException ->
        "Пчёлы уже добавлены. Нельзя отметить, что они отсутствуют"
    is OpenFlightCycleExistsException -> "У этой пчелы уже есть незавершённый вылет"
    is OpenFlightCycleNotFoundException -> "У этой пчелы нет текущего вылета"
    is InitialFlightCycleRequiredException -> "Сначала выполните первый групповой выпуск"
    is InvalidEventTimeException -> "Время события противоречит предыдущему событию"
    is InvalidAzimuthException -> "Азимут должен быть от 0° до 359,9°"
    is AzimuthCaptureRequiresOpenFlightCycleException ->
        "Азимут можно зафиксировать только во время текущего вылета"
    is AzimuthCaptureAlreadyConsumedException ->
        "Возможность зафиксировать азимут этого вылета уже использована"
    else -> fallback
}
