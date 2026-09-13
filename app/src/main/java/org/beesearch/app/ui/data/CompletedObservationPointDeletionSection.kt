package org.beesearch.app.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun CompletedObservationPointDeletionSection(
    points: List<CompletedObservationPointSummary>,
    isBusy: Boolean,
    isDeleting: Boolean,
    onDelete: (UUID) -> Unit,
) {
    var showPoints by rememberSaveable { mutableStateOf(false) }
    var pendingPointId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingPoint = pendingPointId?.let { id -> points.find { it.id.toString() == id } }

    pendingPoint?.let { point ->
        DeleteObservationPointDialog(
            point = point,
            isDeleting = isDeleting,
            onConfirm = {
                pendingPointId = null
                onDelete(point.id)
            },
            onDismiss = { if (!isBusy) pendingPointId = null },
        )
    }

    DataSection(
        title = "Удаление отдельной точки",
        description = if (points.isEmpty()) {
            "Нет завершённых точек, доступных для удаления. Активная точка здесь не показывается."
        } else {
            "Можно удалить одну завершённую точку вместе с её пчёлами и циклами. Остальные наблюдения сохранятся."
        },
    ) {
        Button(
            onClick = { showPoints = !showPoints },
            enabled = !isBusy && points.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("show-completed-points"),
        ) {
            Text(if (showPoints) "Скрыть список" else "Удалить отдельную точку")
        }

        if (showPoints) {
            points.forEach { point ->
                CompletedObservationPointRow(
                    point = point,
                    enabled = !isBusy,
                    onDelete = { pendingPointId = point.id.toString() },
                )
            }
        }
    }
}

@Composable
private fun CompletedObservationPointRow(
    point: CompletedObservationPointSummary,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${point.territoryCode} — ${point.territoryName}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("${point.formattedDate()} · Точка №${point.pointNumber}")
            Text("Пчёл: ${point.beeCount}")
            TextButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.testTag("delete-point-${point.id}"),
            ) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun DeleteObservationPointDialog(
    point: CompletedObservationPointSummary,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить точку наблюдения?") },
        text = {
            Text(
                "${point.territoryCode} — ${point.territoryName}\n" +
                    "${point.formattedDate()} · Точка №${point.pointNumber}\n\n" +
                    "Будут удалены эта точка, все связанные с ней пчёлы и циклы. " +
                    "Остальные данные не изменятся.",
                modifier = Modifier.testTag("delete-point-details-${point.id}"),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("confirm-delete-point"),
            ) { Text("Удалить безвозвратно") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("cancel-delete-point"),
            ) { Text("Отмена") }
        },
    )
}

private fun CompletedObservationPointSummary.formattedDate(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(createdAt)
