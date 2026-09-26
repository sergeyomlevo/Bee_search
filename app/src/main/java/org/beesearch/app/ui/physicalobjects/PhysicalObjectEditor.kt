@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import java.io.File

data class PhysicalObjectMediaDraft(
    val id: UUID = UUID.randomUUID(),
    val label: String,
    val isVideo: Boolean,
    val previewFile: File? = null,
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
    showHeader: Boolean = true,
    isWorking: Boolean = false,
    message: String? = null,
    onSubmit: (HollowProperties, List<PhysicalObjectMediaDraft>) -> Unit,
    onCancel: () -> Unit = {},
) = PhysicalObjectForm(
    title = title, submitLabel = submitLabel, state = initial, isLogHive = false,
    headingProvider = headingProvider, headingReference = headingReference,
    media = media, onStateChange = onStateChange, onPickMedia = onPickMedia,
    onTakePhoto = onTakePhoto, onRemoveMedia = onRemoveMedia,
    showMediaActions = showMediaActions, showHeader = showHeader,
    isWorking = isWorking, message = message,
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
    showHeader: Boolean = true,
    isWorking: Boolean = false,
    message: String? = null,
    onSubmit: (LogHiveProperties, List<PhysicalObjectMediaDraft>) -> Unit,
    onCancel: () -> Unit = {},
) = PhysicalObjectForm(
    title = title, submitLabel = submitLabel, state = initial, isLogHive = true,
    headingProvider = headingProvider, headingReference = headingReference,
    media = media, onStateChange = onStateChange, onPickMedia = onPickMedia,
    onTakePhoto = onTakePhoto, onRemoveMedia = onRemoveMedia,
    showMediaActions = showMediaActions, showHeader = showHeader,
    isWorking = isWorking, message = message,
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
    showHeader: Boolean,
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
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onCancel,
                    enabled = !isWorking,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Назад" },
                ) { Text("←", style = MaterialTheme.typography.headlineSmall) }
                Column {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text("Создание объекта", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Field("Дерево", draft.tree, "tree", update, error = draft.errors["tree"], enabled = !isWorking) { draft.copy(tree = it) }
        HeadingField(heading, draft, update, enabled = !isWorking)
        Field("Высота летка над землёй, см", draft.entranceHeightCm, "entranceHeightCm", update, enabled = !isWorking, numeric = true, error = draft.errors["entranceHeightCm"]) { draft.copy(entranceHeightCm = it) }
        AdaptiveFieldPair(
            first = {
                Field("Наружный диаметр, см", draft.outerDiameterCm, "outerDiameterCm", update, enabled = !isWorking, numeric = true, error = draft.errors["outerDiameterCm"]) { draft.copy(outerDiameterCm = it) }
            },
            second = {
                Field(
                    if (isLogHive) "Внутренний диаметр, см" else "Внутренний диаметр, см (необязательно)",
                    draft.internalDiameterCm,
                    "internalDiameterCm",
                    update,
                    enabled = !isWorking,
                    numeric = true,
                    error = draft.errors["internalDiameterCm"],
                ) { draft.copy(internalDiameterCm = it) }
            },
        )
        if (isLogHive) {
            Field("Материал колоды", draft.material, "material", update, enabled = !isWorking, error = draft.errors["material"]) { draft.copy(material = it) }
            Field("Высота внутреннего объёма, см", draft.internalHeightCm, "internalHeightCm", update, enabled = !isWorking, numeric = true, error = draft.errors["internalHeightCm"]) { draft.copy(internalHeightCm = it) }
        }
        Field("Дополнительно", draft.notes, "notes", update, singleLine = false, enabled = !isWorking) { draft.copy(notes = it) }
        HorizontalDivider()
        PhysicalObjectMediaSection(
            media, onPickMedia, onTakePhoto, onRemoveMedia,
            enabled = !isWorking, showActions = showMediaActions,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AdaptiveActions(
            cancel = {
                OutlinedButton(onClick = onCancel, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("physical-object-cancel")) { Text("Отмена") }
            },
            submit = {
                Button(onClick = {
                val result = if (isLogHive) draft.validateLogHive() else draft.validateHollow()
                draft = draft.copy(errors = result.errors)
                if (result.isValid) onSubmit(result, media)
                }, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("physical-object-create")) {
                Text(if (isWorking) "Сохранение…" else submitLabel)
                }
            },
        )
    }
}

@Composable
private fun Field(
    label: String, value: String, key: String, update: (PhysicalObjectFormState) -> Unit,
    singleLine: Boolean = true, enabled: Boolean = true, numeric: Boolean = false, error: String? = null,
    transform: (String) -> PhysicalObjectFormState,
) {
    OutlinedTextField(
        value = value, onValueChange = { update(transform(it)) }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag("physical-object-field-$key"),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
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
        Text("Направьте верх телефона в сторону летка", style = MaterialTheme.typography.bodySmall)
        (heading as? HeadingState.Unavailable)?.let { Text(it.message) }
        if (accuracy == HeadingAccuracy.LOW || accuracy == HeadingAccuracy.UNRELIABLE) {
            Text("Точность компаса низкая", color = MaterialTheme.colorScheme.error)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
            val azimuthField: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = state.azimuthDeg,
                        onValueChange = { update(state.copy(azimuthDeg = it, fixedAzimuthDeg = null)) },
                        label = { Text("Азимут, °") },
                        modifier = Modifier.fillMaxWidth().testTag("physical-object-manual-azimuth"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = enabled,
                        isError = state.errors["azimuthDeg"] != null,
                        supportingText = state.errors["azimuthDeg"]?.let { { Text(it) } },
                    )
                    state.azimuthDeg.toIntOrNull()?.takeIf { it in 0..359 }?.let { value ->
                        Text(
                            azimuthSector(value),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("physical-object-entered-azimuth-sector"),
                        )
                    }
                }
            }
            val compassAction: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Текущее: $display", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("physical-object-live-azimuth"))
                    Button(
                        onClick = {
                            if (live != null) update(state.copy(azimuthDeg = live.toString(), fixedAzimuthDeg = live))
                        },
                        enabled = enabled && live != null,
                        modifier = Modifier.fillMaxWidth().testTag("physical-object-fix-azimuth"),
                    ) { Text("Зафиксировать с компаса") }
                }
            }
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    azimuthField(Modifier.fillMaxWidth())
                    compassAction(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    azimuthField(Modifier.weight(0.8f))
                    compassAction(Modifier.weight(1.2f))
                }
            }
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
            AdaptiveActions(
                cancel = { OutlinedButton(onClick = onCamera, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Сделать фото") } },
                submit = { OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Выбрать из галереи") } },
            )
        }
        if (media.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("physical-object-media-thumbnails")) {
                items(media, key = { it.id }) { item ->
                    PhysicalObjectMediaThumbnail(item, enabled, showActions, onRemove)
                }
            }
        }
    }
}

@Composable
private fun PhysicalObjectMediaThumbnail(
    item: PhysicalObjectMediaDraft,
    enabled: Boolean,
    showRemove: Boolean,
    onRemove: (UUID) -> Unit,
) {
    Box(Modifier.size(112.dp).testTag("physical-object-media-${item.id}")) {
        PhysicalObjectMediaPreview(
            file = item.previewFile,
            isVideo = item.isVideo,
            contentDescription = item.label,
            modifier = Modifier.fillMaxSize(),
            testTag = "physical-object-media-preview-${item.id}",
        )
        if (showRemove) {
            IconButton(
                onClick = { onRemove(item.id) }, enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                    .semantics { contentDescription = "Удалить медиа ${item.label}" }
                    .testTag("physical-object-remove-media-${item.id}"),
            ) {
                Box(
                    Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun AdaptiveFieldPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun AdaptiveActions(
    cancel: @Composable () -> Unit,
    submit: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cancel()
                submit()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { cancel() }
                Box(Modifier.weight(1f)) { submit() }
            }
        }
    }
}
