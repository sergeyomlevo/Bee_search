@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.points

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.ui.map.BeeMap
import org.beesearch.app.ui.map.BeeMapMode
import org.beesearch.app.ui.map.MapCoverageStore
import org.beesearch.app.ui.map.MapPackageStore

@Composable
internal fun PointsRoute(
    territory: Territory?,
    repository: ObservationRepository,
    mapCoverageStore: MapCoverageStore,
    mapPackageStore: MapPackageStore,
    onBack: () -> Unit,
    onChooseTerritory: () -> Unit,
    onOpenPoint: (UUID) -> Unit,
) {
    if (territory == null) {
        MissingCurrentTerritoryScreen(onBack = onBack, onChooseTerritory = onChooseTerritory)
        return
    }
    val pointsViewModel: PointsViewModel = viewModel(
        key = "points-${territory.id}",
        factory = PointsViewModel.factory(repository, territory.id),
    )
    val state by pointsViewModel.uiState.collectAsStateWithLifecycle()
    PointsScreen(
        territory = territory,
        state = state,
        mapCoverageStore = mapCoverageStore,
        mapPackageStore = mapPackageStore,
        onBack = onBack,
        onSelectYear = pointsViewModel::selectYear,
        onSelectViewMode = pointsViewModel::selectViewMode,
        onSelectPoint = pointsViewModel::selectPoint,
        onOpenPoint = onOpenPoint,
    )
}

@Composable
internal fun PointsScreen(
    territory: Territory,
    state: PointsUiState,
    mapCoverageStore: MapCoverageStore,
    mapPackageStore: MapPackageStore,
    onBack: () -> Unit,
    onSelectYear: (PointsYearFilter) -> Unit,
    onSelectViewMode: (PointsViewMode) -> Unit,
    onSelectPoint: (UUID) -> Unit,
    onOpenPoint: (UUID) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Точки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PointsBrowserControls(
                state = state,
                onSelectYear = onSelectYear,
                onSelectViewMode = onSelectViewMode,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.viewMode == PointsViewMode.MAP -> Box(Modifier.fillMaxSize()) {
                    BeeMap(
                        territoryId = territory.id,
                        coverageStore = mapCoverageStore,
                        packageStore = mapPackageStore,
                        locationState = LocationUiState.PermissionRequired,
                        observationPointDraft = null,
                        locationPermissionGranted = false,
                        onRequestLocationPermission = {},
                        onStartObservationPointCreation = {},
                        onConfirmObservationPointCreation = { _, _ -> },
                        onCancelObservationPointCreation = {},
                        mode = BeeMapMode.POINT_BROWSER,
                        savedObservationPoints = state.points,
                        onSelectSavedObservationPoint = onSelectPoint,
                        modifier = Modifier.fillMaxSize().testTag("points-map"),
                    )
                    if (state.points.isEmpty()) {
                        EmptyPointsMessage(Modifier.align(Alignment.Center))
                    }
                    state.selectedPoint?.let { selected ->
                        SelectedPointCard(
                            point = selected,
                            onOpen = { onOpenPoint(selected.id) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .testTag("selected-point-card"),
                        )
                    }
                }
                else -> PointsTable(
                    points = state.points,
                    onOpenPoint = onOpenPoint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PointsBrowserControls(
    state: PointsUiState,
    onSelectYear: (PointsYearFilter) -> Unit,
    onSelectViewMode: (PointsViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            TextButton(
                onClick = { yearMenuExpanded = true },
                modifier = Modifier.testTag("points-year-filter"),
            ) {
                Text("Год: ${yearFilterLabel(state.yearFilter)} ▾")
            }
            DropdownMenu(
                expanded = yearMenuExpanded,
                onDismissRequest = { yearMenuExpanded = false },
            ) {
                state.availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            yearMenuExpanded = false
                            onSelectYear(PointsYearFilter.Year(year))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Все годы") },
                    onClick = {
                        yearMenuExpanded = false
                        onSelectYear(PointsYearFilter.All)
                    },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PointsViewMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.viewMode == mode,
                    onClick = { onSelectViewMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, PointsViewMode.entries.size),
                    label = { Text(if (mode == PointsViewMode.MAP) "Карта" else "Таблица") },
                    modifier = Modifier.weight(1f).testTag("points-mode-${mode.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
internal fun PointsTable(
    points: List<ObservationPointSummary>,
    onOpenPoint: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { EmptyPointsMessage() }
        return
    }
    LazyColumn(modifier.testTag("points-table")) {
        items(points, key = ObservationPointSummary::id) { point ->
            PointTableRow(point = point, onClick = { onOpenPoint(point.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PointTableRow(
    point: ObservationPointSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { role = Role.Button }
            .testTag("point-row-${point.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(pointDisplayName(point), fontWeight = FontWeight.Bold)
            Text(formatPointDateTime(point.createdAt), style = MaterialTheme.typography.bodySmall)
            Text(pointResultLabel(point.beePresenceResult), style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Пчёл: ${point.beeCount}")
            Text("Циклов: ${point.completedFlightCycleCount}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectedPointCard(
    point: ObservationPointSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(pointDisplayName(point), style = MaterialTheme.typography.titleMedium)
            Text(formatPointDateTime(point.createdAt))
            Text(pointResultLabel(point.beePresenceResult))
            Text("Пчёл: ${point.beeCount}")
            TextButton(onClick = onOpen, modifier = Modifier.testTag("open-selected-point")) {
                Text("Открыть")
            }
        }
    }
}

@Composable
private fun EmptyPointsMessage(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
        Text("Точек за выбранный период нет", Modifier.padding(16.dp))
    }
}

@Composable
private fun MissingCurrentTerritoryScreen(
    onBack: () -> Unit,
    onChooseTerritory: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Точки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Текущая территория не найдена")
            TextButton(onClick = onChooseTerritory) { Text("Выбрать территорию") }
        }
    }
}

internal fun pointResultLabel(result: BeePresenceResult?): String = when (result) {
    BeePresenceResult.BEES_FOUND -> "Пчёлы найдены"
    BeePresenceResult.NO_BEES_FOUND -> "Пчёлы отсутствуют"
    null -> "Результат не зафиксирован"
}

internal fun pointDisplayName(point: ObservationPointSummary): String =
    pointDisplayName(point.pointNumber, point.code)

internal fun pointDisplayName(pointNumber: Int, code: String?): String =
    code?.takeIf(String::isNotBlank)?.let { "Точка $pointNumber · $it" } ?: "Точка $pointNumber"

internal fun yearFilterLabel(filter: PointsYearFilter): String = when (filter) {
    PointsYearFilter.All -> "Все годы"
    is PointsYearFilter.Year -> filter.value.toString()
}

private val pointDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

internal fun formatPointDateTime(instant: Instant): String =
    pointDateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))
