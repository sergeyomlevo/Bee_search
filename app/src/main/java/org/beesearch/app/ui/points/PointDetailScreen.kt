@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.points

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.data.pointexport.ObservationPointDocumentExporter
import org.beesearch.app.data.pointexport.observationPointExportFileName
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.weather.WeatherSyncScheduler
import org.beesearch.app.ui.properties.PointDescriptionSection
import org.beesearch.app.ui.properties.PointPhotoSection
import org.beesearch.app.ui.properties.WeatherBlock
import org.beesearch.app.ui.properties.displayName

/**
 * The single screen of one ObservationPoint: stored point data, description, photos, weather snapshot
 * and the existing Bee/FlightCycle history. It replaces the former separate point-properties screen.
 */
@Composable
internal fun PointDetailRoute(
    pointId: UUID,
    repository: ObservationRepository,
    maintenance: ObservationDataMaintenance,
    fileStore: ObservationAttachmentFileStore,
    weatherScheduler: WeatherSyncScheduler,
    pointExporter: ObservationPointDocumentExporter,
    exchangeStorage: BeeSearchExchangeStorage,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: PointDetailViewModel = viewModel(
        key = "point-detail-$pointId",
        factory = PointDetailViewModel.factory(
            repository = repository,
            maintenance = maintenance,
            fileStore = fileStore,
            weatherScheduler = weatherScheduler,
            pointExporter = pointExporter,
            pointId = pointId,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ObservationPointAttachment?>(null) }
    var confirmDeletePoint by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val createExportDocument = rememberLauncherForActivityResult(
        CreateExchangeDocument(
            mimeType = "application/zip",
            initialFolder = exchangeStorage.initialDocumentUri(ExchangeFolder.DATA),
        ),
    ) { destination: Uri? ->
        destination?.let(viewModel::exportPoint)
    }

    val importUri = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.importPhoto(
                source = { resolver.openInputStream(uri) ?: error("Не удалось открыть фотографию") },
                originalFileName = resolver.displayName(uri),
                mimeType = resolver.getType(uri),
            )
        }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath ?: return@rememberLauncherForActivityResult
        cameraPath = null
        val file = File(path)
        if (saved && file.exists()) {
            viewModel.importPhoto(
                source = { file.inputStream() },
                originalFileName = file.name,
                mimeType = "image/jpeg",
                onComplete = { file.delete() },
            )
        } else {
            file.delete()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PointDetailScreen(
            state = state,
            fileStore = fileStore,
            onBack = onBack,
            onStartDescriptionEditing = viewModel::startDescriptionEditing,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onCancelDescriptionEditing = viewModel::cancelDescriptionEditing,
            onSaveDescription = viewModel::saveDescription,
            onTakePhoto = {
                val file = fileStore.cameraCaptureFile(UUID.randomUUID())
                cameraPath = file.absolutePath
                takePicture.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                )
            },
            onPickPhoto = {
                importUri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDeletePhoto = viewModel::deletePhoto,
            onRetryWeather = viewModel::retryWeather,
            onExportPoint = {
                state.detail?.let { createExportDocument.launch(observationPointExportFileName(it)) }
            },
            onDeletePoint = { viewModel.deletePoint(onDeleted = onDeleted) },
            onDismissMessage = viewModel::dismissMessage,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
}

@Composable
internal fun PointDetailScreen(
    state: PointDetailUiState,
    fileStore: ObservationAttachmentFileStore? = null,
    onBack: () -> Unit,
    onStartDescriptionEditing: () -> Unit = {},
    onDescriptionChanged: (String) -> Unit = {},
    onCancelDescriptionEditing: () -> Unit = {},
    onSaveDescription: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onDeletePhoto: (ObservationPointAttachment) -> Unit = {},
    onRetryWeather: () -> Unit = {},
    onExportPoint: () -> Unit = {},
    onDeletePoint: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var confirmDeletePoint by rememberSaveable { mutableStateOf(false) }
    var deletePhotoTarget by remember { mutableStateOf<ObservationPointAttachment?>(null) }
    val detail = state.detail
    val deletable = detail?.point?.completedAt != null && !state.isDeletingPoint
    Column(Modifier.fillMaxSize()) {
        CompactScreenHeader(
            title = detail?.let { "Точка №${it.point.pointNumber}" } ?: "Точка",
            onBack = onBack,
        ) {
            if (detail != null) {
                HeaderMenuButton(
                    items = buildList {
                        add(HeaderMenuItem(
                            label = "Экспортировать точку",
                            testTag = "point-menu-export",
                            onClick = onExportPoint,
                        ))
                        if (deletable) add(HeaderMenuItem(
                            label = "Удалить точку",
                            testTag = "point-menu-delete",
                            isDestructive = true,
                            onClick = { confirmDeletePoint = true },
                        ))
                    },
                    menuDescription = "Действия с этой точкой",
                    testTag = "point-menu",
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Точка не найдена")
                }
                else -> PointDetailContent(
                    detail = detail,
                    state = state,
                    fileStore = fileStore,
                    onStartDescriptionEditing = onStartDescriptionEditing,
                    onDescriptionChanged = onDescriptionChanged,
                    onCancelDescriptionEditing = onCancelDescriptionEditing,
                    onSaveDescription = onSaveDescription,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onDeletePhoto = { attachment -> deletePhotoTarget = attachment },
                    onRetryWeather = onRetryWeather,
                )
            }
        }
        state.message?.let { message ->
            PointDetailMessageRow(message = message, onDismiss = onDismissMessage)
        }
    }

    deletePhotoTarget?.let { attachment ->
        AlertDialog(
            onDismissRequest = { deletePhotoTarget = null },
            title = { Text("Удалить фотографию?") },
            text = { Text("Файл будет удалён из приложения.") },
            confirmButton = {
                TextButton(
                    onClick = { deletePhotoTarget = null; onDeletePhoto(attachment) },
                    modifier = Modifier.testTag("confirm-delete-photo"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletePhotoTarget = null },
                    modifier = Modifier.testTag("cancel-delete-photo"),
                ) { Text("Отмена") }
            },
        )
    }

    if (confirmDeletePoint) {
        PointDeleteDialog(
            detail = detail,
            isDeleting = state.isDeletingPoint,
            onConfirm = { confirmDeletePoint = false; onDeletePoint() },
            onDismiss = { if (!state.isDeletingPoint) confirmDeletePoint = false },
        )
    }
}

@Composable
private fun PointDetailContent(
    detail: ObservationPointDetail,
    state: PointDetailUiState,
    fileStore: ObservationAttachmentFileStore?,
    onStartDescriptionEditing: () -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCancelDescriptionEditing: () -> Unit,
    onSaveDescription: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (ObservationPointAttachment) -> Unit,
    onRetryWeather: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("point-detail-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PointSection("Основное") {
                DetailField("Территория", "${detail.territory.code} — ${detail.territory.name}")
                DetailField("Точка", pointDisplayName(detail.point.pointNumber, detail.point.code))
                DetailField("Дата и время", formatPointDateTime(detail.point.createdAt))
                DetailField(
                    "Координаты",
                    String.format(Locale.ROOT, "%.6f, %.6f", detail.point.latitude, detail.point.longitude),
                )
                detail.point.gpsAccuracyM?.let { accuracy ->
                    DetailField("Точность GPS", String.format(Locale.ROOT, "%.1f м", accuracy))
                }
                DetailField("Наблюдатель", "${detail.observer.displayName} (${detail.observer.code})")
                DetailField("Результат", pointResultLabel(detail.point.beePresenceResult))
            }
        }
        item {
            PointSection("Описание") {
                PointDescriptionSection(
                    detail = detail,
                    descriptionDraft = state.descriptionDraft,
                    isEditing = state.isDescriptionEditing,
                    isSaving = state.isDescriptionSaving,
                    onStartEditing = onStartDescriptionEditing,
                    onDescriptionChanged = onDescriptionChanged,
                    onCancelEditing = onCancelDescriptionEditing,
                    onSaveDescription = onSaveDescription,
                )
            }
        }
        item {
            PointSection("Фото") {
                PointPhotoSection(
                    detail = detail,
                    fileStore = fileStore,
                    isPhotoSaving = state.isPhotoSaving,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onDeletePhoto = onDeletePhoto,
                )
            }
        }
        item {
            PointSection("Погода") {
                WeatherBlock(detail.weather, onRetryWeather)
            }
        }
        item {
            PointSection("Пчёлы") {
                if (detail.beeHistories.isEmpty()) {
                    Text("Пчёл: 0", modifier = Modifier.padding(bottom = 8.dp))
                } else {
                    BeeFlightMatrixView(
                        matrix = remember(detail.beeHistories) {
                            buildBeeFlightMatrix(detail.beeHistories)
                        },
                    )
                }
            }
        }
    }
}

