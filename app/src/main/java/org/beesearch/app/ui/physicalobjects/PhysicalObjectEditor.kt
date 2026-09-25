@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import java.util.UUID

data class PhysicalObjectMediaDraft(
    val id: UUID = UUID.randomUUID(),
    val label: String,
    val isVideo: Boolean,
)

@Composable
fun HollowForm(
    title: String = "Новое дупло",
    submitLabel: String = "Создать",
    initial: PhysicalObjectFormState = PhysicalObjectFormState(),
    headingProvider: HeadingProvider = HeadingProvider { flowOf(HeadingState.Unavailable("Компас недоступен")) },
    headingReference: HeadingReference = HeadingReference(0.0, 0.0),
    media: List<PhysicalObjectMediaDraft> = emptyList(),
    onStateChange: (PhysicalObjectFormState) -> Unit = {},
    onPickMedia: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRemoveMedia: (UUID) -> Unit = {},
    showMediaActions: Boolean = true,
    isWorking: Boolean = false,
    message: String? = null,
    onSubmit: (HollowProperties, List<PhysicalObjectMediaDraft>) -> Unit,
    onCancel: () -> Unit = {},
) = PhysicalObjectForm(
    title = title, submitLabel = submitLabel, state = initial, isLogHive = false,
    headingProvider = headingProvider, headingReference = headingReference,
    media = media, onStateChange = onStateChange, onPickMedia = onPickMedia,
    onTakePhoto = onTakePhoto, onRemoveMedia = onRemoveMedia,
    showMediaActions = showMediaActions, isWorking = isWorking, message = message,
    onSubmit = { result, items -> result.hollow?.let { onSubmit(it, items) } }, onCancel = onCancel,
)

@Composable
fun LogHiveForm(
    title: String = "Новая колода",
    submitLabel: String = "Создать",
    initial: PhysicalObjectFormState = PhysicalObjectFormState(),
    headingProvider: HeadingProvider = HeadingProvider { flowOf(HeadingState.Unavailable("Компас недоступен")) },
    headingReference: HeadingReference = HeadingReference(0.0, 0.0),
    media: List<PhysicalObjectMediaDraft> = emptyList(),
    onStateChange: (PhysicalObjectFormState) -> Unit = {},
    onPickMedia: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRemoveMedia: (UUID) -> Unit = {},
    showMediaActions: Boolean = true,
    isWorking: Boolean = false,
    message: String? = null,
    onSubmit: (LogHiveProperties, List<PhysicalObjectMediaDraft>) -> Unit,
    onCancel: () -> Unit = {},
) = PhysicalObjectForm(
    title = title, submitLabel = submitLabel, state = initial, isLogHive = true,
    headingProvider = headingProvider, headingReference = headingReference,
    media = media, onStateChange = onStateChange, onPickMedia = onPickMedia,
    onTakePhoto = onTakePhoto, onRemoveMedia = onRemoveMedia,
    showMediaActions = showMediaActions, isWorking = isWorking, message = message,
    onSubmit = { result, items -> result.logHive?.let { onSubmit(it, items) } }, onCancel = onCancel,
)

