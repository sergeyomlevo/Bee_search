package org.beesearch.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.data.gpx.GpxAdmissionRejection
import org.beesearch.app.data.gpx.GpxCapturedRequest
import org.beesearch.app.data.gpx.GpxFailureCategory
import org.beesearch.app.data.gpx.GpxImportGateway
import org.beesearch.app.data.gpx.GpxOriginalStoreError
import org.beesearch.app.data.gpx.GpxPreparationResult
import org.beesearch.app.domain.model.TerritoryRequiredException

/** User-visible import states; all persistence semantics stay in the existing workflow/coordinator. */
internal sealed interface GpxImportUiState {
    data object Idle : GpxImportUiState

    data class Importing(val fileName: String, val territoryName: String) : GpxImportUiState

    data class Checking(val fileName: String, val territoryName: String) : GpxImportUiState

    data class Duplicate(val fileName: String, val territoryName: String) : GpxImportUiState

    data class Success(
        val fileName: String,
        val territoryName: String,
        val hasWarnings: Boolean = false,
    ) : GpxImportUiState

    data class Failure(
        val reason: GpxImportFailure,
        val fileName: String,
        val territoryName: String,
    ) : GpxImportUiState

    data class Cancelled(val fileName: String, val territoryName: String) : GpxImportUiState

    data class DuplicateDeclined(val fileName: String, val territoryName: String) : GpxImportUiState

    data class ContextChanged(val fileName: String, val territoryName: String) : GpxImportUiState

    data class CheckFailed(
        val fileName: String,
        val territoryName: String,
        val afterCancel: Boolean,
    ) : GpxImportUiState
}

internal enum class GpxImportFailure { INVALID_GPX, LIMIT, READ, NO_SPACE }

/** One-shot request for the system picker; the UI consumes it with [GpxImportViewModel.onPickerLaunched]. */
internal data class GpxPickerRequest(val token: Long)

/**
 * UI state for the approved GPX import flow.
 *
 * The workflow remains the only owner of capture/execute/duplicate/READY correlation. This class
 * only maps its durable results to user-visible states and keeps the captured handle until the
 * system picker returns.
 */
