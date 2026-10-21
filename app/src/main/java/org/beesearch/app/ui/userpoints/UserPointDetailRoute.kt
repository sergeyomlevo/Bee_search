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
) {
    val displayModel: UserPointDisplayViewModel = viewModel(factory = UserPointDisplayViewModel.factory(repository))
    val displayState by displayModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(territoryId) { displayModel.setTerritory(territoryId) }
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
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("user-point-card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        org.beesearch.app.ui.points.CompactScreenHeader("Моя точка", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> { Text(error!!); Button(onClick = { retry++ }) { Text("Повторить") } }
                point == null -> Text("Точка не найдена")
                else -> UserPointCardBody(point!!, mediaStore, showOnMap,
                    showWorking = displayState.loading || pointId in displayState.writing,
                    showError = if (pointId in displayState.writeErrors) "Не удалось сохранить видимость" else displayState.error,
                    onRetry = if (displayState.error != null) displayModel::retryRead else showOnMap,
                )
            }
        }
    }
}

@Composable
private fun UserPointCardBody(
    point: UserPoint, mediaStore: UserPointMediaStore, onShowOnMap: (() -> Unit)?,
    showWorking: Boolean, showError: String?, onRetry: (() -> Unit)?,
) {
    val context = LocalContext.current
    Text(point.title, style = MaterialTheme.typography.headlineSmall)
    point.note?.let { Text(it) }
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
                OutlinedButton(onClick = onShowOnMap, enabled = !showWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-show-on-map")) { Text("Показать на карте") }
                if (showError != null) {
                    Text(showError, color = MaterialTheme.colorScheme.error)
                    onRetry?.let { retry -> TextButton(onClick = retry, enabled = !showWorking) { Text("Повторить") } }
                }
            }
        }
    }
}
