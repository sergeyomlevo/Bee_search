package org.beesearch.app.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.repository.ObservationRepository
import java.util.UUID

internal enum class PointsViewMode {
    MAP,
    TABLE,
}

internal sealed interface PointsYearFilter {
    data object All : PointsYearFilter
    data class Year(val value: Int) : PointsYearFilter
}

internal data class PointsUiState(
    val points: List<ObservationPointSummary> = emptyList(),
    val availableYears: List<Int> = emptyList(),
    val yearFilter: PointsYearFilter = PointsYearFilter.All,
    val viewMode: PointsViewMode = PointsViewMode.MAP,
    val selectedPoint: ObservationPointSummary? = null,
    val isLoading: Boolean = true,
)

internal class PointsViewModel(
    repository: ObservationRepository,
    territoryId: UUID,
) : ViewModel() {
    private val requestedYearFilter = MutableStateFlow<PointsYearFilter?>(null)
    private val viewMode = MutableStateFlow(PointsViewMode.MAP)
    private val selectedPointId = MutableStateFlow<UUID?>(null)

    val uiState: StateFlow<PointsUiState> = combine(
        repository.observeObservationPointSummaries(territoryId, observationYear = null),
        requestedYearFilter,
        viewMode,
        selectedPointId,
    ) { allPoints, requestedFilter, currentMode, selectedId ->
        buildPointsUiState(allPoints, requestedFilter, currentMode, selectedId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PointsUiState(),
    )

    fun selectYear(filter: PointsYearFilter) {
        requestedYearFilter.value = filter
        selectedPointId.value = null
    }

    fun selectViewMode(mode: PointsViewMode) {
        viewMode.value = mode
    }

    fun selectPoint(pointId: UUID) {
        selectedPointId.value = pointId
    }

    fun clearPointSelection() {
        selectedPointId.value = null
    }

    companion object {
        fun factory(
            repository: ObservationRepository,
            territoryId: UUID,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PointsViewModel(repository, territoryId) as T
        }
    }
}

internal fun buildPointsUiState(
    allPoints: List<ObservationPointSummary>,
    requestedFilter: PointsYearFilter?,
    viewMode: PointsViewMode,
    selectedPointId: UUID?,
): PointsUiState {
    val years = allPoints.map(ObservationPointSummary::observationYear).distinct().sortedDescending()
    val effectiveFilter = when (requestedFilter) {
        is PointsYearFilter.Year -> requestedFilter.takeIf { it.value in years }
            ?: years.firstOrNull()?.let(PointsYearFilter::Year)
            ?: PointsYearFilter.All
        PointsYearFilter.All -> PointsYearFilter.All
        null -> years.firstOrNull()?.let(PointsYearFilter::Year) ?: PointsYearFilter.All
    }
    val filteredPoints = when (effectiveFilter) {
        PointsYearFilter.All -> allPoints
        is PointsYearFilter.Year -> allPoints.filter { it.observationYear == effectiveFilter.value }
    }
    return PointsUiState(
        points = filteredPoints,
        availableYears = years,
        yearFilter = effectiveFilter,
        viewMode = viewMode,
        selectedPoint = filteredPoints.firstOrNull { it.id == selectedPointId },
        isLoading = false,
    )
}

internal data class PointDetailUiState(
    val detail: ObservationPointDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)

internal class PointDetailViewModel(
    repository: ObservationRepository,
    pointId: UUID,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PointDetailUiState())
    val uiState: StateFlow<PointDetailUiState> = mutableUiState

    init {
        viewModelScope.launch {
            val detail = repository.getObservationPointDetail(pointId)
            mutableUiState.value = PointDetailUiState(
                detail = detail,
                isLoading = false,
                notFound = detail == null,
            )
        }
    }

    companion object {
        fun factory(
            repository: ObservationRepository,
            pointId: UUID,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PointDetailViewModel(repository, pointId) as T
        }
    }
}