internal class GpxImportViewModel(
    private val workflow: GpxImportGateway,
) : ViewModel() {

    private val _state = MutableStateFlow<GpxImportUiState>(GpxImportUiState.Idle)
    val state: StateFlow<GpxImportUiState> = _state.asStateFlow()

    private val _pickerRequest = MutableStateFlow<GpxPickerRequest?>(null)
    val pickerRequest: StateFlow<GpxPickerRequest?> = _pickerRequest.asStateFlow()

    private var captured: GpxCapturedRequest? = null
    private var capturedTerritoryId: UUID? = null
    private var capturedObserverId: UUID? = null
    private var capturedTerritoryName: String = ""
    private var selectedDisplayName: String? = null
    private var fileName: String = ""
    private var openSource: (() -> InputStream?)? = null
    private var requestUuid: UUID? = null
    private var importJob: Job? = null
    private var captureJob: Job? = null
    private var pickerToken = 0L

    /** Starts a new explicit intent and asks the UI to launch the system picker. */
    fun beginImport(territoryName: String) {
        val current = _state.value
        if (current is GpxImportUiState.Importing ||
            current is GpxImportUiState.Checking ||
            current is GpxImportUiState.Duplicate ||
            captureJob?.isActive == true
        ) return
        resetInternal()
        captureJob = viewModelScope.launch {
            try {
                val intent = workflow.capture()
                captured = intent
                capturedTerritoryId = intent.request.territoryId
                capturedObserverId = intent.request.observerId
                capturedTerritoryName = territoryName
                _pickerRequest.value = GpxPickerRequest(++pickerToken)
            } catch (_: TerritoryRequiredException) {
                _state.value = GpxImportUiState.ContextChanged("", territoryName)
            } catch (_: Exception) {
                _state.value = GpxImportUiState.ContextChanged("", territoryName)
            } finally {
                captureJob = null
            }
        }
    }

    fun onPickerLaunched(token: Long) {
        if (_pickerRequest.value?.token == token) _pickerRequest.value = null
    }

    /** Picker Back/cancel is neutral: no error, no new state, the nested page stays. */
    fun onPickerCancelled() {
        captured = null
        openSource = null
        _pickerRequest.value = null
        _state.value = GpxImportUiState.Idle
    }

    fun onFilePicked(displayName: String?, open: () -> InputStream?) {
        val intent = captured ?: return
        selectedDisplayName = displayName?.takeIf(String::isNotBlank)
        fileName = selectedDisplayName ?: "GPX-файл"
        openSource = open
        requestUuid = intent.request.requestUuid
        _state.value = GpxImportUiState.Importing(fileName, capturedTerritoryName)
        importJob?.cancel()
        importJob = viewModelScope.launch { runImport(intent, confirmRepeatedOriginal = false) }
    }

    /** Cancel starts a durable result check; a committed READY still wins over the cancellation. */
    fun cancelImport() {
        if (_state.value !is GpxImportUiState.Importing) return
        _state.value = GpxImportUiState.Checking(fileName, capturedTerritoryName)
        val job = importJob
        importJob = null
        job?.cancel()
        viewModelScope.launch {
            job?.join()
            settleAfterCancel()
        }
    }

    fun confirmDuplicate() {
        if (_state.value !is GpxImportUiState.Duplicate) return
        _state.value = GpxImportUiState.Importing(fileName, capturedTerritoryName)
        importJob = viewModelScope.launch {
            try {
                val fresh = workflow.capture(selectedDisplayName)
                if (fresh.request.territoryId != capturedTerritoryId ||
                    fresh.request.observerId != capturedObserverId
                ) {
                    _state.value = GpxImportUiState.ContextChanged(fileName, capturedTerritoryName)
                    return@launch
                }
                captured = fresh
                requestUuid = fresh.request.requestUuid
                runImport(fresh, confirmRepeatedOriginal = true)
            } catch (_: TerritoryRequiredException) {
                _state.value = GpxImportUiState.ContextChanged(fileName, capturedTerritoryName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                settleAfterUnknownException()
            }
        }
    }

    fun declineDuplicate() {
        if (_state.value !is GpxImportUiState.Duplicate) return
        _state.value = GpxImportUiState.DuplicateDeclined(fileName, capturedTerritoryName)
        captured = null
        openSource = null
    }

    fun retryCheck() {
        val current = _state.value as? GpxImportUiState.CheckFailed ?: return
        _state.value = GpxImportUiState.Checking(current.fileName, current.territoryName)
        viewModelScope.launch {
            try {
                val ready = workflow.lookup(requestUuid ?: return@launch)
                _state.value = if (ready != null) {
                    GpxImportUiState.Success(current.fileName, current.territoryName, ready.hasWarnings)
                } else if (current.afterCancel) {
                    GpxImportUiState.Cancelled(current.fileName, current.territoryName)
                } else {
                    GpxImportUiState.Failure(
                        GpxImportFailure.INVALID_GPX, current.fileName, current.territoryName,
                    )
                }
            } catch (_: Exception) {
                _state.value = GpxImportUiState.CheckFailed(
                    current.fileName, current.territoryName, current.afterCancel,
                )
            }
        }
    }

    /** Clears the task state; the caller then closes the panel or starts another import. */
    fun reset() {
        resetInternal()
    }

    private suspend fun runImport(intent: GpxCapturedRequest, confirmRepeatedOriginal: Boolean) {
        val open = openSource ?: run {
            _state.value = GpxImportUiState.Failure(
                GpxImportFailure.READ, fileName, capturedTerritoryName,
            )
            return
        }
        try {
            val result = workflow.execute(
                captured = intent,
                open = { open() ?: throw IOException("GPX_SOURCE_UNAVAILABLE") },
                confirmRepeatedOriginal = confirmRepeatedOriginal,
                displayName = selectedDisplayName,
            )
            handleResult(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            settleAfterUnknownException()
        }
    }

    private suspend fun handleResult(result: GpxPreparationResult) {
        when (result) {
            is GpxPreparationResult.Ready -> {
                _state.value = GpxImportUiState.Success(
                    fileName, capturedTerritoryName, result.hasWarnings,
                )
                finishJob()
            }
            is GpxPreparationResult.Failed -> when {
                result.kind == GpxPreparationResult.Kind.ADMISSION &&
                    result.admission == GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED -> {
                    _state.value = GpxImportUiState.Duplicate(fileName, capturedTerritoryName)
                    finishJob()
                }
                result.kind == GpxPreparationResult.Kind.REQUEST -> {
                    _state.value = GpxImportUiState.ContextChanged(fileName, capturedTerritoryName)
                    finishJob()
                }
                result.kind == GpxPreparationResult.Kind.PERSISTENCE -> {
                    settleAfterTypedFailure()
                    finishJob()
                }
                else -> {
                    val failure = failureFor(result)
                    _state.value = if (failure != null) {
                        GpxImportUiState.Failure(failure, fileName, capturedTerritoryName)
                    } else {
                        GpxImportUiState.Failure(GpxImportFailure.INVALID_GPX, fileName, capturedTerritoryName)
                    }
                    finishJob()
                }
            }
            is GpxPreparationResult.Aborted -> {
                _state.value = GpxImportUiState.Cancelled(fileName, capturedTerritoryName)
                finishJob()
            }
            is GpxPreparationResult.Prepared -> settleAfterUnknownException()
        }
    }

    private fun failureFor(result: GpxPreparationResult.Failed): GpxImportFailure? = when (result.kind) {
        GpxPreparationResult.Kind.PARSE -> when (result.parse?.category) {
            GpxFailureCategory.LIMIT_EXCEEDED -> GpxImportFailure.LIMIT
            GpxFailureCategory.INPUT_IO_ERROR -> GpxImportFailure.READ
            else -> GpxImportFailure.INVALID_GPX
        }
        GpxPreparationResult.Kind.ORIGINAL -> when (result.original) {
            GpxOriginalStoreError.STORAGE_EXHAUSTED -> GpxImportFailure.NO_SPACE
            GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED -> GpxImportFailure.LIMIT
            GpxOriginalStoreError.IO_FAILURE,
            GpxOriginalStoreError.INVALID_EMPTY_SOURCE,
            GpxOriginalStoreError.CONFLICT,
            GpxOriginalStoreError.IDENTITY_MISMATCH,
            GpxOriginalStoreError.UNSAFE_PATH_OR_FILE_TYPE,
            GpxOriginalStoreError.OWNERSHIP_VIOLATION,
            GpxOriginalStoreError.INCOMPLETE_PUBLICATION,
            GpxOriginalStoreError.UNSUPPORTED_FILESYSTEM_OPERATION,
            -> GpxImportFailure.READ
            null -> GpxImportFailure.INVALID_GPX
        }
        GpxPreparationResult.Kind.ADMISSION -> GpxImportFailure.LIMIT
        else -> null
    }

    private suspend fun settleAfterCancel() {
        val uuid = requestUuid ?: return
        try {
            val ready = workflow.lookup(uuid)
            _state.value = if (ready != null) {
                GpxImportUiState.Success(fileName, capturedTerritoryName, ready.hasWarnings)
            } else {
                GpxImportUiState.Cancelled(fileName, capturedTerritoryName)
            }
        } catch (_: Exception) {
            _state.value = GpxImportUiState.CheckFailed(fileName, capturedTerritoryName, afterCancel = true)
        }
    }

    private suspend fun settleAfterTypedFailure() {
        val uuid = requestUuid ?: return
        try {
            val ready = workflow.lookup(uuid)
            _state.value = if (ready != null) {
                GpxImportUiState.Success(fileName, capturedTerritoryName, ready.hasWarnings)
            } else {
                GpxImportUiState.Failure(GpxImportFailure.INVALID_GPX, fileName, capturedTerritoryName)
            }
        } catch (_: Exception) {
            _state.value = GpxImportUiState.CheckFailed(fileName, capturedTerritoryName, afterCancel = false)
        }
    }

    private suspend fun settleAfterUnknownException() {
        val uuid = requestUuid ?: return
        try {
            val ready = workflow.lookup(uuid)
            _state.value = if (ready != null) {
                GpxImportUiState.Success(fileName, capturedTerritoryName, ready.hasWarnings)
            } else {
                GpxImportUiState.Failure(GpxImportFailure.INVALID_GPX, fileName, capturedTerritoryName)
            }
        } catch (_: Exception) {
            _state.value = GpxImportUiState.CheckFailed(fileName, capturedTerritoryName, afterCancel = false)
        }
    }

    private fun finishJob() {
        importJob = null
    }

    private fun resetInternal() {
        captureJob?.cancel()
        captureJob = null
        importJob?.cancel()
        importJob = null
        captured = null
        capturedTerritoryId = null
        capturedObserverId = null
        capturedTerritoryName = ""
        fileName = ""
        selectedDisplayName = null
        openSource = null
        requestUuid = null
        _pickerRequest.value = null
        _state.value = GpxImportUiState.Idle
    }

    override fun onCleared() {
        captureJob?.cancel()
        importJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(workflow: GpxImportGateway): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GpxImportViewModel(workflow) as T
            }
    }
}
