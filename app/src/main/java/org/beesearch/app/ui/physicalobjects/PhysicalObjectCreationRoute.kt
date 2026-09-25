package org.beesearch.app.ui.physicalobjects

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.PhysicalObjectCreationTarget
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import org.beesearch.app.ui.properties.displayName
import java.io.File
import java.util.UUID

@Composable
internal fun PhysicalObjectCreationRoute(
    target: PhysicalObjectCreationTarget,
    repository: PhysicalObjectRepository,
    mediaStore: PhysicalObjectMediaFileStore,
    headingProvider: HeadingProvider,
    onCancel: () -> Unit,
    onCreated: (UUID) -> Unit,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val model: PhysicalObjectCreationViewModel = viewModel(
        key = "physical-object-create-${target.requestId}",
        factory = PhysicalObjectCreationViewModel.factory(target, repository, mediaStore),
    )
    val state by model.state.collectAsStateWithLifecycle()
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        model.addMedia(uris.mapNotNull { uri -> uri.toPendingMedia(resolver) })
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath ?: return@rememberLauncherForActivityResult
        cameraPath = null
        val file = File(path)
        if (saved && file.isFile) {
            model.addMedia(
                listOf(
                    PendingPhysicalObjectMedia(
                        type = PhysicalObjectMediaType.IMAGE,
                        originalFileName = null,
                        mimeType = "image/jpeg",
                        source = file::inputStream,
                        onConsumed = { file.delete() },
                    ),
                ),
            )
        } else {
            file.delete()
        }
    }
    val cancel = { model.cancel(onCancel) }
    BackHandler(onBack = cancel)

    val mediaDrafts = state.media.map { item ->
        PhysicalObjectMediaDraft(
            id = item.id,
            label = item.originalFileName ?: if (item.type == PhysicalObjectMediaType.VIDEO) "Видео" else "Фото",
            isVideo = item.type == PhysicalObjectMediaType.VIDEO,
        )
    }
    val pick = {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val camera = {
        val file = mediaStore.cameraCaptureFile(UUID.randomUUID())
        cameraPath = file.absolutePath
        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val reference = HeadingReference(target.latitude, target.longitude)
    when (target.type) {
        PhysicalObjectType.HOLLOW -> HollowForm(
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = mediaDrafts,
            onStateChange = model::updateForm,
            onPickMedia = pick,
            onTakePhoto = camera,
            onRemoveMedia = model::removeMedia,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, _ -> model.createHollow(properties, onCreated) },
            onCancel = cancel,
        )
        PhysicalObjectType.LOG_HIVE -> LogHiveForm(
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = mediaDrafts,
            onStateChange = model::updateForm,
            onPickMedia = pick,
            onTakePhoto = camera,
            onRemoveMedia = model::removeMedia,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, _ -> model.createLogHive(properties, onCreated) },
            onCancel = cancel,
        )
        PhysicalObjectType.APIARY -> error("Apiary creation is not part of this flow")
    }
}

private fun Uri.toPendingMedia(resolver: android.content.ContentResolver): PendingPhysicalObjectMedia? {
    val mimeType = resolver.getType(this)
    val fileName = resolver.displayName(this)
    val type = when {
        mimeType?.startsWith("image/") == true -> PhysicalObjectMediaType.IMAGE
        mimeType?.startsWith("video/") == true -> PhysicalObjectMediaType.VIDEO
        fileName.hasVideoExtension() -> PhysicalObjectMediaType.VIDEO
        fileName != null -> PhysicalObjectMediaType.IMAGE
        else -> return null
    }
    return PendingPhysicalObjectMedia(
        type = type,
        originalFileName = fileName,
        mimeType = mimeType,
        source = { resolver.openInputStream(this) ?: error("Не удалось открыть медиа") },
    )
}

private fun String?.hasVideoExtension(): Boolean = this?.substringAfterLast('.', "")
    ?.lowercase() in setOf("mp4", "m4v", "mov", "webm", "3gp", "mkv")

private const val MAX_PICKED_MEDIA = 20
