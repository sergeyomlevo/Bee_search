package org.beesearch.app.ui.map

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.PhysicalObjectRepository

/**
 * Data assembly of the unified data map: what each available type contributes and which filter its
 * records are read with.
 *
 * It is deliberately separate from marker presentation: this file decides *which* objects the map
 * shows, while [SavedObjectMarkers] decides how one object looks. Every temporal filter is executed
 * by the I5 temporal query layer in SQL — the UI never re-filters a loaded set and never mutates a
 * stored canonical date.
 */

/**
 * The interval each type asks the temporal query layer for.
 *
 * A null value means that type is unbounded — «Всё время» — which is not a default period. Each
 * interval is used only for its own type, so one type's filter can never affect another's records.
 */
internal data class MapObjectFilters(
    val observationPoint: org.beesearch.app.domain.model.ObservationPointFilterSet,
    val hollow: org.beesearch.app.domain.model.PhysicalObjectFilterSet,
    val logHive: org.beesearch.app.domain.model.PhysicalObjectFilterSet,
)

internal fun MapDataDisplayState.objectFilters(): MapObjectFilters = MapObjectFilters(
    display(MapDataType.OBSERVATION_POINT).filters as org.beesearch.app.domain.model.ObservationPointFilterSet,
    display(MapDataType.HOLLOW).filters as org.beesearch.app.domain.model.PhysicalObjectFilterSet,
    display(MapDataType.LOG_HIVE).filters as org.beesearch.app.domain.model.PhysicalObjectFilterSet,
)

/**
 * Markers of the unified data map.
 *
 * Every type is filtered by its own filters in the query layer, so this assembly only decides
 * visibility: hiding a type removes its markers without touching the filter it keeps. Each display
 * type resolves its own object kind through one vocabulary instead of a second mapping table.
 */
internal fun researchObjectMarkers(
    display: MapDataDisplayState,
    points: List<ObservationPointSummary>,
    hollows: List<Hollow>,
    logHives: List<LogHive>,
): List<MapObjectMarker> = buildList {
    MapDataType.entries.forEach { type ->
        if (display.isVisible(type)) {
            addAll(
                markersOf(
                    type = type,
                    points = points,
                    hollows = hollows,
                    logHives = logHives,
                ),
            )
        }
    }
}

private fun markersOf(
    type: MapDataType,
    points: List<ObservationPointSummary>,
    hollows: List<Hollow>,
    logHives: List<LogHive>,
): List<MapObjectMarker> = when (type.objectType()) {
    MapObjectType.OBSERVATION_POINT -> observationPointMarkers(points)
    MapObjectType.HOLLOW -> hollowMarkers(hollows)
    MapObjectType.LOG_HIVE -> logHiveMarkers(logHives)
}

/**
 * Research objects of one Territory after each type's own filter, as the unified map shows them.
 *
 * Every interval goes to its own type's query in the I5 temporal layer. A hidden type is still
 * queried with its own filters: the value has to survive while the type is off. The flow depends only
 * on the filters, not on visibility, so toggling a type never re-reads the database.
 */
internal fun mapResearchObjectFlow(
    observationRepository: ObservationRepository,
    physicalObjectRepository: PhysicalObjectRepository,
    territoryId: UUID,
    filters: MapObjectFilters,
): Flow<MapObjectSets> = combine(
    observationRepository.observeObservationPointSummaries(
        territoryId = territoryId,
        observationYear = null,
        dateInterval = filters.observationPoint.dateInterval,
        countFilters = filters.observationPoint,
    ),
    oneShot {
        physicalObjectRepository.listForTerritory(
            territoryId = territoryId,
            hollowDateInterval = filters.hollow.dateInterval,
            logHiveDateInterval = filters.logHive.dateInterval,
            hollowFilters = filters.hollow,
            logHiveFilters = filters.logHive,
        )
    },
) { points, physical ->
    MapObjectSets(points = points, hollows = physical.hollows, logHives = physical.logHives)
}

/**
 * Years and months that contain records of a type.
 *
 * The calendar is built from real records, so an unbounded query is the source for its units; a
 * record whose canonical date is unknown contributes no unit but stays a visible record.
 */
internal fun mapDataAvailabilityFlow(
    observationRepository: ObservationRepository,
    physicalObjectRepository: PhysicalObjectRepository,
    territoryId: UUID,
): Flow<MapDataAvailability> = combine(
    observationRepository.observeObservationPointSummaries(territoryId),
    oneShot { physicalObjectRepository.listForTerritory(territoryId) },
) { points, objects ->
    val pointDates = points.map(ObservationPointSummary::observationDate)
    val hollowDates = objects.hollows.mapNotNull(Hollow::fixationDate)
    val logHiveDates = objects.logHives.mapNotNull(LogHive::fixationDate)
    MapDataAvailability(
        years = mapOf(
            MapDataType.OBSERVATION_POINT to availableYearsOf(pointDates),
            MapDataType.HOLLOW to availableYearsOf(hollowDates),
            MapDataType.LOG_HIVE to availableYearsOf(logHiveDates),
        ),
        months = mapOf(
            MapDataType.OBSERVATION_POINT to availableMonthsOf(pointDates),
            MapDataType.HOLLOW to availableMonthsOf(hollowDates),
            MapDataType.LOG_HIVE to availableMonthsOf(logHiveDates),
        ),
        // Record presence is separate from dated presence: a record without a canonical date has no
        // calendar unit but is still a record of this type.
        hasRecords = mapOf(
            MapDataType.OBSERVATION_POINT to points.isNotEmpty(),
            MapDataType.HOLLOW to objects.hollows.isNotEmpty(),
            MapDataType.LOG_HIVE to objects.logHives.isNotEmpty(),
        ),
    )
}

/** Turns a one-shot repository call into a flow that restarts with the current filter. */
private fun <T> oneShot(load: suspend () -> T): Flow<T> = flow { emit(load()) }
