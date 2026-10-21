package org.beesearch.app.ui.userpoints

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import org.beesearch.app.ui.media.openManagedPhoto
import org.beesearch.app.ui.physicalobjects.PhysicalObjectMediaPreview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun UserPointDetailRoute(
    territoryId: UUID,
    pointId: UUID,
    repository: UserPointRepository,
    mediaStore: UserPointMediaStore,
    onBack: () -> Unit,
    onShowOnMap: ((UserPoint) -> Unit)? = null,
    onEdit: ((UserPoint) -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
) {
    val displayModel: UserPointDisplayViewModel = viewModel(factory = UserPointDisplayViewModel.factory(repository))
    val deletionModel: UserPointDeletionViewModel = viewModel(
        key = "user-point-deletion",
        factory = UserPointDeletionViewModel.factory(repository, mediaStore),
    )
    val displayState by displayModel.state.collectAsStateWithLifecycle()
    val deletionState by deletionModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(territoryId) { displayModel.setTerritory(territoryId) }
    LaunchedEffect(territoryId, pointId) { deletionModel.clearError() }
    val scope = rememberCoroutineScope()
    val showOnMap: (() -> Unit)? = onShowOnMap?.let { callback -> { scope.launch {
        displayModel.showOnMap(pointId)?.let(callback)
    }; Unit } }
    var point by remember(territoryId, pointId) { mutableStateOf<UserPoint?>(null) }
    var loading by remember(territoryId, pointId) { mutableStateOf(true) }
    var error by remember(territoryId, pointId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(territoryId, pointId, retry) {
        loading = true
        try { point = repository.getPoint(territoryId, pointId); error = null }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Не удалось загрузить точку" }
        finally { loading = false }
    }
    var showDeleteConfirmation by remember(territoryId, pointId) { mutableStateOf(false) }
    LaunchedEffect(deletionState.error) {
        if (deletionState.error != null) showDeleteConfirmation = false
    }
    BackHandler(onBack = { if (!deletionState.isWorking) onBack() })
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("user-point-card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        org.beesearch.app.ui.points.CompactScreenHeader(
            "Моя точка",
            onBack = { if (!deletionState.isWorking) onBack() },
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> { Text(error!!); Button(onClick = { retry++ }) { Text("Повторить") } }
                point == null -> Text("Точка не найдена")
                else -> UserPointCardBody(point!!, mediaStore, showOnMap, onEdit,
                    showWorking = displayState.loading || pointId in displayState.writing,
                    showError = if (pointId in displayState.writeErrors) "Не удалось сохранить видимость" else displayState.error,
                    onRetry = if (displayState.error != null) displayModel::retryRead else showOnMap,
                    deletionWorking = deletionState.isWorking,
                    deletionError = deletionState.error,
                    onDelete = { showDeleteConfirmation = true },
                )
            }
        }
    }
    if (showDeleteConfirmation && point != null) {
        val deleting = point!!
        val hasPhotos = deleting.photos.isNotEmpty()
        AlertDialog(
            onDismissRequest = { if (!deletionState.isWorking) showDeleteConfirmation = false },
            title = { Text("Удалить точку?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        buildString {
                            append("«${deleting.title}» будет удалена из Bee Search")
                            if (hasPhotos) append(" вместе с фотографиями")
                            append(". Восстановить её нельзя. Исходные файлы в другой папке телефона не затрагиваются.")
                        },
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = false },
                        enabled = !deletionState.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("user-point-delete-cancel"),
                    ) { Text("Отмена") }
                    Button(
                        onClick = {
                            deletionModel.delete(territoryId, deleting.id) {
                                showDeleteConfirmation = false
                                (onDeleted ?: onBack)()
                            }
                        },
                        enabled = !deletionState.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("user-point-delete-confirm"),
                    ) { Text(if (deletionState.isWorking) "Удаление…" else "Удалить") }
                }
            },
            dismissButton = null,
        )
    }
}

@Composable
private fun UserPointCardBody(
    point: UserPoint, mediaStore: UserPointMediaStore, onShowOnMap: (() -> Unit)?,
    onEdit: ((UserPoint) -> Unit)?,
    showWorking: Boolean, showError: String?, onRetry: (() -> Unit)?,
    deletionWorking: Boolean, deletionError: String?, onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Text(point.title, style = MaterialTheme.typography.headlineSmall)
    point.note?.let { Text(it) }
    Text(
        text = userPointCreatedDateText(point.createdAt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("user-point-created-at"),
    )
    if (point.photos.isNotEmpty()) {
        var selectedId by remember(point.id) { mutableStateOf(point.photos.first().id) }
        val selected = point.photos.firstOrNull { it.id == selectedId } ?: point.photos.first()
        val photoFile = remember(selected.relativePath) { runCatching { mediaStore.resolve(selected.relativePath) }.getOrNull() }
        PhysicalObjectMediaPreview(photoFile, false, selected.originalFileName ?: "Фотография точки",
            Modifier.fillMaxWidth().aspectRatio(1.8f).clickable { openManagedPhoto(context, photoFile, selected.mimeType) })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(point.photos, key = { it.id }) { photo ->
                PhysicalObjectMediaPreview(runCatching { mediaStore.resolve(photo.relativePath) }.getOrNull(), false,
                    photo.originalFileName ?: "Фотография точки", Modifier.size(112.dp).clickable { selectedId = photo.id })
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Координаты", style = MaterialTheme.typography.titleMedium)
            Text(String.format(java.util.Locale.ROOT, "%.6f, %.6f", point.latitude, point.longitude))
            if (onShowOnMap != null) {
                OutlinedButton(onClick = onShowOnMap, enabled = !showWorking && !deletionWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-show-on-map")) { Text("Показать на карте") }
                if (showError != null) {
                    Text(showError, color = MaterialTheme.colorScheme.error)
                    onRetry?.let { retry -> TextButton(onClick = retry, enabled = !showWorking) { Text("Повторить") } }
                }
            }
            onEdit?.let { edit ->
                OutlinedButton(
                    onClick = { edit(point) },
                    enabled = !showWorking && !deletionWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-edit"),
                ) { Text("Редактировать") }
            }
            Button(
                onClick = onDelete,
                enabled = !showWorking && !deletionWorking,
                modifier = Modifier.fillMaxWidth().testTag("user-point-delete"),
            ) { Text("Удалить точку") }
            if (deletionError != null) {
                Text(deletionError, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("user-point-delete-error"))
                TextButton(
                    onClick = onDelete,
                    enabled = !showWorking && !deletionWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-delete-retry"),
                ) { Text("Повторить") }
            }
        }
    }
}
