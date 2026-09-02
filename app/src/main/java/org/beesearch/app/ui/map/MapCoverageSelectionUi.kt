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

private val coverageFill = Color(0xFF1565C0).copy(alpha = 0.16f)
private val coverageBorder = Color(0xFF0D47A1).copy(alpha = 0.9f)
private val viewportFrame = Color(0xFFF57C00).copy(alpha = 0.95f)

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
    canAddFragment: Boolean,
    onAddFragment: () -> Unit,
    onUndo: () -> Unit,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
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
                    text = "Выбор области · $fragmentCount",
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
