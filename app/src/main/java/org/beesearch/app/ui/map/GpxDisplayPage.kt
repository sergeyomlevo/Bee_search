package org.beesearch.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import java.util.UUID
import org.beesearch.app.ui.points.CompactScreenHeader

@Composable
internal fun GpxDisplayPage(
    state: GpxDisplayUiState,
    onBack: () -> Unit,
    onToggle: (java.util.UUID, Boolean) -> Unit,
    onRetry: (java.util.UUID?) -> Unit,
    onDeleteConfirmed: (UUID) -> Unit = {},
    onRetryDelete: (UUID) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuRow by remember(state.territoryId) { mutableStateOf<UUID?>(null) }
    var deleteTarget by remember(state.territoryId) { mutableStateOf<DeleteTarget?>(null) }
    val liveTarget = deleteTarget?.takeIf { target ->
        state.territoryId == target.territoryId && state.rows.any { it.id == target.importId }
    }
    Column(modifier = modifier.fillMaxWidth().testTag("gpx-display-page")) {
        CompactScreenHeader(
            title = "Показать трек",
            onBack = onBack,
            modifier = Modifier.testTag("gpx-display-header"),
        )
        when {
            state.loading -> DisplayLoading()
            state.error != null -> DisplayError(
                message = state.error,
                onRetry = { onRetry(null) },
            )
            state.rows.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Нет сохранённых GPX", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("gpx-display-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    Box(Modifier.fillMaxWidth()) {
                        GpxDisplayRow(
                            row = row,
                            onToggle = { onToggle(row.id, it) },
                            onRetry = { onRetry(row.id) },
                            onOpenDeleteMenu = { menuRow = row.id },
                            onOpenDeleteConfirmation = { retry ->
                                deleteTarget = DeleteTarget(state.territoryId, row.id, row.name, retry)
                            },
                        )
                        if (menuRow == row.id) {
                            Box(Modifier.align(Alignment.CenterEnd)) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { menuRow = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        onClick = {
                                            menuRow = null
                                            deleteTarget = DeleteTarget(state.territoryId, row.id, row.name, retry = false)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (liveTarget != null) {
        DeleteGpxDialog(
            target = liveTarget,
            deleting = state.rows.firstOrNull { it.id == liveTarget.importId }?.deleting == true,
            onDismiss = { if (!state.rows.any { it.id == liveTarget.importId && it.deleting }) deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                if (liveTarget.retry) onRetryDelete(liveTarget.importId)
                else onDeleteConfirmed(liveTarget.importId)
            },
        )
    }
}

private data class DeleteTarget(
    val territoryId: UUID?,
    val importId: UUID,
    val name: String,
    val retry: Boolean,
)

@Composable
private fun DisplayLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("gpx-display-loading"),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun DisplayError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("gpx-display-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Повторить") }
    }
}

@Composable
private fun GpxDisplayRow(
    row: GpxDisplayRow,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenDeleteMenu: () -> Unit,
    onOpenDeleteConfirmation: (Boolean) -> Unit,
) {
    val disabled = row.saving || row.deleting
    val status = when {
        row.deleting -> "Удаляем…"
        row.deleteError != null -> row.deleteError
        row.error != null -> row.error
        row.saving -> "Сохраняем…"
        row.preparing -> "Показываем…"
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("gpx-display-row-${row.id}")
                .semantics {
                    role = Role.Switch
                    stateDescription = when {
                        row.deleteError != null -> "${if (row.selected) "Включено" else "Выключено"}. ${row.deleteError}"
                        row.deleting -> "Удаление"
                        row.error != null -> "${if (row.selected) "Включено" else "Выключено"}. ${row.error}"
                        row.saving -> "${if (row.selected) "Включено" else "Выключено"}. Сохраняем"
                        row.preparing -> "${if (row.selected) "Включено" else "Выключено"}. Показываем"
                        else -> if (row.selected) "Включено" else "Выключено"
                    }
                    contentDescription = row.name
                    customActions = listOf(CustomAccessibilityAction("Удалить трек", action = {
                        if (!disabled) {
                            onOpenDeleteConfirmation(false)
                            true
                        } else false
                    }))
                }
                .toggleable(
                    value = row.selected,
                    enabled = !disabled,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Switch(
                checked = row.selected,
                onCheckedChange = null,
                enabled = !disabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                row.name,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).combinedClickable(
                    enabled = !disabled,
                    onClick = { onToggle(!row.selected) },
                    onLongClick = onOpenDeleteMenu,
                ).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (row.deleteError != null) {
            Column(Modifier.fillMaxWidth().padding(start = 80.dp, end = 16.dp, bottom = 8.dp)) {
                Text(row.deleteError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(
                    onClick = { onOpenDeleteConfirmation(true) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Повторить") }
            }
        } else if (status != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 80.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (row.error != null || row.deleteError != null) {
                    Text(
                        "Повторить",
                        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = {
                            if (row.deleteError != null) onOpenDeleteConfirmation(true) else onRetry()
                        })
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteGpxDialog(
    target: DeleteTarget,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить трек?") },
        text = {
            Text(
                "«${target.name}» будет удалён из Bee Search вместе с сохранёнными координатами. " +
                    "Исходный файл в другой папке телефона не затрагивается.",
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onDismiss, enabled = !deleting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Отмена")
                }
                TextButton(onClick = onConfirm, enabled = !deleting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Удалить")
                }
            }
        },
    )
}
