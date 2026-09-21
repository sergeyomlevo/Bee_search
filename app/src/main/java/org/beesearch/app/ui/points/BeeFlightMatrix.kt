package org.beesearch.app.ui.points

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.util.Locale
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.ui.observation.BeeMarkIcon

internal data class BeeFlightMatrix(
    val columns: List<Int>,
    val rows: List<BeeFlightMatrixRow>,
)

internal data class BeeFlightMatrixRow(
    val bee: Bee,
    val displayNumber: Int,
    val cells: List<BeeFlightMatrixCell?>,
)

internal data class BeeFlightMatrixCell(
    val cycle: FlightCycle,
    val durationText: String,
    val azimuthText: String?,
)

/** Builds a stable rectangular view without changing persisted Bee/FlightCycle semantics. */
internal fun buildBeeFlightMatrix(histories: List<BeeObservationHistory>): BeeFlightMatrix {
    val ordered = histories.sortedWith(compareBy({ it.bee.createdAt }, { it.bee.id.toString() }))
    val maximumSequence = ordered
        .flatMap { it.flightCycles }
        .maxOfOrNull(FlightCycle::sequenceNumber)
        ?.coerceAtLeast(0)
        ?: 0
    val columns = (1..maximumSequence).toList()
    val rows = ordered.mapIndexed { index, history ->
        val cyclesByNumber = history.flightCycles.associateBy(FlightCycle::sequenceNumber)
        BeeFlightMatrixRow(
            bee = history.bee,
            displayNumber = index + 1,
            cells = columns.map { number ->
                cyclesByNumber[number]?.let { cycle ->
                    BeeFlightMatrixCell(
                        cycle = cycle,
                        durationText = cycle.returnTime?.let { returned ->
                            formatMatrixFlightDuration(cycle.departureTime, returned)
                        } ?: "…",
                        azimuthText = cycle.azimuthDeg?.let { String.format(Locale.ROOT, "%.0f°", it) },
                    )
                }
            },
        )
    }
    return BeeFlightMatrix(columns = columns, rows = rows)
}

internal fun formatMatrixFlightDuration(departure: java.time.Instant, returned: java.time.Instant): String {
    val seconds = Duration.between(departure, returned).seconds.coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (hours == 0L) {
        "%d:%02d".format(minutes, remainingSeconds)
    } else {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }
}

@Composable
internal fun BeeFlightMatrixView(
    matrix: BeeFlightMatrix,
    modifier: Modifier = Modifier,
) {
    var selectedCell by remember { mutableStateOf<BeeFlightMatrixCell?>(null) }
    var selectedRow by remember { mutableStateOf<BeeFlightMatrixRow?>(null) }
    val horizontalScroll = rememberScrollState()

    Column(
        modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
            .testTag("bee-flight-matrix"),
    ) {
        Row {
            MatrixHeaderCell("№", Modifier.width(BeeIdentityColumnWidth))
            matrix.columns.forEach { number ->
                MatrixHeaderCell("Ц$number", Modifier.width(CycleColumnWidth).testTag("matrix-header-$number"))
            }
        }
        matrix.rows.forEach { row ->
            Row {
                BeeIdentityCell(row)
                row.cells.forEachIndexed { columnIndex, cell ->
                    val sequenceNumber = matrix.columns[columnIndex]
                    FlightCycleCell(
                        row = row,
                        sequenceNumber = sequenceNumber,
                        cell = cell,
                        onClick = {
                            selectedRow = row
                            selectedCell = cell
                        },
                    )
                }
            }
        }
    }

    val cycle = selectedCell
    val row = selectedRow
    if (cycle != null && row != null) {
        FlightCycleDetailDialog(row = row, cell = cycle, onDismiss = {
            selectedCell = null
            selectedRow = null
        })
    }
}

@Composable
private fun MatrixHeaderCell(text: String, modifier: Modifier) {
    Surface(
        modifier = modifier.height(MatrixHeaderHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BeeIdentityCell(row: BeeFlightMatrixRow) {
    val markDescription = BeeMarkCatalog.displayName(row.bee.markColor, row.bee.markPosition)
    val beeLabel = "Пчела ${row.displayNumber}"
    Surface(
        modifier = Modifier
            .width(BeeIdentityColumnWidth)
            .height(MatrixRowHeight)
            .testTag("matrix-bee-${row.bee.id}")
            .semantics { contentDescription = "$beeLabel, $markDescription" },
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            BeeMarkIcon(
                markColor = row.bee.markColor,
                markPosition = row.bee.markPosition,
                height = MatrixBeeMarkHeight,
            )
            Text(
                text = row.displayNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlightCycleCell(
    row: BeeFlightMatrixRow,
    sequenceNumber: Int,
    cell: BeeFlightMatrixCell?,
    onClick: () -> Unit,
) {
    val description = if (cell == null) {
        "Пчела ${row.displayNumber}, цикл $sequenceNumber: нет данных"
    } else {
        buildString {
            append("Пчела ${row.displayNumber}, цикл $sequenceNumber: ")
            append(if (cell.cycle.returnTime == null) "открыт, пчела в полёте" else cell.durationText)
            cell.azimuthText?.let { append(", азимут $it") }
        }
    }
    Surface(
        modifier = Modifier
            .width(CycleColumnWidth)
            .height(MatrixRowHeight)
            .testTag("matrix-cell-${row.bee.id}-$sequenceNumber")
            .semantics { contentDescription = description }
            .then(if (cell != null) Modifier.clickable(onClick = onClick) else Modifier),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell?.durationText ?: "—",
                fontWeight = if (cell == null) FontWeight.Normal else FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            cell?.azimuthText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun FlightCycleDetailDialog(
    row: BeeFlightMatrixRow,
    cell: BeeFlightMatrixCell,
    onDismiss: () -> Unit,
) {
    val cycle = cell.cycle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пчела ${row.displayNumber} · Цикл ${cycle.sequenceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BeeMarkIcon(
                    markColor = row.bee.markColor,
                    markPosition = row.bee.markPosition,
                    height = DialogBeeMarkHeight,
                )
                Text(BeeMarkCatalog.displayName(row.bee.markColor, row.bee.markPosition))
                DetailField("Вылет", formatPointDateTime(cycle.departureTime))
                cycle.returnTime?.let { returned ->
                    DetailField("Прилёт", formatPointDateTime(returned))
                    DetailField("Длительность", formatCompletedFlightDuration(cycle.departureTime, returned))
                } ?: Text("Цикл открыт · Пчела в полёте", color = MaterialTheme.colorScheme.primary)
                cycle.azimuthDeg?.let { DetailField("Азимут", String.format(Locale.ROOT, "%.0f°", it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-cycle-detail")) {
                Text("Закрыть")
            }
        },
        modifier = Modifier.testTag("cycle-detail-dialog"),
    )
}

private val BeeIdentityColumnWidth = 64.dp
private val CycleColumnWidth = 88.dp
private val MatrixHeaderHeight = 56.dp
private val MatrixRowHeight = 88.dp
private val MatrixBeeMarkHeight = 44.dp
private val DialogBeeMarkHeight = 64.dp
