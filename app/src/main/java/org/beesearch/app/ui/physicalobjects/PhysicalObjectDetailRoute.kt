@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

@Composable
internal fun PhysicalObjectDetailRoute(
    objectId: UUID,
    repository: PhysicalObjectRepository,
    territories: List<Territory>,
    observers: List<Observer>,
    headingProvider: HeadingProvider,
    onBack: () -> Unit,
) {
    val model: PhysicalObjectDetailViewModel = viewModel(
        key = "physical-object-detail-$objectId",
        factory = PhysicalObjectDetailViewModel.factory(objectId, repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val back = { if (state.editing) model.cancelEditing() else onBack() }
    BackHandler(onBack = back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Редактирование" else "Объект") },
                navigationIcon = { TextButton(onClick = back) { Text("Назад") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val value = state.value
            when {
                value == null && state.isWorking -> CircularProgressIndicator()
                value == null -> Text(state.error ?: "Объект не найден")
                state.editing -> PhysicalObjectEditForm(value, state, model, headingProvider)
                else -> PhysicalObjectDetails(
                    value = value,
                    territoryLabel = territories.firstOrNull { it.id == value.territoryId() }
                        ?.let { "${it.code} · ${it.name}" } ?: "Не найдена",
                    creatorLabel = observers.firstOrNull { it.id == value.creatorId() }
                        ?.let { "${it.code} · ${it.displayName}" } ?: "Не указан",
                    onEdit = model::startEditing,
                )
            }
        }
    }
}

@Composable
private fun PhysicalObjectDetails(
    value: PhysicalObjectDetailValue,
    territoryLabel: String,
    creatorLabel: String,
    onEdit: () -> Unit,
) = when (value) {
    is PhysicalObjectDetailValue.HollowValue ->
        HollowCard(value.value, territoryLabel, creatorLabel, onEdit)
    is PhysicalObjectDetailValue.LogHiveValue ->
        LogHiveCard(value.value, territoryLabel, creatorLabel, onEdit)
}

@Composable
private fun PhysicalObjectEditForm(
    value: PhysicalObjectDetailValue,
    state: PhysicalObjectDetailUiState,
    model: PhysicalObjectDetailViewModel,
    headingProvider: HeadingProvider,
) {
    val media = value.media().map(PhysicalObjectMedia::toDraft)
    val reference = HeadingReference(value.latitude(), value.longitude())
    when (value) {
        is PhysicalObjectDetailValue.HollowValue -> HollowForm(
            title = value.value.designation,
            submitLabel = "Сохранить",
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = media,
            onStateChange = model::updateForm,
            showMediaActions = false,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, _ -> model.saveHollow(properties) },
            onCancel = model::cancelEditing,
        )
        is PhysicalObjectDetailValue.LogHiveValue -> LogHiveForm(
            title = value.value.designation,
            submitLabel = "Сохранить",
            initial = state.form,
            headingProvider = headingProvider,
            headingReference = reference,
            media = media,
            onStateChange = model::updateForm,
            showMediaActions = false,
            isWorking = state.isWorking,
            message = state.error,
            onSubmit = { properties, _ -> model.saveLogHive(properties) },
            onCancel = model::cancelEditing,
        )
    }
}

private fun PhysicalObjectMedia.toDraft() = PhysicalObjectMediaDraft(
    id = id,
    label = originalFileName ?: if (type == PhysicalObjectMediaType.VIDEO) "Видео" else "Фото",
    isVideo = type == PhysicalObjectMediaType.VIDEO,
)

private fun PhysicalObjectDetailValue.territoryId(): UUID = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.territoryId
    is PhysicalObjectDetailValue.LogHiveValue -> value.territoryId
}

private fun PhysicalObjectDetailValue.creatorId(): UUID? = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.creatorObserverId
    is PhysicalObjectDetailValue.LogHiveValue -> value.creatorObserverId
}

private fun PhysicalObjectDetailValue.latitude(): Double = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.latitude
    is PhysicalObjectDetailValue.LogHiveValue -> value.latitude
}

private fun PhysicalObjectDetailValue.longitude(): Double = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.longitude
    is PhysicalObjectDetailValue.LogHiveValue -> value.longitude
}

private fun PhysicalObjectDetailValue.media(): List<PhysicalObjectMedia> = when (this) {
    is PhysicalObjectDetailValue.HollowValue -> value.media
    is PhysicalObjectDetailValue.LogHiveValue -> value.media
}
