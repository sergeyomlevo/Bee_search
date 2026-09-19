@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.properties

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.weather.WeatherSyncScheduler

@Composable
internal fun PointPropertiesRoute(
    pointId: UUID,
    repository: ObservationRepository,
    fileStore: ObservationAttachmentFileStore,
    weatherScheduler: WeatherSyncScheduler,
    onBack: () -> Unit,
) {
    val vm: PointPropertiesViewModel = viewModel(
        key = "point-properties-$pointId",
        factory = PointPropertiesViewModel.factory(repository, fileStore, weatherScheduler, pointId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    PointPropertiesScreen(state, vm, fileStore, onBack)
}

@Composable
internal fun PointPropertiesScreen(
    state: PointPropertiesUiState,
    viewModel: PointPropertiesViewModel,
    fileStore: ObservationAttachmentFileStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ObservationPointAttachment?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val importUri = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(
            source = { resolver.openInputStream(uri) ?: error("Не удалось открыть фотографию") },
            originalFileName = resolver.displayName(uri),
            mimeType = resolver.getType(uri),
        )
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
        } else file.delete()
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Свойства точки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.detail == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Точка не найдена") }
            else -> PointPropertiesContent(
                detail = state.detail,
                state = state,
                fileStore = fileStore,
                modifier = Modifier.fillMaxSize().padding(padding),
                onDescriptionChanged = viewModel::onDescriptionChanged,
                onSaveDescription = viewModel::saveDescription,
                onTakePhoto = {
                    val file = fileStore.cameraCaptureFile(UUID.randomUUID())
                    cameraPath = file.absolutePath
                    takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                },
                onPickPhoto = { importUri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDeletePhoto = { deleteTarget = it },
                onRetryWeather = viewModel::retryWeather,
            )
        }
    }
    deleteTarget?.let { attachment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить фотографию?") },
            text = { Text("Файл будет удалён из приложения.") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; viewModel.deletePhoto(attachment) }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it) }
    }
}

@Composable
internal fun PointPropertiesContent(
    detail: ObservationPointDetail,
    state: PointPropertiesUiState,
    fileStore: ObservationAttachmentFileStore,
    modifier: Modifier,
    onDescriptionChanged: (String) -> Unit,
    onSaveDescription: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (ObservationPointAttachment) -> Unit,
    onRetryWeather: () -> Unit,
) {
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Точка ${detail.point.pointNumber}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(formatCreatedAt(detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("Описание", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.descriptionDraft,
                onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth().testTag("point-properties-description"),
                minLines = 3,
                maxLines = 8,
                label = { Text("Описание точки") },
                enabled = !state.isDescriptionSaving,
            )
            Button(
                onClick = onSaveDescription,
                enabled = !state.isDescriptionSaving,
                modifier = Modifier.padding(top = 8.dp).testTag("point-properties-save-description"),
            ) { Text(if (state.isDescriptionSaving) "Сохранение…" else "Сохранить") }
        }
        item { HorizontalDivider() }
        item {
            Text("Фотографии", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().testTag("point-properties-take-photo")) {
                    Text("＋", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(8.dp)); Text("Сделать фото")
                }
                Button(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth().testTag("point-properties-pick-photo")) { Text("Выбрать существующее фото") }
            }
        }
        if (detail.attachments.isEmpty()) item { Text("Фотографий пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(detail.attachments, key = { it.id }) { attachment ->
            AttachmentRow(attachment, fileStore, onDeletePhoto)
        }
        item { HorizontalDivider() }
        item { WeatherBlock(detail.weather, onRetryWeather) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun AttachmentRow(
    attachment: ObservationPointAttachment,
    fileStore: ObservationAttachmentFileStore,
    onDelete: ((ObservationPointAttachment) -> Unit)? = null,
) {
    val file = remember(attachment.relativePath) { runCatching { fileStore.resolve(attachment.relativePath) }.getOrNull() }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.absolutePath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            file?.let { decodePhotoThumbnail(it)?.asImageBitmap() }
        }
    }
    Card(Modifier.fillMaxWidth().testTag("point-photo-${attachment.id}")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) Image(bitmap!!, contentDescription = "Фотография точки", modifier = Modifier.size(96.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Text("Фото") }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(attachment.originalFileName ?: "Фотография") ; Text("${attachment.byteSize / 1024} КБ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (onDelete != null) IconButton(onClick = { onDelete(attachment) }, modifier = Modifier.size(48.dp)) {
                Text("×", modifier = Modifier.semantics { this.contentDescription = "Удалить фотографию" })
            }
        }
    }
}

@Composable
internal fun WeatherBlock(
    weather: ObservationPointWeather?,
    onRetry: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    Text("Погода", style = MaterialTheme.typography.titleMedium)
    when (weather?.status) {
        WeatherStatus.LOADED -> {
            Text("Температура: ${formatDecimal(weather.temperatureC)} °C", Modifier.testTag("weather-loaded"))
            Text("Ветер: ${formatDecimal(weather.windSpeedMps)} м/с")
            Text("Направление: ${formatDecimal(weather.windDirectionDeg)}° (${windDirectionLabel(weather.windDirectionDeg)})")
        }
        WeatherStatus.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Погода временно недоступна") ; if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Повторить") } }
        else -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Погода: ожидает подключения") ; if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Повторить") } }
    }
    Text(
        "Данные погоды: Open-Meteo",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).clickable { uriHandler.openUri("https://open-meteo.com/") }.semantics { role = Role.Button }.testTag("weather-attribution"),
    )
}

private fun ContentResolver.displayName(uri: Uri): String? = query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

private fun decodePhotoThumbnail(file: File): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 512 || bounds.outHeight / sampleSize > 512) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun formatCreatedAt(detail: ObservationPointDetail): String = detail.point.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT))
private fun formatDecimal(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"

internal fun windDirectionLabel(degrees: Double?): String {
    if (degrees == null || !degrees.isFinite()) return "—"
    val labels = listOf("С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ", "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ")
    val index = (((degrees % 360.0) + 360.0) % 360.0 / 22.5 + 0.5).toInt() % labels.size
    return labels[index]
}
