@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.InputStream
import java.util.UUID
import org.beesearch.app.ObservationPointPreparationDraft
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.StagedObservationPointPhoto
import org.beesearch.app.ui.properties.decodePhotoThumbnail
import org.beesearch.app.ui.properties.displayName

@Composable
internal fun ObservationPointPreparationScreen(
    draft: ObservationPointPreparationDraft,
    fileStore: ObservationAttachmentFileStore,
    onDescriptionChanged: (String) -> Unit,
    onImportPhoto: (() -> InputStream, String?, String?, (() -> Unit)?) -> Unit,
    onDeletePhoto: (StagedObservationPointPhoto) -> Unit,
    onConfirmPoint: () -> Unit,
    onRecordNoBeesFound: () -> Unit,
    onAbort: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var showNoBeesConfirmation by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onImportPhoto(
                { resolver.openInputStream(uri) ?: error("Не удалось открыть фотографию") },
                resolver.displayName(uri),
                resolver.getType(uri),
                null,
            )
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath ?: return@rememberLauncherForActivityResult
        cameraPath = null
        val file = File(path)
        if (saved && file.exists()) {
            onImportPhoto(
                { file.inputStream() },
                null,
                "image/jpeg",
                { file.delete() },
            )
        } else {
            file.delete()
        }
    }
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
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = onDescriptionChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("observation-point-draft-description"),
                    minLines = 3,
                    maxLines = 8,
                    label = { Text("Описание точки") },
                    enabled = !draft.isSaving,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val file = fileStore.cameraCaptureFile(UUID.randomUUID())
                            cameraPath = file.absolutePath
                            takePhoto.launch(
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                ),
                            )
                        },
                        enabled = !draft.isSaving && !draft.isPhotoSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("observation-point-draft-take-photo"),
                    ) {
                        Text("＋", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.size(8.dp))
                        Text("Сделать фото")
                    }
                    Button(
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !draft.isSaving && !draft.isPhotoSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("observation-point-draft-pick-photo"),
                    ) { Text(if (draft.isPhotoSaving) "Добавление…" else "Выбрать фото") }
                }
            }
            items(draft.photos, key = { it.id }) { photo ->
                DraftPhotoRow(
                    photo = photo,
                    fileStore = fileStore,
                    enabled = !draft.isSaving && !draft.isPhotoSaving,
                    onDelete = { onDeletePhoto(photo) },
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
                    enabled = !draft.isSaving && !draft.isPhotoSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add-observation-point"),
                ) { Text(if (draft.isSaving) "Сохранение…" else "Добавить") }
            }
            item {
                OutlinedButton(
                    onClick = { showNoBeesConfirmation = true },
                    enabled = !draft.isSaving && !draft.isPhotoSaving,
                    modifier = Modifier.fillMaxWidth().testTag("record-no-bees-from-draft"),
                ) { Text("Пчёлы отсутствуют") }
            }
        }
    }
}

@Composable
private fun DraftPhotoRow(
    photo: StagedObservationPointPhoto,
    fileStore: ObservationAttachmentFileStore,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    val file = remember(photo.relativePath) {
        runCatching { fileStore.resolveDraftPhoto(photo.relativePath) }.getOrNull()
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.absolutePath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            file?.let { decodePhotoThumbnail(it)?.asImageBitmap() }
        }
    }
    Card(Modifier.fillMaxWidth().testTag("draft-photo-${photo.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Фотография создаваемой точки",
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Text("Фото") }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(photo.originalFileName ?: "Фотография")
                Text(
                    "${photo.byteSize / 1024} КБ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Text("×", modifier = Modifier.semantics { contentDescription = "Удалить фотографию" })
            }
        }
    }
}
