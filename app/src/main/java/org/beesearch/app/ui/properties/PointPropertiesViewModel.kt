package org.beesearch.app.ui.properties

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
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.weather.WeatherSyncScheduler

internal data class PointPropertiesUiState(
    val detail: ObservationPointDetail? = null,
    val isLoading: Boolean = true,
    val descriptionDraft: String = "",
    val isDescriptionSaving: Boolean = false,
    val isPhotoSaving: Boolean = false,
    val errorMessage: String? = null,
)

internal class PointPropertiesViewModel(
    private val repository: ObservationRepository,
    private val fileStore: ObservationAttachmentFileStore,
    private val weatherScheduler: WeatherSyncScheduler,
    private val pointId: UUID,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(PointPropertiesUiState())
    val uiState: StateFlow<PointPropertiesUiState> = mutableState.asStateFlow()
    private var draftInitialized = false

    init {
        viewModelScope.launch {
            repository.observeObservationPointProperties(pointId).collect { detail ->
                val old = mutableState.value
                mutableState.value = old.copy(
                    detail = detail,
                    isLoading = false,
                    descriptionDraft = if (!draftInitialized) {
                        draftInitialized = true
                        detail?.point?.description.orEmpty()
                    } else old.descriptionDraft,
                    errorMessage = null,
                )
            }
        }
    }

    fun onDescriptionChanged(value: String) {
        mutableState.value = mutableState.value.copy(descriptionDraft = value)
    }

    fun saveDescription() {
        val value = mutableState.value.descriptionDraft.trimEnd().ifBlank { null }
        mutableState.value = mutableState.value.copy(isDescriptionSaving = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { repository.updateObservationPointDescription(pointId, value) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Не удалось сохранить описание") }
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
        mutableState.value = mutableState.value.copy(isPhotoSaving = true, errorMessage = null)
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
                mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Не удалось добавить фотографию")
            }
            onComplete?.invoke()
            mutableState.value = mutableState.value.copy(isPhotoSaving = false)
        }
    }

    fun deletePhoto(attachment: ObservationPointAttachment) {
        if (mutableState.value.isPhotoSaving) return
        mutableState.value = mutableState.value.copy(isPhotoSaving = true, errorMessage = null)
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
                mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Не удалось удалить фотографию")
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

    companion object {
        fun factory(
            repository: ObservationRepository,
            fileStore: ObservationAttachmentFileStore,
            weatherScheduler: WeatherSyncScheduler,
            pointId: UUID,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PointPropertiesViewModel(repository, fileStore, weatherScheduler, pointId = pointId) as T
        }
    }

}
