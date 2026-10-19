package org.beesearch.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.beesearch.app.ui.points.CompactScreenHeader

@Composable
internal fun GpxDisplayPage(
    state: GpxDisplayUiState,
    onBack: () -> Unit,
    onToggle: (java.util.UUID, Boolean) -> Unit,
    onRetry: (java.util.UUID?) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    GpxDisplayRow(
                        row = row,
                        onToggle = { onToggle(row.id, it) },
                        onRetry = { onRetry(row.id) },
                    )
                }
            }
        }
    }
}

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
) {
    val disabled = row.saving
    val status = row.error ?: when {
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
                        row.error != null -> "${if (row.selected) "Включено" else "Выключено"}. ${row.error}"
                        row.saving -> "${if (row.selected) "Включено" else "Выключено"}. Сохраняем"
                        row.preparing -> "${if (row.selected) "Включено" else "Выключено"}. Показываем"
                        else -> if (row.selected) "Включено" else "Выключено"
                    }
                    contentDescription = row.name
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
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (status != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 80.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (row.error != null) {
                    Text(
                        "Повторить",
                        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onRetry)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
