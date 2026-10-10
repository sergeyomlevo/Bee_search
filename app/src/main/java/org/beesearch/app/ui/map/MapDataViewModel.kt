package org.beesearch.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.PhysicalObjectRepository

/** Years and months that actually contain records of a type; the calendar is built from data. */
internal data class MapDataAvailability(
    val years: Map<MapDataType, List<Int>> = emptyMap(),
    val months: Map<MapDataType, List<YearMonth>> = emptyMap(),
    /**
     * Whether the Territory holds records of this type at all, including records whose canonical
     * date is unknown. It keeps «нет записей» from being said about a type that does have records —
     * a legacy Hollow has no fixation date yet is still a visible record on the map.
     */
    val hasRecords: Map<MapDataType, Boolean> = emptyMap(),
) {
    fun yearsOf(type: MapDataType): List<Int> = years[type] ?: emptyList()

    fun monthsOf(type: MapDataType): List<YearMonth> = months[type] ?: emptyList()

    fun hasRecordsOf(type: MapDataType): Boolean = hasRecords[type] ?: false

    companion object {
        val EMPTY = MapDataAvailability()
    }
}

/** Objects of the current Territory after their own type filter has been applied by the query. */
internal data class MapObjectSets(
    val points: List<ObservationPointSummary> = emptyList(),
    val hollows: List<Hollow> = emptyList(),
    val logHives: List<LogHive> = emptyList(),
) {
    companion object {
        val EMPTY = MapObjectSets()
    }
}

/**
 * State of the approved «Данные на карте» surface.
 *
 * [markers] already reflects both conditions of the approved map result: the type is visible and its
 * own filters was applied by the temporal query layer.
 */
internal data class MapDataUiState(
    val today: LocalDate,
    val display: MapDataDisplayState = DEFAULT_MAP_DATA_DISPLAY,
    val markers: List<MapObjectMarker> = emptyList(),
    val availability: MapDataAvailability = MapDataAvailability.EMPTY,
    val selectedObjectId: UUID? = null,
    /**
     * Whether the «Данные на карте» sheet is open, and which type's filters it is showing.
     *
     * This is one user session of settings, so it belongs to a scope that outlives a single
     * composition: an open panel and an open filter screen survive anything that recomposes the map
     * surface — including the transient route change a settings write can cause.
     */
    val panelOpen: Boolean = false,
    val openedType: MapDataType? = null,
) {
    /** Only a currently visible, filtered real record can supply a preview or detail action. */
    val selectedMarker: MapObjectMarker?
        get() = markers.firstOrNull { it.id == selectedObjectId }

    fun yearsOf(type: MapDataType): List<Int> = availability.yearsOf(type)

    fun monthsOf(type: MapDataType): List<YearMonth> = availability.monthsOf(type)

    fun hasRecordsOf(type: MapDataType): Boolean = availability.hasRecordsOf(type)
}

