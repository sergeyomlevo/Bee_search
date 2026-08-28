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
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeHasFlightHistoryException
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.DuplicateTerritoryCodeException
import org.beesearch.app.domain.model.EntityNotFoundException
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
    val beePresenceResult: BeePresenceResult? = null,
    val isReleaseStarted: Boolean = false,
    val isLoading: Boolean = true,
)

internal class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val territoryRepository: TerritoryRepository,
    private val observationRepository: ObservationRepository,
    private val createObservationPoint: CreateObservationPoint,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val manualRoute = MutableStateFlow<AppRoute?>(null)
    private val _feedback = MutableStateFlow<String?>(null)
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.PermissionRequired)
    private val _observationPointDraft = MutableStateFlow<ObservationPointCreationDraft?>(null)
    private val _completingObservationPointId = MutableStateFlow<UUID?>(null)
    private val _beeMutationInProgress = MutableStateFlow(false)
    private var locationJob: kotlinx.coroutines.Job? = null

    val feedback: StateFlow<String?> = _feedback.asStateFlow()
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()
    val observationPointDraft: StateFlow<ObservationPointCreationDraft?> =
        _observationPointDraft.asStateFlow()
    val completingObservationPointId: StateFlow<UUID?> = _completingObservationPointId.asStateFlow()
    val beeMutationInProgress: StateFlow<Boolean> = _beeMutationInProgress.asStateFlow()
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
                observationRepository.observeHasFlightCycles(point.id),
            ) { bees, isReleaseStarted ->
                BeePreparationUiState(
                    pointId = point.id,
                    bees = bees,
                    beePresenceResult = point.beePresenceResult,
                    isReleaseStarted = isReleaseStarted,
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
            _feedback.value = "Код наблюдателя сохранён"
        }
    }

    fun setCurrentTerritory(territoryId: UUID) {
        launchOperation {
            settingsRepository.setCurrentTerritoryId(territoryId)
            manualRoute.value = null
            _feedback.value = "Текущая территория изменена"
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
            _feedback.value = validationError
            return
        }

        launchOperation {
            val territory = territoryRepository.createTerritory(code, name)
            settingsRepository.setCurrentTerritoryId(territory.id)
            manualRoute.value = null
            _feedback.value = "Территория создана и выбрана текущей"
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
            _feedback.value = "Сначала выберите текущую территорию"
            return
        }
        val reading = (locationState.value as? LocationUiState.Available)?.reading
        if (reading == null) {
            _feedback.value = "Дождитесь GPS-позиции"
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
            _feedback.value = "Введите код наблюдателя"
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
                _feedback.value = "Точка наблюдения сохранена"
            } catch (error: CancellationException) {
                throw error
            } catch (error: ObservationPointAlreadyActiveException) {
                _observationPointDraft.value = null
                manualRoute.value = activePoint.value?.let(AppRoute::ResumeObservation)
                _feedback.value = userMessageFor(error, "Не удалось сохранить точку наблюдения")
            } catch (error: Exception) {
                _observationPointDraft.value = draft.copy(isSaving = false)
                _feedback.value = userMessageFor(error, "Не удалось сохранить точку наблюдения")
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
                _feedback.value = "Точка наблюдения завершена"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _feedback.value = userMessageFor(error, "Не удалось завершить точку наблюдения")
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
                _feedback.value = "Отсутствие пчёл сохранено. Точка наблюдения завершена"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _feedback.value = userMessageFor(error, "Не удалось сохранить отсутствие пчёл")
            } finally {
                _completingObservationPointId.value = null
            }
        }
    }

    fun addPreparedBee(pointId: UUID, markColor: String, markPosition: MarkPosition) {
        val preparation = beePreparation.value
        if (preparation.isLoading || preparation.pointId != pointId) return
        if (preparation.isReleaseStarted) {
            _feedback.value = "Первый выпуск уже начат: набор пчёл зафиксирован"
            return
        }
        if (preparation.bees.any { it.markColor == markColor && it.markPosition == markPosition }) {
            _feedback.value = "Такая метка уже добавлена"
            return
        }
        launchBeeMutation {
            observationRepository.addBee(pointId, markColor, markPosition)
            _feedback.value = "Пчела добавлена"
        }
    }

    fun removePreparedBee(beeId: UUID) {
        launchBeeMutation {
            observationRepository.removePreparedBee(beeId)
            _feedback.value = "Пчела удалена из подготовки"
        }
    }

    fun clearFeedback() {
        _feedback.value = null
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
                _feedback.value = "Территория с таким кодом уже существует"
            } catch (error: Exception) {
                _feedback.value = userMessageFor(error, "Не удалось сохранить изменения")
            }
        }
    }

    private fun launchBeeMutation(operation: suspend () -> Unit) {
        if (_beeMutationInProgress.value) return
        _beeMutationInProgress.value = true
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DuplicateBeeMarkException) {
                _feedback.value = "Такая метка уже добавлена"
            } catch (error: InitialReleaseAlreadyStartedException) {
                _feedback.value = "Первый выпуск уже начат: набор пчёл зафиксирован"
            } catch (error: Exception) {
                _feedback.value = userMessageFor(error, "Не удалось изменить набор пчёл")
            } finally {
                _beeMutationInProgress.value = false
            }
        }
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
    else -> fallback
}