/** Section with a heading; the heading is skipped when a section only shows its own empty state. */
@Composable
private fun PointSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/**
 * Vertical key/value field.
 *
 * The former layout put the label and the value in one row, which squeezed the value into a narrow
 * right column at large system font scale. Stacking them keeps the value on the full width.
 */
@Composable
internal fun DetailField(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PointDetailMessageRow(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f).testTag("point-detail-message"),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("point-detail-message-dismiss")) {
            Text("Закрыть")
        }
    }
}

/** Confirmation that names exactly the point being deleted. */
@Composable
private fun PointDeleteDialog(
    detail: ObservationPointDetail?,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail?.let { "Удалить Точка №${it.point.pointNumber}?" } ?: "Удалить точку?") },
        text = {
            Text(
                buildString {
                    detail?.let {
                        append(formatPointDateTime(it.point.createdAt))
                        append("\nТерритория: ${it.territory.code} — ${it.territory.name}")
                        append("\n\n")
                    }
                    append("Точка будет удалена вместе со своими пчёлами, циклами полёта, ")
                    append("описанием, фотографиями и погодой. Остальные точки сохранятся.")
                },
                modifier = Modifier.testTag("point-delete-confirm-scope"),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("confirm-delete-point"),
            ) { Text("Удалить точку") }
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

internal fun formatCompletedFlightDuration(departure: Instant, returned: Instant): String {
    val seconds = Duration.between(departure, returned).seconds.coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
}
