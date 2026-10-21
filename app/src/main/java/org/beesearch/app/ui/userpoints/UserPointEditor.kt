package org.beesearch.app.ui.userpoints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.beesearch.app.ui.physicalobjects.PhysicalObjectMediaDraft
import org.beesearch.app.ui.physicalobjects.PhysicalObjectMediaSection

@Composable
internal fun UserPointEditor(
    state: UserPointEditorState,
    media: List<PhysicalObjectMediaDraft>,
    titleFocusRequester: FocusRequester,
    onTitleChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onChangeLocation: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (java.util.UUID) -> Unit,
    onOpenPhoto: (java.util.UUID) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    val bringIntoViewRequester = androidx.compose.runtime.remember { BringIntoViewRequester() }
    LaunchedEffect(state.error) {
        if (state.error == "Введите название") bringIntoViewRequester.bringIntoView()
    }
    Column(
        modifier = Modifier.fillMaxSize().imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !state.isWorking,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Назад" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("←", style = MaterialTheme.typography.headlineSmall) }
            Column(Modifier.weight(1f)) {
                Text(if (state.isEditing) "Редактирование" else "Новая точка", style = MaterialTheme.typography.headlineSmall)
                if (!state.isEditing) Text("Создание объекта", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.isEditing && !state.isLoaded) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error == null) {
                    CircularProgressIndicator()
                } else {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetryLoad, enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("user-point-edit-retry")) {
                        Text("Повторить")
                    }
                }
            }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester)
                    .bringIntoViewRequester(bringIntoViewRequester).testTag("user-point-title"),
                singleLine = false,
                minLines = 1,
                enabled = !state.isWorking,
                isError = state.error == "Введите название",
                supportingText = if (state.error == "Введите название") ({ Text("Введите название") }) else null,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth().testTag("user-point-note"),
                minLines = 3,
                enabled = !state.isWorking,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Координаты", style = MaterialTheme.typography.titleMedium)
                Text(
                    String.format(java.util.Locale.ROOT, "%.6f, %.6f", state.latitude, state.longitude),
                    modifier = Modifier.testTag("user-point-coordinates"),
                )
                OutlinedButton(onClick = onChangeLocation, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                    Text("Изменить место")
                }
            }
            HorizontalDivider()
            PhysicalObjectMediaSection(
                media = media,
                onPick = onPickPhoto,
                onCamera = onTakePhoto,
                onRemove = onRemovePhoto,
                enabled = !state.isWorking,
                showActions = true,
                title = "Фотографии",
                onOpen = onOpenPhoto,
            )
            state.error?.takeIf { it != "Введите название" }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("user-point-error"))
            }
            OutlinedButton(onClick = onCancel, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().testTag("user-point-cancel")) {
                Text("Отмена")
            }
            Button(onClick = onSave, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().testTag("user-point-save")) {
                Text(if (state.isWorking) "Сохранение…" else "Сохранить")
            }
        }
    }
}
