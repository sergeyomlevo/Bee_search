package org.beesearch.app.ui.properties

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.WeatherStatus

/**
 * Reusable ObservationPoint property sections.
 *
 * They live in this package but are composed by the single ObservationPoint screen, so there is one
 * implementation of the description, photo and weather UI and no second screen for the same data.
 */

internal const val ADD_DESCRIPTION_LABEL = "Добавить описание"
internal const val EDIT_DESCRIPTION_LABEL = "Изменить"
internal const val TAKE_PHOTO_LABEL = "Сделать фото"
internal const val PICK_PHOTO_LABEL = "Выбрать фото"

/** Description section: a compact empty state, the stored text, or an inline editor. */
@Composable
internal fun PointDescriptionSection(
    detail: ObservationPointDetail,
    descriptionDraft: String,
    isEditing: Boolean,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    onStartEditing: () -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onSaveDescription: () -> Unit,
) {
    val stored = detail.point.description?.takeIf { it.isNotBlank() }
    Column(modifier.fillMaxWidth().testTag("point-description")) {
        when {
            isEditing -> {
                OutlinedTextField(
                    value = descriptionDraft,
                    onValueChange = onDescriptionChanged,
                    modifier = Modifier.fillMaxWidth().testTag("point-description-field"),
                    minLines = 3,
                    maxLines = 8,
                    label = { Text("Описание точки") },
                    enabled = !isSaving,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = onSaveDescription,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("point-description-save"),
                    ) { Text(if (isSaving) "Сохранение…" else "Сохранить") }
                    TextButton(
                        onClick = onCancelEditing,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("point-description-cancel"),
                    ) { Text("Отмена") }
                }
            }
            stored != null -> {
                Text(stored, modifier = Modifier.testTag("point-description-text"))
                TextButton(
                    onClick = onStartEditing,
                    modifier = Modifier.testTag("point-description-edit"),
                ) { Text(EDIT_DESCRIPTION_LABEL) }
            }
            else -> {
                Text(
                    text = "Описания пока нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onStartEditing,
                    modifier = Modifier.testTag("point-description-add"),
                ) { Text(ADD_DESCRIPTION_LABEL) }
            }
        }
    }
}

/** Photo section: capture, pick, preview and delete individual photos. */
@Composable
internal fun PointPhotoSection(
    detail: ObservationPointDetail,
    fileStore: ObservationAttachmentFileStore?,
    isPhotoSaving: Boolean,
    modifier: Modifier = Modifier,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (ObservationPointAttachment) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onTakePhoto,
                enabled = !isPhotoSaving && fileStore != null,
                modifier = Modifier.weight(1f).testTag("point-photo-take"),
            ) {
                Text("＋", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(8.dp))
                Text(TAKE_PHOTO_LABEL)
            }
            Button(
                onClick = onPickPhoto,
                enabled = !isPhotoSaving && fileStore != null,
                modifier = Modifier.weight(1f).testTag("point-photo-pick"),
            ) { Text(PICK_PHOTO_LABEL) }
        }
        if (detail.attachments.isEmpty()) {
            Text("Фотографий пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        detail.attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                fileStore = fileStore,
                onDelete = if (isPhotoSaving) null else onDeletePhoto,
            )
        }
    }
}

@Composable
internal fun AttachmentRow(
    attachment: ObservationPointAttachment,
    fileStore: ObservationAttachmentFileStore?,
    onDelete: ((ObservationPointAttachment) -> Unit)? = null,
) {
    val file = remember(attachment.relativePath, fileStore) {
        fileStore?.let { store -> runCatching { store.resolve(attachment.relativePath) }.getOrNull() }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.absolutePath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            file?.let { decodePhotoThumbnail(it)?.asImageBitmap() }
        }
    }
    Card(Modifier.fillMaxWidth().testTag("point-photo-${attachment.id}")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) {
                Image(
                    bitmap!!,
                    contentDescription = "Фотография точки",
                    modifier = Modifier.size(96.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Text("Фото") }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(attachment.originalFileName ?: "Фотография")
                Text("${attachment.byteSize / 1024} КБ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDelete != null) {
                IconButton(onClick = { onDelete(attachment) }, modifier = Modifier.size(48.dp)) {
                    Text("×", modifier = Modifier.semantics { contentDescription = "Удалить фотографию" })
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (weather?.status) {
            WeatherStatus.LOADED -> {
                Text("Температура: ${formatDecimal(weather.temperatureC)} °C", Modifier.testTag("weather-loaded"))
                Text("Ветер: ${formatDecimal(weather.windSpeedMps)} м/с")
                Text("Направление: ${formatDecimal(weather.windDirectionDeg)}° (${windDirectionLabel(weather.windDirectionDeg)})")
            }
            WeatherStatus.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Погода временно недоступна")
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("weather-retry"),
                    ) { Text("Повторить") }
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Погода: ожидает подключения")
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("weather-retry"),
                    ) { Text("Повторить") }
                }
            }
        }
        Text(
            "Open-Meteo",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { uriHandler.openUri("https://open-meteo.com/") }
                .semantics { role = Role.Button }
                .testTag("weather-attribution"),
        )
    }
}

internal fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

internal fun decodePhotoThumbnail(file: File): android.graphics.Bitmap? {
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

private fun formatDecimal(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"

internal fun windDirectionLabel(degrees: Double?): String {
    if (degrees == null || !degrees.isFinite()) return "—"
    val labels = listOf(
        "С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ",
        "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ",
    )
    val index = (((degrees % 360.0) + 360.0) % 360.0 / 22.5 + 0.5).toInt() % labels.size
    return labels[index]
}
