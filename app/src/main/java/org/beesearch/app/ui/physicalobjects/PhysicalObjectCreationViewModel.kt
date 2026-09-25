package org.beesearch.app.ui.physicalobjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.PhysicalObjectCreationTarget
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.media.StagedPhysicalObjectMedia
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal data class PendingPhysicalObjectMedia(
    val type: PhysicalObjectMediaType,
    val originalFileName: String?,
    val mimeType: String?,
    val source: () -> InputStream,
    val onConsumed: (() -> Unit)? = null,
)

internal data class PhysicalObjectCreationUiState(
    val form: PhysicalObjectFormState = PhysicalObjectFormState(),
    val media: List<StagedPhysicalObjectMedia> = emptyList(),
    val isWorking: Boolean = false,
    val error: String? = null,
)

internal class PhysicalObjectCreationViewModel(
    private val target: PhysicalObjectCreationTarget,
    private val repository: PhysicalObjectRepository,
    private val mediaStore: PhysicalObjectMediaFileStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val objectId = UUID.randomUUID()
    private val draftSessionId = UUID.randomUUID()
    private val _state = MutableStateFlow(PhysicalObjectCreationUiState())
    val state: StateFlow<PhysicalObjectCreationUiState> = _state.asStateFlow()

    fun updateForm(form: PhysicalObjectFormState) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(form = form, error = null)
    }

    fun addMedia(items: List<PendingPhysicalObjectMedia>) {
        if (items.isEmpty() || _state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            runCatching {
                val added = mutableListOf<StagedPhysicalObjectMedia>()
                try {
                    items.forEach { item ->
                        added += try {
                            mediaStore.stageDraftMedia(
                            draftSessionId = draftSessionId,
                            mediaId = UUID.randomUUID(),
                            type = item.type,
                            originalFileName = item.originalFileName,
                            mimeType = item.mimeType,
                            createdAt = Instant.now(clock),
                            source = item.source,
                        )
                        } finally {
                            item.onConsumed?.invoke()
                        }
                    }
                    added
                } catch (error: Throwable) {
                    added.forEach { mediaStore.deleteDraft(it) }
                    throw error
                }
            }.onSuccess { added ->
                _state.value = _state.value.copy(
                    media = _state.value.media + added,
                    isWorking = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(isWorking = false, error = error.userMessage("Не удалось добавить медиа"))
            }
        }
    }

    fun removeMedia(id: UUID) {
        val item = _state.value.media.firstOrNull { it.id == id } ?: return
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            if (mediaStore.deleteDraft(item)) {
                _state.value = _state.value.copy(
                    media = _state.value.media.filterNot { it.id == id },
                    isWorking = false,
                )
            } else {
                _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить медиа")
            }
        }
    }

    fun createHollow(properties: HollowProperties, onCreated: (UUID) -> Unit) =
        create(properties, null, onCreated)

    fun createLogHive(properties: LogHiveProperties, onCreated: (UUID) -> Unit) =
        create(null, properties, onCreated)

    private fun create(
        hollow: HollowProperties?,
        logHive: LogHiveProperties?,
        onCreated: (UUID) -> Unit,
    ) {
        if (_state.value.isWorking) return
        check((hollow != null) xor (logHive != null))
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            var activation: org.beesearch.app.data.media.PhysicalObjectMediaActivation? = null
            runCatching {
                activation = mediaStore.prepareDraftActivation(draftSessionId, objectId, _state.value.media)
                val media = requireNotNull(activation).media
                // Finish draft cleanup before Room commits the durable object. If cleanup fails,
                // the failure path can still restore every moved file without leaving a saved
                // object whose creation result is ambiguous to the UI.
                requireNotNull(activation).commit()
                when (target.type) {
                    PhysicalObjectType.HOLLOW -> repository.createHollow(
                        NewHollow(
                            id = objectId,
                            territoryId = target.territoryId,
                            creatorObserverId = target.observerId,
                            latitude = target.latitude,
                            longitude = target.longitude,
                            properties = requireNotNull(hollow),
                            media = media,
                        ),
                    )
                    PhysicalObjectType.LOG_HIVE -> repository.createLogHive(
                        NewLogHive(
                            id = objectId,
                            territoryId = target.territoryId,
                            creatorObserverId = target.observerId,
                            latitude = target.latitude,
                            longitude = target.longitude,
                            properties = requireNotNull(logHive),
                            media = media,
                        ),
                    )
                    PhysicalObjectType.APIARY -> error("Apiary creation is not part of this flow")
                }
            }.onSuccess {
                onCreated(objectId)
            }.onFailure { error ->
                runCatching { activation?.rollback() }
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = error.userMessage("Не удалось создать объект"),
                )
            }
        }
    }

    fun cancel(onCancelled: () -> Unit) {
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            if (mediaStore.discardDraft(draftSessionId)) {
                onCancelled()
            } else {
                _state.value = _state.value.copy(isWorking = false, error = "Не удалось удалить временные медиа")
            }
        }
    }

    companion object {
        fun factory(
            target: PhysicalObjectCreationTarget,
            repository: PhysicalObjectRepository,
            mediaStore: PhysicalObjectMediaFileStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PhysicalObjectCreationViewModel(target, repository, mediaStore) as T
        }
    }
}

private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
