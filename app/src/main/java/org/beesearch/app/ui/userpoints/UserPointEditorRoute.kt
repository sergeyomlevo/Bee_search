package org.beesearch.app.ui.userpoints

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.UUID
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import org.beesearch.app.ui.media.openManagedPhoto
import org.beesearch.app.ui.physicalobjects.PhysicalObjectMediaDraft

@Composable
internal fun UserPointEditorRoute(
    target: UserPointEditorTarget,
    repository: UserPointRepository,
    mediaStore: UserPointMediaStore,
    onCancel: () -> Unit,
    onSaved: (UserPoint) -> Unit,
    onChangeLocation: (Double, Double) -> Unit,
) {
    val model: UserPointEditorViewModel = viewModel(
        key = "user-point-editor-${target.requestId}",
        factory = UserPointEditorViewModel.factory(target, repository, mediaStore),
    )
    val deletionModel: UserPointDeletionViewModel = viewModel(
        key = "user-point-deletion",
        factory = UserPointDeletionViewModel.factory(repository, mediaStore),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(target.coordinateRevision) { model.setCoordinates(target.latitude, target.longitude) }
    LaunchedEffect(state.photos, state.existingPhotos) {
        val stagedMissing = state.photos.any { photo ->
            runCatching { mediaStore.resolveDraft(photo.relativePath).isFile }.getOrDefault(false).not()
        }
        val existingMissing = state.existingPhotos.any { photo ->
            runCatching { mediaStore.resolve(photo.relativePath).isFile }.getOrDefault(false).not()
        }
        if (stagedMissing || existingMissing) {
            model.reportError("Фото недоступно")
        }
    }
    LaunchedEffect(state.error) {
        if (state.error == "Введите название") focusRequester.requestFocus()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        try {
            model.addPhotos(uris.mapNotNull { it.toPendingPhoto(resolver) })
        } catch (_: Throwable) {
            model.reportError("Не удалось добавить фото")
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath ?: return@rememberLauncherForActivityResult
        cameraPath = null
        val file = File(path)
        if (saved && file.isFile) {
            try {
                model.addPhotos(listOf(PendingUserPointPhoto(null, "image/jpeg", file::inputStream, { file.delete() })))
            } catch (_: Throwable) {
                file.delete()
                model.reportError("Не удалось добавить фото")
            }
        } else file.delete()
    }
    val cancel = { model.cancel(onCancel) }
    BackHandler(onBack = cancel)
    val existingMedia = state.existingPhotos.mapNotNull { photo ->
        runCatching {
            PhysicalObjectMediaDraft(
                id = photo.id,
                label = photo.originalFileName ?: "Фото",
                isVideo = false,
                previewFile = mediaStore.resolve(photo.relativePath),
            )
        }.getOrNull()
    }
    val stagedMedia = state.photos.mapNotNull { photo ->
        runCatching {
            PhysicalObjectMediaDraft(
                id = photo.id,
                label = photo.originalFileName ?: "Фото",
                isVideo = false,
                previewFile = mediaStore.resolveDraft(photo.relativePath),
            )
        }.getOrNull()
    }
    UserPointEditor(
        state = state,
        media = existingMedia + stagedMedia,
        titleFocusRequester = focusRequester,
        onTitleChanged = model::setTitle,
        onNoteChanged = model::setNote,
        onChangeLocation = { onChangeLocation(state.latitude, state.longitude) },
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val file = mediaStore.cameraCaptureFile(UUID.randomUUID())
            cameraPath = file.absolutePath
            try {
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            } catch (_: Throwable) {
                cameraPath = null
                file.delete()
                model.reportError("Не удалось открыть камеру")
            }
        },
        onRemovePhoto = model::removePhoto,
        onOpenPhoto = { id ->
            val staged = state.photos.firstOrNull { it.id == id }
            if (staged != null) {
                staged.let { photo ->
                runCatching { mediaStore.resolveDraft(photo.relativePath) }.getOrNull()?.let { file ->
                    openManagedPhoto(context, file, photo.mimeType)
                }
                }
            } else {
                state.existingPhotos.firstOrNull { it.id == id }?.let { photo ->
                    runCatching { mediaStore.resolve(photo.relativePath) }.getOrNull()?.let { file ->
                        openManagedPhoto(context, file, photo.mimeType)
                    }
                }
            }
        },
        onSave = {
            model.save { saved ->
                model.state.value.cleanupPending?.let(deletionModel::retainCleanup)
                onSaved(saved)
            }
        },
        onCancel = cancel,
        onRetryLoad = model::retryLoad,
    )
}

private fun Uri.toPendingPhoto(resolver: android.content.ContentResolver): PendingUserPointPhoto? {
    val mime = resolver.getType(this)
    if (mime != null && !mime.startsWith("image/", ignoreCase = true)) return null
    val name = runCatching {
        resolver.query(this, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return PendingUserPointPhoto(name, mime ?: "image/*", { resolver.openInputStream(this) ?: error("Не удалось открыть фото") })
}
