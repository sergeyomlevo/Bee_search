@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.points

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.ui.map.BeeMap
import org.beesearch.app.ui.map.BeeMapMode
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapPackageStore
import org.beesearch.app.ui.map.observationPointMarkers

internal const val BACKUP_DOCUMENT_NAME = "bee-search-backup.zip"

@Composable
internal fun PointsRoute(
    territories: List<Territory>,
    currentTerritoryId: UUID?,
    repository: ObservationRepository,
    observationDataMaintenance: ObservationDataMaintenance,
    backupExporter: BackupDocumentExporter,
    exchangeStorage: BeeSearchExchangeStorage,
    mapAreaStore: MapAreaStore,
    mapPackageStore: MapPackageStore,
    onBack: () -> Unit,
    onChooseTerritory: () -> Unit,
    onOpenPoint: (UUID) -> Unit,
) {
    if (territories.isEmpty()) {
        MissingCurrentTerritoryScreen(onBack = onBack, onChooseTerritory = onChooseTerritory)
        return
    }
    val pointsViewModel: PointsViewModel = viewModel(
        factory = PointsViewModel.factory(repository, observationDataMaintenance, backupExporter),
    )
    val state by pointsViewModel.uiState.collectAsStateWithLifecycle()

    // The current operational Territory is only the initial viewing selection.
    LaunchedEffect(territories.map(Territory::id), currentTerritoryId) {
        pointsViewModel.syncTerritories(territories.map(Territory::id), currentTerritoryId)
    }
    LaunchedEffect(pointsViewModel) { pointsViewModel.refreshCounts() }

    // Export reuses the existing logical-backup exporter; the user still confirms name and location.
    val createDocument = rememberLauncherForActivityResult(
        CreateExchangeDocument(
            mimeType = "application/zip",
            initialFolder = exchangeStorage.initialDocumentUri(ExchangeFolder.DATA),
        ),
    ) { destination ->
        destination?.let(pointsViewModel::export)
    }

    PointsScreen(
        territories = territories,
        state = state,
        mapAreaStore = mapAreaStore,
        mapPackageStore = mapPackageStore,
        onBack = onBack,
        onSelectTerritory = pointsViewModel::selectTerritory,
        onSelectYear = pointsViewModel::selectYear,
        onSelectViewMode = pointsViewModel::selectViewMode,
        onSelectPoint = pointsViewModel::selectPoint,
        onDismissPointSelection = pointsViewModel::clearPointSelection,
        onOpenPoint = onOpenPoint,
        onExportAll = { createDocument.launch(BACKUP_DOCUMENT_NAME) },
        onDeleteAll = pointsViewModel::deleteAllPoints,
        onDismissMessage = pointsViewModel::dismissMessage,
    )
}

