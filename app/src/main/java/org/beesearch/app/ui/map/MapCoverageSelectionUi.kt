package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal const val ENTER_COVERAGE_SELECTION_DESCRIPTION = "Сформировать offline coverage"
internal const val ADD_COVERAGE_FRAGMENT_DESCRIPTION = "Добавить видимый участок карты"
internal const val UNDO_COVERAGE_FRAGMENT_DESCRIPTION = "Отменить последний добавленный участок"
internal const val SHOW_ALL_COVERAGE_DESCRIPTION = "Показать всё выбранное покрытие"
internal const val CLEAR_COVERAGE_DESCRIPTION = "Очистить выбранное покрытие"
internal const val DONE_COVERAGE_SELECTION_DESCRIPTION = "Завершить выбор offline coverage"
internal const val MAP_COVERAGE_SELECTION_CONTROLS_TAG = "map-coverage-selection-controls"
internal const val CURRENT_COVERAGE_SUMMARY_TAG = "current-coverage-summary"
internal const val COPY_SELECTED_COVERAGE_DESCRIPTION = "Копировать выбранный bbox для сборки карты"
internal const val IMPORT_OFFLINE_MAP_DESCRIPTION = "Импортировать офлайн-карту"
internal const val SELECT_OFFLINE_COVERAGE_DESCRIPTION = "Выбрать участок для офлайн-карты"
internal const val OFFLINE_MAP_PACKAGE_PANEL_TAG = "offline-map-package-panel"

private val coverageFill = Color(0xFF1565C0).copy(alpha = 0.16f)
private val coverageBorder = Color(0xFF0D47A1).copy(alpha = 0.9f)
private val viewportFrame = Color(0xFFF57C00).copy(alpha = 0.95f)

@Composable
internal fun OfflineMapPackagePanel(
    desiredCoverageConfigured: Boolean,
    availability: MapPackageAvailability,
    isLoading: Boolean,
    isImporting: Boolean,
    message: String?,
    onSelectCoverage: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = availability as? MapPackageAvailability.Ready
    val unavailable = availability as? MapPackageAvailability.Unavailable
    Surface(
        modifier = modifier
            .widthIn(max = 288.dp)
            .testTag(OFFLINE_MAP_PACKAGE_PANEL_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                isLoading -> Text("Проверка офлайн-карты…", style = MaterialTheme.typography.bodySmall)
                ready != null -> Text("Офлайн-карта готова", style = MaterialTheme.typography.labelLarge)
                else -> Text("Офлайн-карта не подготовлена", style = MaterialTheme.typography.labelLarge)
            }
            (message ?: unavailable?.message)?.let { detail ->
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            if (!desiredCoverageConfigured) {
                Text(
                    "Сначала выберите участок, который должен работать без сети.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onSelectCoverage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = SELECT_OFFLINE_COVERAGE_DESCRIPTION },
                ) { Text("Выбрать участок") }
            } else {
                Text(
                    "Импорт: сначала manifest, затем соответствующий PMTiles.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onImport,
                    enabled = !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = IMPORT_OFFLINE_MAP_DESCRIPTION },
                ) { Text(if (isImporting) "Импорт…" else if (ready == null) "Импортировать карту" else "Заменить карту") }
                TextButton(
                    onClick = onSelectCoverage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = SELECT_OFFLINE_COVERAGE_DESCRIPTION },
                ) { Text("Изменить участок") }
            }
        }
    }
}

@Composable
internal fun CoverageSelectionEntry(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onEnter,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = ENTER_COVERAGE_SELECTION_DESCRIPTION }
            .testTag("enter-coverage-selection"),
    ) {
        CoverageGlyph()
    }
}