/**
 * Owns the display state of the current Territory and the research objects it shows.
 *
 * The I5 temporal query layer stays the only place that filters by research date: this class passes
 * each type's own interval to that type's query and never re-filters a loaded set in the UI. A
 * period selection is presentation state, so it can never change a stored canonical date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MapDataViewModel(
    private val observationRepository: ObservationRepository,
    private val physicalObjectRepository: PhysicalObjectRepository,
    private val store: MapDataDisplayStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val territoryId = MutableStateFlow<UUID?>(null)
    private val display = MutableStateFlow(DEFAULT_MAP_DATA_DISPLAY)
    private val availability = MutableStateFlow(MapDataAvailability.EMPTY)
    private val objects = MutableStateFlow(MapObjectSets.EMPTY)
    private val selectedObjectId = MutableStateFlow<UUID?>(null)
    private val panelOpen = MutableStateFlow(false)
    private val openedType = MutableStateFlow<MapDataType?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    private val pendingSave = MutableStateFlow<SaveRequest?>(null)

    /**
     * Counts user mutations and Territory switches.
     *
     * A stored state read while a change is already being made must not overwrite that change, and a
     * state read for a Territory the user has since left must not be applied at all.
     */
    private var loadGeneration = 0
    private var mutationRevision = 0
    private val cameras = mutableMapOf<UUID, MapCameraContext>()

    fun cameraFor(id: UUID?): MapCameraContext? = id?.let(cameras::get)

    fun saveCamera(id: UUID, camera: MapCameraContext) {
        cameras[id] = camera
    }

    /** The sheet session as one value, so the UI state stays a five-flow combination. */
    private val panelSession = combine(panelOpen, openedType) { open, type -> open to type }

    val uiState: StateFlow<MapDataUiState> =
        combine(
            display,
            availability,
            objects,
            selectedObjectId,
            panelSession,
        ) { state, available, sets, selected, session ->
            MapDataUiState(
                today = today(),
                display = state,
                markers = researchObjectMarkers(state, sets.points, sets.hollows, sets.logHives),
                availability = available,
                selectedObjectId = selected,
                panelOpen = session.first,
                openedType = session.second,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = MapDataUiState(today = today()),
        )

    init {
        // Conflated persistence: only the newest state of a Territory is written.
        viewModelScope.launch {
            pendingSave.filterNotNull().collect { request ->
                store.save(request.territoryId, request.state)
            }
        }
        // Available years/months come from the unfiltered records of the Territory.
        viewModelScope.launch {
            combine(territoryId.filterNotNull().distinctUntilChanged(), refreshTrigger) { id, _ -> id }
                .flatMapLatest { id ->
                    mapDataAvailabilityFlow(observationRepository, physicalObjectRepository, id)
                }
                .collect { availability.value = it }
        }
        // Displayed objects follow each type's own filters. Only the intervals drive this flow, so
        // switching a type's visibility never re-reads records.
        viewModelScope.launch {
            combine(
                territoryId.filterNotNull().distinctUntilChanged(),
                display.map { it.objectFilters() }.distinctUntilChanged(),
                refreshTrigger,
            ) { id, intervals, _ -> id to intervals }
                .flatMapLatest { (id, intervals) ->
                    mapResearchObjectFlow(
                        observationRepository = observationRepository,
                        physicalObjectRepository = physicalObjectRepository,
                        territoryId = id,
                        filters = intervals,
                    )
                }
                .collect { objects.value = it }
        }
    }

    fun setTerritory(id: UUID?) {
        // Re-entering the same map from a real record keeps its filters and selection. Refresh is
        // separate, so this also avoids a transient default display while navigation returns.
        if (territoryId.value == id) return
        // Whatever was shown belonged to the previous Territory: never draw one Territory's markers
        // or calendar with another Territory's state while the stored state is being read.
        loadGeneration += 1
        val generation = loadGeneration
        display.value = DEFAULT_MAP_DATA_DISPLAY
        availability.value = MapDataAvailability.EMPTY
        objects.value = MapObjectSets.EMPTY
        selectedObjectId.value = null
        if (id == null) {
            // Without a Territory there is no research data to configure, so the panel that configures
            // it has nothing to show and is closed rather than left open over an empty map.
            openedType.value = null
            panelOpen.value = false
        }
        territoryId.value = id
        if (id == null) return

        val revisionAtStart = mutationRevision
        viewModelScope.launch {
            val loaded = store.load(id)
            // A newer switch, or a change the user made while this read was in flight, wins.
            if (loadGeneration == generation && mutationRevision == revisionAtStart) {
                display.value = loaded
            }
        }
    }

    /** Re-reads records of the Territory, e.g. after a new object was created on the map. */
    fun refresh() {
        refreshTrigger.value += 1
    }

    fun setVisible(type: MapDataType, visible: Boolean) {
        mutate { it.withVisibility(type, visible) }
    }

    fun setPeriod(type: MapDataType, period: ResearchDateInterval?) {
        mutate { it.withPeriod(type, period) }
    }

    fun setFilters(type: MapDataType, filters: org.beesearch.app.domain.model.ResearchObjectFilterSet) {
        mutate { it.withFilters(type, filters) }
    }

    fun resetPeriod(type: MapDataType) {
        mutate { it.withResetPeriod(type) }
    }

    fun resetFilters() {
        mutate { it.withResetFilters() }
    }

    /**
     * Opens the «Данные на карте» sheet at its type list.
     *
     * The sheet session lives here, not in the composition, so nothing that merely recomposes the map
     * surface can close a panel the user opened or lose the filter screen they are working in.
     */
    fun openPanel() {
        openedType.value = null
        panelOpen.value = true
    }

    /** Closes the sheet. Only an explicit user action does this. */
    fun closePanel() {
        openedType.value = null
        panelOpen.value = false
    }

    /** Shows the filter screen of one type inside the open panel. */
    fun openTypeFilters(type: MapDataType) {
        openedType.value = type
    }

    /** Returns from a type's filter screen to the type list, keeping the panel open. */
    fun closeTypeFilters() {
        openedType.value = null
    }

    /**
     * Marker selection on the unified map.
     *
     * Selection supplies the compact preview. A hidden/filtered-out identity cannot be selected.
     */
    fun toggleSelection(id: UUID) {
        val visibleMarkers = researchObjectMarkers(display.value, objects.value.points,
            objects.value.hollows, objects.value.logHives)
        if (visibleMarkers.none { it.id == id }) return
        selectedObjectId.value = if (selectedObjectId.value == id) null else id
    }

    fun closePreview() {
        selectedObjectId.value = null
    }

    private fun mutate(change: (MapDataDisplayState) -> MapDataDisplayState) {
        val updated = change(display.value)
        if (updated == display.value) return
        mutationRevision += 1
        display.value = updated
        // A selection that is no longer on the map — hidden or filtered out — is not kept, because a
        // halo the user did not ask for would describe an object that is not being shown.
        selectedObjectId.value = null
        val id = territoryId.value ?: return
        pendingSave.value = SaveRequest(id, updated)
    }

    private data class SaveRequest(val territoryId: UUID, val state: MapDataDisplayState)

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        fun factory(
            observationRepository: ObservationRepository,
            physicalObjectRepository: PhysicalObjectRepository,
            store: MapDataDisplayStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MapDataViewModel(observationRepository, physicalObjectRepository, store) as T
        }
    }
}
