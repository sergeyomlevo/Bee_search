package org.beesearch.app.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.data.media.AttachmentDeletionBatch
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointNotCompletedException
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.weather.WeatherSyncScheduler

/**
 * State of the single ObservationPoint screen.
 *
 * The screen shows the stored point data together with its description, photos and weather snapshot,
 * so one ViewModel owns both reading the point graph and editing its properties.
 */
internal data class PointDetailUiState(
    val detail: ObservationPointDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val descriptionDraft: String = "",
    val isDescriptionEditing: Boolean = false,
    val isDescriptionSaving: Boolean = false,
    val isPhotoSaving: Boolean = false,
    val isDeletingPoint: Boolean = false,
    val message: String? = null,
)

internal class PointDetailViewModel(
    private val repository: ObservationRepository,
    private val maintenance: ObservationDataMaintenance,
    private val fileStore: ObservationAttachmentFileStore,
    private val weatherScheduler: WeatherSyncScheduler,
    private val pointId: UUID,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(PointDetailUiState())
    val uiState: StateFlow<PointDetailUiState> = mutableState.asStateFlow()
    private var draftInitialized = false

    init {
        viewModelScope.launch {
            repository.observeObservationPointProperties(pointId).collect { detail ->
                val old = mutableState.value
                mutableState.value = old.copy(
                    detail = detail,
                    isLoading = false,
                    notFound = detail == null,
                    descriptionDraft = if (!draftInitialized) {
                        draftInitialized = true
                        detail?.point?.description.orEmpty()
                    } else {
                        old.descriptionDraft
                    },
                )
            }
        }
    }

    fun startDescriptionEditing() {
        mutableState.value = mutableState.value.copy(isDescriptionEditing = true, message = null)
    }

    fun cancelDescriptionEditing() {
        mutableState.value = mutableState.value.copy(
            isDescriptionEditing = false,
            descriptionDraft = mutableState.value.detail?.point?.description.orEmpty(),
        )
    }

    fun onDescriptionChanged(value: String) {
        mutableState.value = mutableState.value.copy(descriptionDraft = value)
    }

    fun saveDescription() {
        val value = mutableState.value.descriptionDraft.trimEnd().ifBlank { null }
        mutableState.value = mutableState.value.copy(isDescriptionSaving = true, message = null)
        viewModelScope.launch {
            runCatching { repository.updateObservationPointDescription(pointId, value) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(isDescriptionEditing = false)
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        message = error.message ?: "Не удалось сохранить описание",
                    )
                }
            mutableState.value = mutableState.value.copy(isDescriptionSaving = false)
        }
    }

    fun importPhoto(
        source: () -> InputStream,
        originalFileName: String?,
        mimeType: String?,
        onComplete: (() -> Unit)? = null,
    ) {
        if (mutableState.value.isPhotoSaving) return
        mutableState.value = mutableState.value.copy(isPhotoSaving = true, message = null)
        viewModelScope.launch {
            val attachmentId = UUID.randomUUID()
            runCatching {
                val stored = fileStore.importPhoto(pointId, attachmentId, source)
                try {
                    repository.insertObservationPointAttachment(
                        ObservationPointAttachment(
                            id = attachmentId,
                            observationPointId = pointId,
                            type = AttachmentType.PHOTO,
                            relativePath = stored.relativePath,
                            originalFileName = originalFileName,
                            mimeType = mimeType,
                            byteSize = stored.byteSize,
                            sha256 = stored.sha256,
                            createdAt = Instant.now(clock),
                        ),
                    )
                } catch (error: Exception) {
                    fileStore.delete(stored.relativePath)
                    throw error
                }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    message = error.message ?: "Не удалось добавить фотографию",
                )
            }
            onComplete?.invoke()
            mutableState.value = mutableState.value.copy(isPhotoSaving = false)
        }
    }

    fun deletePhoto(attachment: ObservationPointAttachment) {
        if (mutableState.value.isPhotoSaving) return
        mutableState.value = mutableState.value.copy(isPhotoSaving = true, message = null)
        viewModelScope.launch {
            var batch: AttachmentDeletionBatch? = null
            var metadataDeleted = false
            runCatching {
                batch = fileStore.stageDeletion(listOf(attachment.relativePath))
                val deleted = repository.deleteObservationPointAttachment(attachment.id)
                if (deleted == null) error("Фотография уже удалена")
                metadataDeleted = true
                batch!!.commit()
            }.onFailure { error ->
                if (!metadataDeleted) runCatching { batch?.rollback() }
                mutableState.value = mutableState.value.copy(
                    message = error.message ?: "Не удалось удалить фотографию",
                )
            }
            mutableState.value = mutableState.value.copy(isPhotoSaving = false)
        }
    }

    fun retryWeather() {
        viewModelScope.launch {
            repository.resetWeatherPending(pointId)
            weatherScheduler.enqueue()
        }
    }

    /**
     * Deletes this ObservationPoint through the existing selective-delete backend.
     *
     * [onDeleted] is invoked exactly once, only after persistence succeeded, so the caller can leave
     * the screen without modelling the navigation as durable state.
     */
    fun deletePoint(onDeleted: () -> Unit) {
        if (mutableState.value.isDeletingPoint) return
        mutableState.value = mutableState.value.copy(isDeletingPoint = true, message = null)
        viewModelScope.launch {
            runCatching { maintenance.deleteCompletedObservationPoint(pointId) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(isDeletingPoint = false)
                    onDeleted()
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        isDeletingPoint = false,
                        message = when (error) {
                            is ObservationPointNotCompletedException ->
                                "Удалить можно только завершённую точку наблюдения."
                            is EntityNotFoundException -> "Точка наблюдения больше не найдена."
                            else -> "Не удалось удалить точку наблюдения."
                        },
                    )
                }
        }
    }

    fun dismissMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    companion object {
        fun factory(
            repository: ObservationRepository,
            maintenance: ObservationDataMaintenance,
            fileStore: ObservationAttachmentFileStore,
            weatherScheduler: WeatherSyncScheduler,
            pointId: UUID,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PointDetailViewModel(repository, maintenance, fileStore, weatherScheduler, pointId) as T
        }
    }
}
