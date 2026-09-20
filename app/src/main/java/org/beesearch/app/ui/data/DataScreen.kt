@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.data

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.BeeSearchApplication
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.domain.model.ObservationDataCounts

internal const val BACKUP_DOCUMENT_NAME = "bee-search-backup.zip"

@Composable
internal fun DataRoute(
    application: BeeSearchApplication,
    onBack: () -> Unit,
) {
    val viewModel: DataViewModel = viewModel(factory = DataViewModel.factory(application))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exchangeStorage = application.container.exchangeStorage
    // Export writes into the exchange Data folder; the user still confirms name and location.
    val createDocument = rememberLauncherForActivityResult(
        CreateExchangeDocument(
            mimeType = "application/zip",
            initialFolder = exchangeStorage.initialDocumentUri(ExchangeFolder.DATA),
        ),
    ) { destination ->
        destination?.let(viewModel::export)
    }
    LaunchedEffect(viewModel) { viewModel.refreshCounts() }
    LaunchedEffect(exchangeStorage) { exchangeStorage.ensure() }

    DataScreen(
        state = state,
        onBack = onBack,
        onExport = { createDocument.launch(BACKUP_DOCUMENT_NAME) },
        onDeleteCompletedPoint = viewModel::deleteCompletedObservationPoint,
        onClearObservationData = viewModel::clearObservationData,
        onDismissStatus = viewModel::dismissStatus,
        exchangeDataPath = exchangeStorage.userVisiblePath(ExchangeFolder.DATA),
    )
}

@Composable
internal fun DataScreen(
    state: DataUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDeleteCompletedPoint: (java.util.UUID) -> Unit,
    onClearObservationData: () -> Unit,
    onDismissStatus: () -> Unit = {},
    exchangeDataPath: String? = null,
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val busy = state.operation != null
    BackHandler(onBack = onBack)

    if (showClearConfirmation) {
        ClearObservationDataDialog(
            counts = state.counts ?: ObservationDataCounts(0, 0, 0),
            isClearing = state.operation == DataOperation.CLEAR,
            onConfirm = {
                showClearConfirmation = false
                onClearObservationData()
            },
            onDismiss = { if (!busy) showClearConfirmation = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Данные") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("data-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DataSection(
                    title = "Экспорт",
                    description = "Создаёт один файл с данными Bee Search. Экспорт не удаляет данные, " +
                        "не отправляет файл автоматически и не включает файлы офлайн-карт.",
                ) {
                    Button(
                        onClick = onExport,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("export-data"),
                    ) {
                        Text(if (state.operation == DataOperation.EXPORT) "Экспорт…" else "Экспортировать данные")
                    }
                    exchangeDataPath?.let { path ->
                        Text(
                            "Папка обмена: $path",
                            modifier = Modifier.testTag("export-exchange-path"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                CompletedObservationPointDeletionSection(
                    points = state.completedPoints,
                    isBusy = busy,
                    isDeleting = state.operation == DataOperation.DELETE_POINT,
                    onDelete = onDeleteCompletedPoint,
                )
            }
            item {
                val counts = state.counts
                DataSection(
                    title = "Очистка наблюдений",
                    description = if (counts == null) {
                        "Подсчёт сохранённых данных…"
                    } else {
                        "Точек наблюдения: ${counts.observationPoints}\n" +
                            "Пчёл: ${counts.bees}\n" +
                            "Циклов полёта: ${counts.flightCycles}"
                    },
                ) {
                    Button(
                        onClick = { showClearConfirmation = true },
                        enabled = !busy && counts?.isEmpty == false,
                        modifier = Modifier.fillMaxWidth().testTag("clear-observation-data"),
                    ) {
                        Text(if (state.operation == DataOperation.CLEAR) "Удаление…" else "Очистить данные наблюдений")
                    }
                    Text(
                        "Территории, наблюдатели, выбранные настройки и офлайн-карты сохранятся.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            state.status?.let { status ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(if (status.isError) "data-error" else "data-success"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                status.message,
                                color = if (status.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            TextButton(onClick = onDismissStatus) { Text("Закрыть") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DataSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            content()
        }
    }
}

@Composable
private fun ClearObservationDataDialog(
    counts: ObservationDataCounts,
    isClearing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очистить данные наблюдений?") },
        text = {
            Text(
                "Будут безвозвратно удалены:\n" +
                    "• точки наблюдения: ${counts.observationPoints}\n" +
                    "• пчёлы: ${counts.bees}\n" +
                    "• циклы полёта: ${counts.flightCycles}\n\n" +
                    "Территории и наблюдатели сохранятся. Экспорт автоматически не выполняется.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isClearing,
                modifier = Modifier.testTag("confirm-clear-observation-data"),
            ) { Text("Удалить безвозвратно") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isClearing,
                modifier = Modifier.testTag("cancel-clear-observation-data"),
            ) { Text("Отмена") }
        },
    )
}
