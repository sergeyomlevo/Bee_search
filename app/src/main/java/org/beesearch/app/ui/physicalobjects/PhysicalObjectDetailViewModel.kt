package org.beesearch.app.ui.physicalobjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

internal sealed interface PhysicalObjectDetailValue {
    val id: UUID
    data class HollowValue(val value: Hollow) : PhysicalObjectDetailValue { override val id = value.id }
    data class LogHiveValue(val value: LogHive) : PhysicalObjectDetailValue { override val id = value.id }
}

internal data class PhysicalObjectDetailUiState(
    val value: PhysicalObjectDetailValue? = null,
    val editing: Boolean = false,
    val form: PhysicalObjectFormState = PhysicalObjectFormState(),
    val isWorking: Boolean = true,
    val error: String? = null,
)

internal class PhysicalObjectDetailViewModel(
    private val objectId: UUID,
    private val repository: PhysicalObjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PhysicalObjectDetailUiState())
    val state: StateFlow<PhysicalObjectDetailUiState> = _state.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            runCatching { loadValue() }.onSuccess { value ->
                _state.value = _state.value.copy(value = value, isWorking = false, error = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(isWorking = false, error = error.message ?: "Не удалось открыть объект")
            }
        }
    }

    private suspend fun loadValue(): PhysicalObjectDetailValue =
        repository.getHollow(objectId)?.let(PhysicalObjectDetailValue::HollowValue)
            ?: repository.getLogHive(objectId)?.let(PhysicalObjectDetailValue::LogHiveValue)
            ?: error("Объект не найден")

    fun startEditing() {
        val value = _state.value.value ?: return
        _state.value = _state.value.copy(
            editing = true,
            form = value.toFormState(),
            error = null,
        )
    }

    fun cancelEditing() {
        if (!_state.value.isWorking) _state.value = _state.value.copy(editing = false, error = null)
    }

    fun updateForm(form: PhysicalObjectFormState) {
        if (!_state.value.isWorking) _state.value = _state.value.copy(form = form, error = null)
    }

    fun saveHollow(properties: HollowProperties) = save {
        PhysicalObjectDetailValue.HollowValue(repository.updateHollow(objectId, properties))
    }

    fun saveLogHive(properties: LogHiveProperties) = save {
        PhysicalObjectDetailValue.LogHiveValue(repository.updateLogHive(objectId, properties))
    }

    fun updateCoordinates(latitude: Double, longitude: Double) {
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            runCatching {
                repository.updateCoordinates(objectId, latitude, longitude)
                loadValue()
            }.onSuccess { value ->
                _state.value = _state.value.copy(value = value, isWorking = false, error = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = error.message ?: "Не удалось сохранить координаты",
                )
            }
        }
    }

    private fun save(update: suspend () -> PhysicalObjectDetailValue) {
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            runCatching { update() }
                .onSuccess { value ->
                    _state.value = _state.value.copy(
                        value = value,
                        editing = false,
                        isWorking = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        error = error.message ?: "Не удалось сохранить изменения",
                    )
                }
        }
    }

    companion object {
        fun factory(objectId: UUID, repository: PhysicalObjectRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhysicalObjectDetailViewModel(objectId, repository) as T
            }
    }
}

private fun PhysicalObjectDetailValue.toFormState(): PhysicalObjectFormState = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.properties?.let { properties ->
        PhysicalObjectFormState(
            tree = properties.tree,
            entranceHeightCm = properties.entranceHeightCm.toInput(),
            azimuthDeg = properties.entranceAzimuthDeg.toString(),
            outerDiameterCm = properties.outerDiameterCm.toInput(),
            internalDiameterCm = properties.internalDiameterCm?.toInput().orEmpty(),
            notes = properties.notes.orEmpty(),
        )
    } ?: PhysicalObjectFormState()
    is PhysicalObjectDetailValue.LogHiveValue -> value.properties?.let { properties ->
        PhysicalObjectFormState(
            tree = properties.tree,
            entranceHeightCm = properties.entranceHeightCm.toInput(),
            azimuthDeg = properties.entranceAzimuthDeg.toString(),
            outerDiameterCm = properties.outerDiameterCm.toInput(),
            internalDiameterCm = properties.internalDiameterCm.toInput(),
            material = properties.material,
            internalHeightCm = properties.internalHeightCm.toInput(),
            notes = properties.notes.orEmpty(),
        )
    } ?: PhysicalObjectFormState()
}

private fun Double.toInput(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
