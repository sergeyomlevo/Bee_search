package org.beesearch.app.ui.points

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.repository.ObservationDataMaintenance
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

/** Result text of a mass operation, shown under the browser controls. */
internal data class PointsMessage(
    val text: String,
    val isError: Boolean,
)

internal data class PointsUiState(
    val points: List<ObservationPointSummary> = emptyList(),
    /** Territory currently being browsed. Never the operational current Territory by itself. */
    val selectedTerritoryId: UUID? = null,
    val availableYears: List<Int> = emptyList(),
    val yearFilter: PointsYearFilter = PointsYearFilter.All,
    val viewMode: PointsViewMode = PointsViewMode.MAP,
    val selectedPoint: ObservationPointSummary? = null,
    val isLoading: Boolean = true,
    val counts: ObservationDataCounts? = null,
    val isExporting: Boolean = false,
    val isDeletingAll: Boolean = false,
    val message: PointsMessage? = null,
)

/**
 * Saved ObservationPoint browser.
 *
 * The Territory filter is a *viewing* selection: this ViewModel has no settings access at all, so
 * browsing another Territory can never change the operational current Territory of the application.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PointsViewModel(
    private val repository: ObservationRepository,
    private val maintenance: ObservationDataMaintenance,
    private val backupExporter: BackupDocumentExporter,
) : ViewModel() {
    private data class ViewInputs(
        val territoryId: UUID? = null,
        val yearFilter: PointsYearFilter? = null,
        val viewMode: PointsViewMode = PointsViewMode.MAP,
        val selectedPointId: UUID? = null,
    )

    private data class OperationState(
        val counts: ObservationDataCounts? = null,
        val isExporting: Boolean = false,
        val isDeletingAll: Boolean = false,
        val message: PointsMessage? = null,
    )

    private val inputs = MutableStateFlow(ViewInputs())
    private val operation = MutableStateFlow(OperationState())

    private val points = inputs.flatMapLatest { current ->
        current.territoryId
            ?.let { repository.observeObservationPointSummaries(it, observationYear = null) }
            ?: flowOf(emptyList())
    }

    val uiState: StateFlow<PointsUiState> = combine(
        points,
        inputs,
        operation,
    ) { allPoints, current, current_operation ->
        buildPointsUiState(
            allPoints = allPoints,
            selectedTerritoryId = current.territoryId,
            requestedYearFilter = current.yearFilter,
            viewMode = current.viewMode,
            selectedPointId = current.selectedPointId,
            counts = current_operation.counts,
            isExporting = current_operation.isExporting,
            isDeletingAll = current_operation.isDeletingAll,
            message = current_operation.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PointsUiState(),
    )

    /**
     * Keeps the viewing selection valid against the Territories that still exist.
     *
     * The current operational Territory is only the *initial* selection; a later explicit user choice
     * is preserved until that Territory disappears.
     */
    fun syncTerritories(availableTerritoryIds: List<UUID>, currentTerritoryId: UUID?) {
        val current = inputs.value.territoryId
        val next = when {
            current != null && current in availableTerritoryIds -> current
            currentTerritoryId != null && currentTerritoryId in availableTerritoryIds -> currentTerritoryId
            else -> availableTerritoryIds.firstOrNull()
        }
        if (next != current) {
            inputs.value = inputs.value.copy(territoryId = next, yearFilter = null, selectedPointId = null)
        }
    }

    fun selectTerritory(territoryId: UUID) {
        if (inputs.value.territoryId == territoryId) return
        // Years belong to a Territory, so the year choice restarts from its newest year.
        inputs.value = inputs.value.copy(
            territoryId = territoryId,
            yearFilter = null,
            selectedPointId = null,
        )
    }

    fun selectYear(filter: PointsYearFilter) {
        inputs.value = inputs.value.copy(yearFilter = filter, selectedPointId = null)
    }

    fun selectViewMode(mode: PointsViewMode) {
        inputs.value = inputs.value.copy(viewMode = mode)
    }

    fun selectPoint(pointId: UUID) {
        inputs.value = inputs.value.copy(selectedPointId = pointId)
    }

    fun clearPointSelection() {
        inputs.value = inputs.value.copy(selectedPointId = null)
    }

    fun refreshCounts() {
        if (operation.value.isExporting || operation.value.isDeletingAll) return
        viewModelScope.launch {
            runCatching { maintenance.getObservationDataCounts() }
                .onSuccess { counts -> operation.value = operation.value.copy(counts = counts) }
        }
    }

    /** Exports the whole research archive through the existing backup exporter. */
    fun export(destination: Uri) {
        if (operation.value.isExporting || operation.value.isDeletingAll) return
        operation.value = operation.value.copy(isExporting = true, message = null)
        viewModelScope.launch {
            try {
                backupExporter.export(destination)
                operation.value = operation.value.copy(
                    isExporting = false,
                    message = PointsMessage("Экспорт завершён. Файл Bee Search сохранён.", isError = false),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = operation.value.copy(
                    isExporting = false,
                    message = PointsMessage(
                        "Не удалось экспортировать данные. Выберите другое место и повторите.",
                        isError = true,
                    ),
                )
            }
        }
    }

    /**
     * Deletes every ObservationPoint of the research database and its dependent rows.
     *
     * Territories, Observers, the Ареал, device settings and the installed offline map are preserved.
     */
    fun deleteAllPoints() {
        if (operation.value.isExporting || operation.value.isDeletingAll) return
        operation.value = operation.value.copy(isDeletingAll = true, message = null)
        viewModelScope.launch {
            try {
                val remaining = maintenance.clearObservationData()
                inputs.value = inputs.value.copy(selectedPointId = null)
                operation.value = operation.value.copy(
                    isDeletingAll = false,
                    counts = remaining,
                    message = PointsMessage("Все точки наблюдения удалены.", isError = false),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = operation.value.copy(
                    isDeletingAll = false,
                    message = PointsMessage("Не удалось удалить точки наблюдения.", isError = true),
                )
            }
        }
    }

    fun dismissMessage() {
        operation.value = operation.value.copy(message = null)
    }

    companion object {
        fun factory(
            repository: ObservationRepository,
            maintenance: ObservationDataMaintenance,
            backupExporter: BackupDocumentExporter,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PointsViewModel(repository, maintenance, backupExporter) as T
        }
    }
}

/**
 * Pure projection of the browser state.
 *
 * The Territory filter is already applied by the observed query; this function only resolves the year.
 * A year that does not exist in the selected Territory falls back to its newest year, so switching
 * Territory never shows an empty list by accident.
 */
internal fun buildPointsUiState(
    allPoints: List<ObservationPointSummary>,
    selectedTerritoryId: UUID?,
    requestedYearFilter: PointsYearFilter?,
    viewMode: PointsViewMode,
    selectedPointId: UUID?,
    counts: ObservationDataCounts? = null,
    isExporting: Boolean = false,
    isDeletingAll: Boolean = false,
    message: PointsMessage? = null,
): PointsUiState {
    val years = allPoints.map(ObservationPointSummary::observationYear).distinct().sortedDescending()
    val effectiveFilter = when (requestedYearFilter) {
        is PointsYearFilter.Year -> requestedYearFilter.takeIf { it.value in years }
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
        selectedTerritoryId = selectedTerritoryId,
        availableYears = years,
        yearFilter = effectiveFilter,
        viewMode = viewMode,
        selectedPoint = filteredPoints.firstOrNull { it.id == selectedPointId },
        isLoading = false,
        counts = counts,
        isExporting = isExporting,
        isDeletingAll = isDeletingAll,
        message = message,
    )
}
