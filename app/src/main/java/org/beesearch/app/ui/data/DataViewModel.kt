package org.beesearch.app.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.BeeSearchApplication
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.repository.ObservationDataMaintenance

internal enum class DataOperation {
    LOADING,
    EXPORT,
    CLEAR,
}

internal data class DataStatus(
    val message: String,
    val isError: Boolean,
)

internal data class DataUiState(
    val counts: ObservationDataCounts? = null,
    val operation: DataOperation? = null,
    val status: DataStatus? = null,
)

internal class DataViewModel(
    private val backupExporter: BackupDocumentExporter,
    private val observationData: ObservationDataMaintenance,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun refreshCounts() {
        if (_state.value.operation != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(operation = DataOperation.LOADING)
            try {
                _state.value = _state.value.copy(
                    counts = observationData.getObservationDataCounts(),
                    operation = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    operation = null,
                    status = DataStatus("Не удалось прочитать сведения о данных.", isError = true),
                )
            }
        }
    }

    fun export(destination: Uri) {
        if (_state.value.operation != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(operation = DataOperation.EXPORT, status = null)
            try {
                backupExporter.export(destination)
                _state.value = _state.value.copy(
                    operation = null,
                    status = DataStatus("Экспорт завершён. Файл Bee Search сохранён.", isError = false),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    operation = null,
                    status = DataStatus(
                        "Не удалось экспортировать данные. Выберите другое место и повторите.",
                        isError = true,
                    ),
                )
            }
        }
    }

    fun clearObservationData() {
        if (_state.value.operation != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(operation = DataOperation.CLEAR, status = null)
            try {
                observationData.clearObservationData()
                _state.value = DataUiState(
                    counts = ObservationDataCounts(0, 0, 0),
                    status = DataStatus("Данные наблюдений удалены.", isError = false),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    operation = null,
                    status = DataStatus("Не удалось очистить данные наблюдений.", isError = true),
                )
            }
        }
    }

    fun dismissStatus() {
        _state.value = _state.value.copy(status = null)
    }

    companion object {
        fun factory(application: BeeSearchApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DataViewModel(
                    backupExporter = application.container.backupDocumentExporter,
                    observationData = application.container.observationRepository,
                ) as T
            }
    }
}