@Composable
private fun PhysicalObjectForm(
    title: String,
    submitLabel: String,
    state: PhysicalObjectFormState,
    isLogHive: Boolean,
    headingProvider: HeadingProvider,
    headingReference: HeadingReference,
    media: List<PhysicalObjectMediaDraft>,
    onStateChange: (PhysicalObjectFormState) -> Unit,
    onPickMedia: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveMedia: (UUID) -> Unit,
    showMediaActions: Boolean,
    isWorking: Boolean,
    message: String?,
    onSubmit: (PhysicalObjectFormValidation, List<PhysicalObjectMediaDraft>) -> Unit,
    onCancel: () -> Unit,
) {
    val heading by remember(headingProvider, headingReference) {
        headingProvider.updates(headingReference)
    }.collectAsStateWithLifecycle(initialValue = HeadingState.Initializing)
    var draft by remember(state) { mutableStateOf(state) }
    LaunchedEffect(draft) { onStateChange(draft) }
    val update: (PhysicalObjectFormState) -> Unit = { draft = it }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Field("Дерево", draft.tree, "tree", update, error = draft.errors["tree"], enabled = !isWorking) { draft.copy(tree = it) }
        Field("Высота летка над землёй, см", draft.entranceHeightCm, "entranceHeightCm", update, enabled = !isWorking, error = draft.errors["entranceHeightCm"]) { draft.copy(entranceHeightCm = it) }
        HeadingField(heading, draft, update, enabled = !isWorking)
        Field("Наружный диаметр, см", draft.outerDiameterCm, "outerDiameterCm", update, enabled = !isWorking, error = draft.errors["outerDiameterCm"]) { draft.copy(outerDiameterCm = it) }
        if (isLogHive) {
            Field("Материал колоды", draft.material, "material", update, enabled = !isWorking, error = draft.errors["material"]) { draft.copy(material = it) }
            Field("Внутренний диаметр, см", draft.internalDiameterCm, "internalDiameterCm", update, enabled = !isWorking, error = draft.errors["internalDiameterCm"]) { draft.copy(internalDiameterCm = it) }
            Field("Высота внутреннего объёма, см", draft.internalHeightCm, "internalHeightCm", update, enabled = !isWorking, error = draft.errors["internalHeightCm"]) { draft.copy(internalHeightCm = it) }
        } else {
            Field("Внутренний диаметр, см (необязательно)", draft.internalDiameterCm, "internalDiameterCm", update, enabled = !isWorking, error = draft.errors["internalDiameterCm"]) { draft.copy(internalDiameterCm = it) }
        }
        Field("Дополнительно", draft.notes, "notes", update, singleLine = false, enabled = !isWorking) { draft.copy(notes = it) }
        HorizontalDivider()
        PhysicalObjectMediaSection(
            media, onPickMedia, onTakePhoto, onRemoveMedia,
            enabled = !isWorking, showActions = showMediaActions,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !isWorking, modifier = Modifier.weight(1f).testTag("physical-object-cancel")) { Text("Отмена") }
            Button(onClick = {
                val result = if (isLogHive) draft.validateLogHive() else draft.validateHollow()
                draft = draft.copy(errors = result.errors)
                if (result.isValid) onSubmit(result, media)
            }, enabled = !isWorking, modifier = Modifier.weight(1f).testTag("physical-object-create")) {
                Text(if (isWorking) "Сохранение…" else submitLabel)
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, key: String, update: (PhysicalObjectFormState) -> Unit,
    singleLine: Boolean = true, enabled: Boolean = true, error: String? = null,
    transform: (String) -> PhysicalObjectFormState,
) {
    OutlinedTextField(
        value = value, onValueChange = { update(transform(it)) }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag("physical-object-field-$key"),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
private fun HeadingField(
    heading: HeadingState, state: PhysicalObjectFormState, update: (PhysicalObjectFormState) -> Unit,
    enabled: Boolean,
) {
    val live = (heading as? HeadingState.Available)?.trueHeadingDeg
    val accuracy = (heading as? HeadingState.Available)?.accuracy
    val display = live?.let { "$it° · ${azimuthSector(it)}" } ?: "Нет текущего азимута"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Направление летка", style = MaterialTheme.typography.titleMedium)
        Text("Направьте верх телефона в сторону летка")
        Text(display, modifier = Modifier.testTag("physical-object-live-azimuth"))
        (heading as? HeadingState.Unavailable)?.let { Text(it.message) }
        state.fixedAzimuthDeg?.let { Text("Зафиксировано: $it° · ${azimuthSector(it)}") }
        if (accuracy == HeadingAccuracy.LOW || accuracy == HeadingAccuracy.UNRELIABLE) {
            Text("Точность компаса низкая", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (state.fixedAzimuthDeg != null) {
                        update(state.copy(fixedAzimuthDeg = null))
                    } else if (live != null) {
                        update(state.copy(azimuthDeg = live.toString(), fixedAzimuthDeg = live))
                    }
                },
                enabled = enabled && (state.fixedAzimuthDeg != null || live != null),
                modifier = Modifier.testTag("physical-object-fix-azimuth"),
            ) { Text(if (state.fixedAzimuthDeg == null) "Зафиксировать" else "Измерить снова") }
            OutlinedTextField(
                value = state.azimuthDeg,
                onValueChange = { update(state.copy(azimuthDeg = it, fixedAzimuthDeg = null)) },
                label = { Text("Азимут вручную, °") },
                modifier = Modifier.weight(1f).testTag("physical-object-manual-azimuth"),
                singleLine = true,
                enabled = enabled,
                isError = state.errors["azimuthDeg"] != null,
                supportingText = state.errors["azimuthDeg"]?.let { { Text(it) } },
            )
        }
    }
}

private fun azimuthSector(deg: Int): String = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[(deg + 22) / 45 % 8]

@Composable
private fun PhysicalObjectMediaSection(
    media: List<PhysicalObjectMediaDraft>, onPick: () -> Unit, onCamera: () -> Unit,
    onRemove: (UUID) -> Unit, enabled: Boolean, showActions: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("physical-object-media")) {
        Text("Медиа", style = MaterialTheme.typography.titleMedium)
        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCamera, enabled = enabled) { Text("Сделать фото") }
                OutlinedButton(onClick = onPick, enabled = enabled) { Text("Выбрать файл") }
            }
        }
        media.forEach { item -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (item.isVideo) "Видео: ${item.label}" else "Фото: ${item.label}")
            if (showActions) OutlinedButton(onClick = { onRemove(item.id) }, enabled = enabled) { Text("Удалить") }
        } }
    }
}