@Composable
internal fun PointsScreen(
    territories: List<Territory>,
    state: PointsUiState,
    mapAreaStore: MapAreaStore,
    mapPackageStore: MapPackageStore,
    onBack: () -> Unit,
    onSelectTerritory: (UUID) -> Unit,
    onSelectYear: (PointsYearFilter) -> Unit,
    onSelectViewMode: (PointsViewMode) -> Unit,
    onSelectPoint: (UUID) -> Unit,
    onDismissPointSelection: () -> Unit,
    onOpenPoint: (UUID) -> Unit,
    onExportAll: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
) {
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        CompactScreenHeader(title = "Точки", onBack = onBack) {
            HeaderMenuButton(
                items = listOf(
                    HeaderMenuItem(
                        label = "Экспорт всех данных наблюдений",
                        testTag = "points-menu-export",
                        onClick = { confirmExport = true },
                    ),
                    HeaderMenuItem(
                        label = "Удалить все точки наблюдения",
                        testTag = "points-menu-delete-all",
                        isDestructive = true,
                        onClick = { confirmDeleteAll = true },
                    ),
                ),
                menuDescription = "Действия со всеми точками",
                testTag = "points-menu",
            )
        }
        PointsFilters(
            territories = territories,
            state = state,
            onSelectTerritory = onSelectTerritory,
            onSelectYear = onSelectYear,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        PointsModeSwitch(
            viewMode = state.viewMode,
            onSelectViewMode = { mode ->
                if (mode != state.viewMode) onDismissPointSelection()
                onSelectViewMode(mode)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        )
        state.message?.let { message ->
            PointsMessageRow(message = message, onDismiss = onDismissMessage)
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.viewMode == PointsViewMode.MAP -> Box(Modifier.fillMaxSize()) {
                val selectedTerritory = territories.firstOrNull { it.id == state.selectedTerritoryId }
                BeeMap(
                    territoryId = state.selectedTerritoryId,
                    territoryName = selectedTerritory?.name,
                    areaStore = mapAreaStore,
                    packageStore = mapPackageStore,
                    locationState = LocationUiState.PermissionRequired,
                    locationPermissionGranted = false,
                    onRequestLocationPermission = {},
                    onRequestCreateRecord = { _, _ -> },
                    mode = BeeMapMode.POINT_BROWSER,
                    savedObjectMarkers = observationPointMarkers(state.points),
                    onSelectSavedObject = { marker -> onSelectPoint(marker.id) },
                    modifier = Modifier.fillMaxSize().testTag("points-map"),
                )
                if (state.points.isEmpty()) {
                    EmptyPointsMessage(Modifier.align(Alignment.Center))
                }
                state.selectedPoint?.let { selected ->
                    SelectedPointCard(
                        point = selected,
                        onOpen = {
                            onDismissPointSelection()
                            onOpenPoint(selected.id)
                        },
                        onDismiss = onDismissPointSelection,
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

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("Экспортировать все данные наблюдений?") },
            text = {
                Text(
                    "Будет создан один файл со всеми данными Bee Search: территории и наблюдатели, "
                        + "точки наблюдения с пчёлами, циклами, описаниями, фотографиями и погодой, "
                        + "а также дупла и колоды с их фото и видео и настройки приложения.\n\n"
                        + "Данные не удаляются. Файлы офлайн-карт в экспорт не входят.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmExport = false; onExportAll() },
                    modifier = Modifier.testTag("confirm-export-all"),
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmExport = false },
                    modifier = Modifier.testTag("cancel-export-all"),
                ) { Text("Отмена") }
            },
        )
    }

    if (confirmDeleteAll) {
        PointsDeleteAllDialog(
            counts = state.counts,
            isDeleting = state.isDeletingAll,
            onConfirm = { confirmDeleteAll = false; onDeleteAll() },
            onDismiss = { if (!state.isDeletingAll) confirmDeleteAll = false },
        )
    }
}

@Composable
private fun PointsFilters(
    territories: List<Territory>,
    state: PointsUiState,
    onSelectTerritory: (UUID) -> Unit,
    onSelectYear: (PointsYearFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var territoryMenuExpanded by remember { mutableStateOf(false) }
    var yearMenuExpanded by remember { mutableStateOf(false) }
    val selectedTerritory = territories.firstOrNull { it.id == state.selectedTerritoryId }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.weight(1f)) {
            // The selected Territory is shown by its short code. The full "Территория: CODE" wording
            // does not fit next to the year at 1.7 system font scale, and a truncated filter value
            // would hide the very choice it reports. The menu lists every Territory as "CODE — Name".
            val territoryLabel = selectedTerritory?.code ?: "Территория"
            TextButton(
                onClick = { territoryMenuExpanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Территория: $territoryLabel" }
                    .testTag("points-territory-filter"),
            ) {
                Text(
                    text = "$territoryLabel ▾",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = territoryMenuExpanded,
                onDismissRequest = { territoryMenuExpanded = false },
            ) {
                territories.forEach { territory ->
                    DropdownMenuItem(
                        text = { Text("${territory.code} — ${territory.name}") },
                        onClick = {
                            territoryMenuExpanded = false
                            onSelectTerritory(territory.id)
                        },
                        modifier = Modifier.testTag("points-territory-${territory.id}"),
                    )
                }
            }
        }
        Box(Modifier.widthIn(min = 88.dp, max = 120.dp)) {
            TextButton(
                onClick = { yearMenuExpanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Год: ${yearFilterLabel(state.yearFilter)}" }
                    .testTag("points-year-filter"),
            ) {
                Text(
                    text = "${yearFilterLabel(state.yearFilter)} ▾",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    }
}

@Composable
private fun PointsModeSwitch(
    viewMode: PointsViewMode,
    onSelectViewMode: (PointsViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        PointsViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = viewMode == mode,
                onClick = { onSelectViewMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, PointsViewMode.entries.size),
                label = { Text(if (mode == PointsViewMode.MAP) "Карта" else "Таблица") },
                modifier = Modifier.weight(1f).testTag("points-mode-${mode.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun PointsMessageRow(message: PointsMessage, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.text,
                color = if (message.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f).testTag("points-message"),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("points-message-dismiss")) {
                Text("Закрыть")
            }
        }
    }
}

/** Confirms the single destructive mass operation and states its real scope and counts. */
@Composable
private fun PointsDeleteAllDialog(
    counts: ObservationDataCounts?,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить все точки наблюдения?") },
        text = {
            Text(
                buildString {
                    append("Будут безвозвратно удалены ВСЕ точки наблюдения во всех территориях")
                    if (counts != null) {
                        append(":\n")
                        append("• точки наблюдения: ${counts.observationPoints}\n")
                        append("• пчёлы: ${counts.bees}\n")
                        append("• циклы полёта: ${counts.flightCycles}")
                    } else {
                        append(".")
                    }
                    append("\n\nФотографии точек удаляются вместе с точками.")
                    append("\nТерритории, наблюдатели, ареал, настройки и офлайн-карта сохранятся.")
                    append("\nЭкспорт автоматически не выполняется.")
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("confirm-delete-all-points"),
            ) { Text("Удалить безвозвратно") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("cancel-delete-all-points"),
            ) { Text("Отмена") }
        },
    )
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(pointDisplayName(point), style = MaterialTheme.typography.titleMedium)
            Text(formatPointDateTime(point.createdAt))
            Text(pointResultLabel(point.beePresenceResult))
            Text("Пчёл: ${point.beeCount}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-selected-point")) {
                    Text("Закрыть")
                }
                TextButton(onClick = onOpen, modifier = Modifier.testTag("open-selected-point")) {
                    Text("Открыть")
                }
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
    Column(Modifier.fillMaxSize()) {
        CompactScreenHeader(title = "Точки", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Территорий пока нет")
            TextButton(onClick = onChooseTerritory) { Text("Создать территорию") }
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
