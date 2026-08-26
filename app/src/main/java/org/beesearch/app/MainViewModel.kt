package org.beesearch.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.location.LocationProvider
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.repository.TerritoryRepository
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

internal class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val territoryRepository: TerritoryRepository,
    observationRepository: ObservationRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val manualRoute = MutableStateFlow<AppRoute?>(null)
    private val _feedback = MutableStateFlow<String?>(null)
    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.PermissionRequired)
    private var locationJob: kotlinx.coroutines.Job? = null

    val feedback: StateFlow<String?> = _feedback.asStateFlow()
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()
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

    fun clearFeedback() {
        _feedback.value = null
    }

    fun setLocationPermission(granted: Boolean) {
        locationJob?.cancel()
        locationJob = null
        if (!granted) {
            _locationState.value = LocationUiState.PermissionRequired
            return
        }
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

    private fun launchOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _feedback.value = error.message ?: "Не удалось сохранить изменения"
            }
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
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
                        locationProvider = application.container.locationProvider,
                    ) as T
                }
            }
    }
}
