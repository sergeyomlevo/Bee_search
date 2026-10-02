@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.PhysicalObjectCoordinateUpdate
import org.beesearch.app.PhysicalObjectDeletionBlockers
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.objectexport.physicalObjectExportFileName
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

@Composable
internal fun PhysicalObjectDetailRoute(
    objectId: UUID,
    repository: PhysicalObjectRepository,
    mediaStore: PhysicalObjectMediaFileStore,
    territories: List<Territory>,
    observers: List<Observer>,
    headingProvider: HeadingProvider,
    exchangeStorage: BeeSearchExchangeStorage,
    coordinateUpdate: PhysicalObjectCoordinateUpdate? = null,
    onCoordinateUpdateHandled: (UUID) -> Unit = {},
    deletionBlockers: PhysicalObjectDeletionBlockers? = null,
    onDeletionBlockersHandled: (UUID) -> Unit = {},
    onEditCoordinates: (UUID, String, Double, Double) -> Unit = { _, _, _, _ -> },
    onShowOnMap: (Double, Double) -> Unit = { _, _ -> },
    onDelete: () -> Unit = {},
    onExport: (Uri) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val model: PhysicalObjectDetailViewModel = viewModel(
        key = "physical-object-detail-$objectId",
        factory = PhysicalObjectDetailViewModel.factory(objectId, repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    // The picker owns the file name and the location; the app only suggests both. A cancelled choice
    // returns null and changes nothing, so it is not reported as an error.
    val createExportDocument = rememberLauncherForActivityResult(
        CreateExchangeDocument(
            mimeType = "application/zip",
            initialFolder = exchangeStorage.initialDocumentUri(ExchangeFolder.DATA),
        ),
    ) { destination: Uri? ->
        destination?.let(onExport)
    }
    LaunchedEffect(coordinateUpdate?.requestId, state.isWorking) {
        val request = coordinateUpdate ?: return@LaunchedEffect
        if (state.isWorking) return@LaunchedEffect
        onCoordinateUpdateHandled(request.requestId)
        model.updateCoordinates(request.latitude, request.longitude)
    }
    val back = { if (state.editing) model.cancelEditing() else onBack() }
    BackHandler(onBack = back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.editing) {
                        Text("Редактирование")
                    } else {
                        val value = state.value
                        Column {
                            Text(value?.displayTitle() ?: "Объект")
                            value?.name()?.let { Text(value.designation(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = back,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Назад" },
                    ) { Text("←") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val value = state.value
            when {
                value == null && state.isWorking -> CircularProgressIndicator()
                value == null -> Text(state.error ?: "Объект не найден")
                state.editing -> PhysicalObjectEditForm(value, state, model, headingProvider, mediaStore)
                else -> PhysicalObjectDetails(
                    value = value,
                    territoryLabel = territories.firstOrNull { it.id == value.territoryId() }
                        ?.let { "${it.code} · ${it.name}" } ?: "Не найдена",
                    creatorLabel = observers.firstOrNull { it.id == value.creatorId() }
                        ?.let { "${it.code} · ${it.displayName}" } ?: "Не указан",
                    mediaStore = mediaStore,
                    onEdit = model::startEditing,
                    onEditCoordinates = {
                        onEditCoordinates(value.id, value.designation(), value.latitude(), value.longitude())
                    },
                    onShowOnMap = { onShowOnMap(value.latitude(), value.longitude()) },
                    onDelete = onDelete,
                    onExport = {
                        createExportDocument.launch(
                            physicalObjectExportFileName(
                                type = value.type(),
                                sequenceNumber = value.sequenceNumber(),
                                territoryCode = territories.firstOrNull { it.id == value.territoryId() }
                                    ?.code.orEmpty(),
                                createdAt = value.createdAt(),
                                objectId = value.id,
                            ),
                        )
                    },
                    onOpenMedia = { media ->
                        val file = mediaStore.resolve(media.relativePath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val mimeType = media.mimeType ?: if (media.type == PhysicalObjectMediaType.VIDEO) {
                            "video/*"
                        } else {
                            "image/*"
                        }
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "Нет приложения для просмотра медиа", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }
    deletionBlockers?.let { blockers ->
        PhysicalObjectDeleteBlockedDialog(
            references = blockers.references,
            onDismiss = { onDeletionBlockersHandled(blockers.requestId) },
        )
    }
}

@Composable
private fun PhysicalObjectDetails(
    value: PhysicalObjectDetailValue,
    territoryLabel: String,
    creatorLabel: String,
    mediaStore: PhysicalObjectMediaFileStore,
    onEdit: () -> Unit,
    onEditCoordinates: () -> Unit,
    onShowOnMap: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onOpenMedia: (PhysicalObjectMedia) -> Unit,
) = when (value) {
    is PhysicalObjectDetailValue.HollowValue ->
        HollowCard(
            value.value, territoryLabel, creatorLabel, onEdit,
            onEditCoordinates, onShowOnMap, onDelete,
            onExport = onExport,
            mediaFile = { mediaStore.resolve(it.relativePath) },
            onOpenMedia = onOpenMedia,
        )
    is PhysicalObjectDetailValue.LogHiveValue ->
        LogHiveCard(
            value.value, territoryLabel, creatorLabel, onEdit,
            onEditCoordinates, onShowOnMap, onDelete,
            onExport = onExport,
            mediaFile = { mediaStore.resolve(it.relativePath) },
            onOpenMedia = onOpenMedia,
        )
}

@Composable
private fun PhysicalObjectEditForm(
    value: PhysicalObjectDetailValue,
    state: PhysicalObjectDetailUiState,
    model: PhysicalObjectDetailViewModel,
    headingProvider: HeadingProvider,
    mediaStore: PhysicalObjectMediaFileStore,
) {
    val media = value.media().map { item -> item.toDraft(mediaStore.resolve(item.relativePath)) }
    val reference = HeadingReference(value.latitude(), value.longitude())
    when (value) {
        is PhysicalObjectDetailValue.HollowValue -> HollowForm(
            title = value.value.designation,
            submitLabel = "Сохранить",
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = media,
            onStateChange = model::updateForm,
            showMediaActions = false,
            showHeader = false,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, name, _ -> model.saveHollow(properties, name) },
            onCancel = model::cancelEditing,
        )
        is PhysicalObjectDetailValue.LogHiveValue -> LogHiveForm(
            title = value.value.designation,
            submitLabel = "Сохранить",
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = media,
            onStateChange = model::updateForm,
            showMediaActions = false,
            showHeader = false,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, name, _ -> model.saveLogHive(properties, name) },
            onCancel = model::cancelEditing,
        )
    }
}

private fun PhysicalObjectMedia.toDraft(file: java.io.File) = PhysicalObjectMediaDraft(
    id = id,
    label = originalFileName ?: if (type == PhysicalObjectMediaType.VIDEO) "Видео" else "Фото",
    isVideo = type == PhysicalObjectMediaType.VIDEO,
    previewFile = file,
)

private fun PhysicalObjectDetailValue.territoryId(): UUID = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.territoryId
    is PhysicalObjectDetailValue.LogHiveValue -> value.territoryId
}

private fun PhysicalObjectDetailValue.creatorId(): UUID? = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.creatorObserverId
    is PhysicalObjectDetailValue.LogHiveValue -> value.creatorObserverId
}

private fun PhysicalObjectDetailValue.latitude(): Double = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.latitude
    is PhysicalObjectDetailValue.LogHiveValue -> value.latitude
}

private fun PhysicalObjectDetailValue.longitude(): Double = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.longitude
    is PhysicalObjectDetailValue.LogHiveValue -> value.longitude
}

private fun PhysicalObjectDetailValue.media(): List<PhysicalObjectMedia> = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.media
    is PhysicalObjectDetailValue.LogHiveValue -> value.media
}

private fun PhysicalObjectDetailValue.designation(): String = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.designation
    is PhysicalObjectDetailValue.LogHiveValue -> value.designation
}

private fun PhysicalObjectDetailValue.type(): PhysicalObjectType = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> PhysicalObjectType.HOLLOW
    is PhysicalObjectDetailValue.LogHiveValue -> PhysicalObjectType.LOG_HIVE
}

private fun PhysicalObjectDetailValue.sequenceNumber(): Int = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.sequenceNumber
    is PhysicalObjectDetailValue.LogHiveValue -> value.sequenceNumber
}

private fun PhysicalObjectDetailValue.createdAt(): java.time.Instant = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.createdAt
    is PhysicalObjectDetailValue.LogHiveValue -> value.createdAt
}

private fun PhysicalObjectDetailValue.name(): String? = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.name
    is PhysicalObjectDetailValue.LogHiveValue -> value.name
}

private fun PhysicalObjectDetailValue.displayTitle(): String = name() ?: designation()
