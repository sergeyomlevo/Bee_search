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
        UserPointEditorState(latitude = target.latitude, longitude = target.longitude),
    )
    val state: StateFlow<UserPointEditorState> = _state.asStateFlow()

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
        val item = _state.value.photos.firstOrNull { it.id == id } ?: return
        if (_state.value.isWorking) return
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
        if (current.isWorking || current.isSaved) return
        if (current.title.isBlank()) {
            _state.value = current.copy(error = "Введите название")
            return
        }
        _state.value = current.copy(isWorking = true, error = null)
        viewModelScope.launch {
            var activation: org.beesearch.app.data.media.UserPointMediaActivation? = null
            var committed = false
            var saved: UserPoint? = null
            try {
                withContext(NonCancellable) {
                    activation = mediaStore.prepareDraftActivation(draftSessionId, pointId, _state.value.photos)
                    val now = Instant.now(clock)
                    val point = UserPoint(
                        id = pointId, territoryId = target.territoryId, title = current.title,
                        note = current.note, latitude = current.latitude, longitude = current.longitude,
                        isVisible = true, createdAt = now, updatedAt = now, photos = activation!!.photos,
                    )
                    activation!!.commit()
                    saved = repository.create(point)
                    committed = true
                }
                _state.value = _state.value.copy(isWorking = false, isSaved = true)
            } catch (cancelled: CancellationException) {
                if (!committed) activation?.rollback()
                _state.value = _state.value.copy(isWorking = false)
                throw cancelled
            } catch (failure: Throwable) {
                if (!committed) activation?.rollback()
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
