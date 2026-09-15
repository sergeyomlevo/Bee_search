@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.ObservationPointPreparationDraft

@Composable
internal fun ObservationPointPreparationScreen(
    draft: ObservationPointPreparationDraft,
    onConfirmPoint: () -> Unit,
    onRecordNoBeesFound: () -> Unit,
    onAbort: () -> Unit,
) {
    var showNoBeesConfirmation by rememberSaveable { mutableStateOf(false) }
    BackHandler { onAbort() }

    if (showNoBeesConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!draft.isSaving) showNoBeesConfirmation = false },
            title = { Text("Пчёлы отсутствуют?") },
            text = {
                Text(
                    "Точка наблюдения будет сохранена с результатом " +
                        "«пчёлы отсутствуют» и завершена.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoBeesConfirmation = false
                        onRecordNoBeesFound()
                    },
                    enabled = !draft.isSaving,
                    modifier = Modifier.testTag("confirm-no-bees-from-draft"),
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoBeesConfirmation = false },
                    enabled = !draft.isSaving,
                ) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Подготовка точки",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        onClick = onAbort,
                        enabled = !draft.isSaving,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("abort-observation-point-preparation"),
                    ) { Text("Отмена") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("observation-point-preparation-draft"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Точка пока не сохранена",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    "После добавления откроется наблюдение, где метка пчелы " +
                        "выбирается при первом вылете. Заранее пчёлы не создаются.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Button(
                    onClick = onConfirmPoint,
                    enabled = !draft.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add-observation-point"),
                ) { Text(if (draft.isSaving) "Сохранение…" else "Добавить") }
            }
            item {
                OutlinedButton(
                    onClick = { showNoBeesConfirmation = true },
                    enabled = !draft.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("record-no-bees-from-draft"),
                ) { Text("Пчёлы отсутствуют") }
            }
        }
    }
}
