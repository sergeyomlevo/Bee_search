package org.beesearch.app.ui.userpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.beesearch.app.data.media.StagedUserPointPhoto
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.model.UserPointPhoto
import org.beesearch.app.domain.repository.UserPointRepository

internal data class PendingUserPointPhoto(
    val originalFileName: String?,
    val mimeType: String?,
    val source: () -> java.io.InputStream,
    val onConsumed: (() -> Unit)? = null,
)

internal data class UserPointEditorState(
    val title: String = "",
    val note: String = "",
    val latitude: Double,
    val longitude: Double,
    val photos: List<StagedUserPointPhoto> = emptyList(),
    val existingPhotos: List<UserPointPhoto> = emptyList(),
    val isEditing: Boolean = false,
    val isLoaded: Boolean = true,
    val cleanupPending: UserPoint? = null,
    val isWorking: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

internal class UserPointEditorViewModel(
    private val target: UserPointEditorTarget,
    private val repository: UserPointRepository,
    private val mediaStore: UserPointMediaStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val pointId = target.pointId ?: UUID.randomUUID()
    private val draftSessionId = UUID.randomUUID()
    private val _state = MutableStateFlow(
        UserPointEditorState(latitude = target.latitude, longitude = target.longitude,
            isEditing = target.pointId != null, isLoaded = target.pointId == null),
    )
    val state: StateFlow<UserPointEditorState> = _state.asStateFlow()

    private var original: UserPoint? = null

    init { if (target.pointId != null) retryLoad() }

    fun retryLoad() {
        if (target.pointId == null || _state.value.isLoaded || _state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                val saved = requireNotNull(repository.getPoint(target.territoryId, pointId)) { "Точка не найдена" }
                original = saved
                // Coordinates come from the route: a confirmed revision must not be overwritten by a late load.
                _state.value = _state.value.copy(title = saved.title, note = saved.note.orEmpty(),
                    existingPhotos = saved.photos, isWorking = false, isLoaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(isWorking = false, error = "Не удалось загрузить точку")
            }
        }
    }

    fun setCoordinates(latitude: Double, longitude: Double) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(latitude = latitude, longitude = longitude)
    }

    fun setTitle(value: String) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(title = value, error = null)
    }

    fun setNote(value: String) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(note = value, error = null)
    }

    fun reportError(message: String) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(error = message)
    }

    fun addPhotos(items: List<PendingUserPointPhoto>) {
        if (items.isEmpty() || _state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                val added = mutableListOf<StagedUserPointPhoto>()
                try {
                    items.forEach { item ->
                        try {
                            added += mediaStore.stageDraftMedia(
                                draftSessionId, UUID.randomUUID(), item.originalFileName,
                                item.mimeType, Instant.now(clock), item.source,
                            )
                        } finally {
                            item.onConsumed?.invoke()
                        }
                    }
                    added
                } catch (failure: Throwable) {
                    withContext(NonCancellable) { added.forEach { mediaStore.deleteDraft(it) } }
                    throw failure
                }
                _state.value = _state.value.copy(photos = _state.value.photos + added, isWorking = false)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isWorking = false)
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(isWorking = false, error = failure.userMessage("Не удалось добавить фото"))
            }
        }
    }

    fun removePhoto(id: UUID) {
        if (_state.value.isWorking || !_state.value.isLoaded) return
        if (_state.value.existingPhotos.any { it.id == id }) {
            // Saved originals stay untouched until the whole point update commits.
            _state.value = _state.value.copy(existingPhotos = _state.value.existingPhotos.filterNot { it.id == id }, error = null)
            return
        }
        val item = _state.value.photos.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                if (mediaStore.deleteDraft(item)) {
                    _state.value = _state.value.copy(photos = _state.value.photos.filterNot { it.id == id }, isWorking = false)
                } else {
                    _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить фото")
                }
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить фото")
            }
        }
    }

    fun save(onSaved: (UserPoint) -> Unit) {
        val current = _state.value
        if (current.isWorking || current.isSaved || !current.isLoaded) return
        if (current.title.isBlank()) {
            _state.value = current.copy(error = "Введите название")
            return
        }
        if (!current.latitude.isFinite() || current.latitude !in -90.0..90.0 ||
            !current.longitude.isFinite() || current.longitude !in -180.0..180.0) {
            _state.value = current.copy(error = "Уточните координаты")
            return
        }
        _state.value = current.copy(isWorking = true, error = null)
        viewModelScope.launch {
            var activation: org.beesearch.app.data.media.UserPointMediaActivation? = null
            var committed = false
            var saved: UserPoint? = null
            try {
                withContext(NonCancellable) {
                    mediaStore.requireAvailable(pointId, current.existingPhotos)
                    activation = mediaStore.prepareDraftActivation(draftSessionId, pointId, current.photos)
                    val now = Instant.now(clock)
                    val point = UserPoint(
                        id = pointId, territoryId = target.territoryId, title = current.title,
                        note = current.note, latitude = current.latitude, longitude = current.longitude,
                        isVisible = original?.isVisible ?: true, createdAt = original?.createdAt ?: now, updatedAt = now,
                        photos = current.existingPhotos + activation!!.photos,
                    )
                    activation!!.commit()
                    saved = if (target.pointId == null) repository.create(point) else repository.update(point)
                    committed = true
                    val removed = original?.photos.orEmpty().filter { old -> point.photos.none { it.id == old.id } }
                    if (removed.isNotEmpty() && !mediaStore.cleanupOwned(pointId, removed)) {
                        _state.value = _state.value.copy(cleanupPending = original!!.copy(photos = removed))
                    }
                }
                _state.value = _state.value.copy(isWorking = false, isSaved = true)
            } catch (cancelled: CancellationException) {
                if (!committed) {
                    try { activation?.rollback() }
                    catch (_: Exception) { _state.value = _state.value.copy(error = "Не удалось восстановить временные фото") }
                }
                _state.value = _state.value.copy(isWorking = false)
                throw cancelled
            } catch (failure: Throwable) {
                if (!committed) {
                    try { activation?.rollback() }
                    catch (_: Exception) { _state.value = _state.value.copy(error = "Не удалось восстановить временные фото") }
                }
                _state.value = _state.value.copy(isWorking = false, error = failure.userMessage("Не удалось сохранить точку"))
            }
            saved?.let(onSaved)
        }
    }

    fun cancel(onCancelled: () -> Unit) {
        if (_state.value.isWorking || _state.value.isSaved) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                if (mediaStore.discardDraft(draftSessionId)) onCancelled()
                else _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить временные фото")
            } catch (_: Throwable) {
                _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить временные фото")
            }
        }
    }

    override fun onCleared() {
        // The route owns explicit Cancel/Back. A ViewModel clear must never delete managed files.
        super.onCleared()
    }

    companion object {
        fun factory(target: UserPointEditorTarget, repository: UserPointRepository, mediaStore: UserPointMediaStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UserPointEditorViewModel(target, repository, mediaStore) as T
            }
    }
}

private fun Throwable.userMessage(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