@Composable
internal fun MapCoverageSelectionControls(
    fragmentCount: Int,
    viewportSummary: MapAreaBoundsSummary?,
    selectedSummary: MapAreaBoundsSummary? = null,
    showDevBoundsExport: Boolean = false,
    title: String = "Участок",
    canAddFragment: Boolean,
    onAddFragment: () -> Unit,
    onUndo: () -> Unit,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onCopySelectedBounds: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 336.dp)
            .testTag(MAP_COVERAGE_SELECTION_CONTROLS_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.semantics {
                        contentDescription = DONE_COVERAGE_SELECTION_DESCRIPTION
                    },
                ) {
                    Text("Готово")
                }
                TextButton(onClick = onCancel) { Text("Выйти") }
            }
            viewportSummary?.let { summary ->
                CoverageViewportSummary(summary)
            }
            if (showDevBoundsExport && selectedSummary != null) {
                TextButton(
                    onClick = onCopySelectedBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = COPY_SELECTED_COVERAGE_DESCRIPTION },
                ) {
                    Text("Копировать bbox", maxLines = 1)
                }
            }
            Button(
                onClick = onAddFragment,
                enabled = canAddFragment,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = ADD_COVERAGE_FRAGMENT_DESCRIPTION },
            ) {
                Text("Добавить участок")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onUndo,
                    enabled = fragmentCount > 0,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = UNDO_COVERAGE_FRAGMENT_DESCRIPTION
                    },
                ) {
                    Text("Отмена", maxLines = 1)
                }
                TextButton(
                    onClick = onShowAll,
                    enabled = fragmentCount > 0,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = SHOW_ALL_COVERAGE_DESCRIPTION
                    },
                ) {
                    Text("Обзор", maxLines = 1)
                }
                TextButton(
                    onClick = onClear,
                    enabled = fragmentCount > 0,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = CLEAR_COVERAGE_DESCRIPTION
                    },
                ) {
                    Text("Сброс", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CoverageViewportSummary(summary: MapAreaBoundsSummary) {
    fun coordinate(value: Double): String = "%.6f".format(java.util.Locale.ROOT, value)
    fun kilometers(value: Double): String = "%.1f".format(java.util.Locale.ROOT, value)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(CURRENT_COVERAGE_SUMMARY_TAG),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text("Текущий участок", style = MaterialTheme.typography.labelMedium)
        Text(
            "С: ${coordinate(summary.bounds.north)}  Ю: ${coordinate(summary.bounds.south)}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "З: ${coordinate(summary.bounds.west)}  В: ${coordinate(summary.bounds.east)}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "${kilometers(summary.widthKm)} × ${kilometers(summary.heightKm)} км · ${kilometers(summary.areaKm2)} км²",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun ClearCoverageSelectionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очистить выбор?") },
        text = { Text("Все выбранные участки исчезнут с карты в текущем сеансе.") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Очистить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
internal fun MapCoverageFragmentsOverlay(
    fragments: List<MapCoverageFragment>,
    map: MapLibreMap?,
    cameraRevision: Int,
    modifier: Modifier = Modifier,
) {
    if (fragments.isEmpty() || map == null) return
    Canvas(modifier) {
        // Reading the revision makes the geographic projection refresh while the camera moves.
        cameraRevision
        fragments.forEach { fragment ->
            val path = fragment.toScreenPath(map)
            drawPath(path = path, color = coverageFill)
            drawPath(
                path = path,
                color = coverageBorder,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun MapCoverageViewportFrame(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Граница добавляемого видимого участка карты" },
    ) {
        drawRect(
            color = viewportFrame,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

private fun MapCoverageFragment.toScreenPath(map: MapLibreMap): Path {
    val bounds = bounds
    val corners = listOf(
        LatLng(bounds.north, bounds.west),
        LatLng(bounds.north, bounds.east),
        LatLng(bounds.south, bounds.east),
        LatLng(bounds.south, bounds.west),
    ).map { coordinate ->
        map.projection.toScreenLocation(coordinate).let { screen -> Offset(screen.x, screen.y) }
    }
    return Path().apply {
        moveTo(corners.first().x, corners.first().y)
        corners.drop(1).forEach { corner -> lineTo(corner.x, corner.y) }
        close()
    }
}

@Composable
private fun CoverageGlyph() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val inset = 3.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 7.dp.toPx()),
            end = Offset(size.width / 2f, size.height - 7.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = color,
            start = Offset(7.dp.toPx(), size.height / 2f),
            end = Offset(size.width - 7.dp.toPx(), size.height / 2f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
