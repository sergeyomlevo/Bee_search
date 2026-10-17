package org.beesearch.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val GpxSuccess = Color(0xFF2B7D5B)
private val GpxWarningBackground = Color(0xFFFFF3D6)
private val GpxWarningText = Color(0xFF6B4E00)
private val GpxInset = 16.dp

/**
 * Import task state inside the same sheet: progress, result, failure and cancellation.
 *
 * The duplicate dialog is composed by the caller so it can sit above this progress surface.
 */
@Composable
internal fun GpxImportTask(
    state: GpxImportUiState,
    onCancel: () -> Unit,
    onRetryCheck: () -> Unit,
    onChooseFile: () -> Unit,
    onImportAnother: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GpxInset, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            GpxImportUiState.Idle -> Unit
            is GpxImportUiState.Importing -> GpxProgressContent(
                title = "Импорт GPX…",
                fileName = state.fileName,
                territoryName = state.territoryName,
                phase = "Сохраняем GPX…",
                showCancel = true,
                onCancel = onCancel,
                tag = "gpx-import-progress",
            )
            is GpxImportUiState.Checking -> GpxProgressContent(
                title = "Проверяем результат…",
                fileName = state.fileName,
                territoryName = state.territoryName,
                phase = null,
                showCancel = false,
                onCancel = onCancel,
                tag = "gpx-import-check",
            )
            is GpxImportUiState.Duplicate -> GpxProgressContent(
                title = "Импорт GPX…",
                fileName = state.fileName,
                territoryName = state.territoryName,
                phase = "Проверяем данные…",
                showCancel = false,
                onCancel = onCancel,
                tag = "gpx-import-duplicate-progress",
            )
            is GpxImportUiState.Success -> GpxStatusContent(
                tag = "gpx-import-success",
                iconColor = GpxSuccess,
                glyph = "✓",
                title = "GPX-файл импортирован",
                titleColor = GpxSuccess,
                fileName = state.fileName,
                target = "Сохранён в территории «${state.territoryName}».",
                muted = "На карте данные пока не отображаются.",
                warning = if (state.hasWarnings) {
                    "Некоторые элементы GPX не поддерживаются и не были перенесены. Файл сохранён без изменений."
                } else null,
                actions = {
                    GpxActions(
                        primaryLabel = "Готово",
                        onPrimary = onDone,
                        primaryTag = "gpx-import-done",
                        secondaryLabel = "Импортировать ещё",
                        onSecondary = onImportAnother,
                        secondaryTag = "gpx-import-another",
                    )
                },
            )
            is GpxImportUiState.Failure -> {
                val copy = failureCopy(state.reason)
                GpxStatusContent(
                    tag = "gpx-import-error",
                    iconColor = MaterialTheme.colorScheme.error,
                    glyph = "!",
                    title = copy.title,
                    titleColor = MaterialTheme.colorScheme.error,
                    fileName = state.fileName,
                    target = null,
                    muted = copy.body,
                    warning = null,
                    actions = {
                        GpxActions(
                            primaryLabel = copy.action,
                            onPrimary = onChooseFile,
                            primaryTag = "gpx-import-choose-file",
                            secondaryLabel = "Закрыть",
                            onSecondary = onDone,
                            secondaryTag = "gpx-import-close",
                        )
                    },
                )
            }
            is GpxImportUiState.Cancelled -> GpxStatusContent(
                tag = "gpx-import-cancelled",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                glyph = "—",
                title = "Импорт отменён",
                titleColor = MaterialTheme.colorScheme.onSurface,
                fileName = state.fileName,
                target = null,
                muted = "Файл не был добавлен.",
                warning = null,
                actions = { GpxDoneAction(onDone) },
            )
            is GpxImportUiState.DuplicateDeclined -> GpxStatusContent(
                tag = "gpx-import-duplicate-declined",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                glyph = "—",
                title = "Импорт не выполнен",
                titleColor = MaterialTheme.colorScheme.onSurface,
                fileName = state.fileName,
                target = null,
                muted = "Файл уже есть в территории «${state.territoryName}». Новый файл не добавлен.",
                warning = null,
                actions = { GpxDoneAction(onDone) },
            )
            is GpxImportUiState.ContextChanged -> GpxStatusContent(
                tag = "gpx-import-context-changed",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                glyph = "—",
                title = "Импорт отменён",
                titleColor = MaterialTheme.colorScheme.onSurface,
                fileName = state.fileName,
                target = null,
                muted = "Территория изменилась. Начните импорт снова.",
                warning = null,
                actions = { GpxDoneAction(onDone) },
            )
            is GpxImportUiState.CheckFailed -> GpxStatusContent(
                tag = "gpx-import-check-failed",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                glyph = "—",
                title = "Не удалось проверить результат",
                titleColor = MaterialTheme.colorScheme.onSurface,
                fileName = state.fileName,
                target = null,
                muted = "Повторите проверку.",
                warning = null,
                actions = {
                    GpxActions(
                        primaryLabel = "Проверить снова",
                        onPrimary = onRetryCheck,
                        primaryTag = "gpx-import-retry-check",
                        secondaryLabel = "Закрыть",
                        onSecondary = onDone,
                        secondaryTag = "gpx-import-close",
                    )
                },
            )
        }
    }
}

@Composable
private fun GpxProgressContent(
    title: String,
    fileName: String,
    territoryName: String,
    phase: String?,
    showCancel: Boolean,
    onCancel: () -> Unit,
    tag: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 4.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        GpxFileAndTarget(fileName = fileName, territoryName = territoryName)
        if (phase != null) {
            Text(
                text = phase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (showCancel) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    .heightIn(min = 48.dp).testTag("gpx-import-cancel"),
            ) { Text("Отмена") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GpxStatusContent(
    tag: String,
    iconColor: Color,
    glyph: String,
    title: String,
    titleColor: Color,
    fileName: String,
    target: String?,
    muted: String,
    warning: String?,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(52.dp).background(iconColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, color = Color.White, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (fileName.isNotBlank()) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (target != null) {
            Text(
                text = target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (muted.isNotBlank()) {
            Text(
                text = muted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (warning != null) {
            Surface(
                color = GpxWarningBackground,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = GpxWarningText,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        actions()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GpxFileAndTarget(fileName: String, territoryName: String) {
    if (fileName.isNotBlank()) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (territoryName.isNotBlank()) {
        Text(
            text = "В территорию «$territoryName»",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun GpxActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryTag: String,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    secondaryTag: String,
) {
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    if (large) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(secondaryTag),
            ) { Text(secondaryLabel) }
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(primaryTag),
            ) { Text(primaryLabel) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.heightIn(min = 48.dp).testTag(secondaryTag),
            ) { Text(secondaryLabel) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onPrimary,
                modifier = Modifier.heightIn(min = 48.dp).testTag(primaryTag),
            ) { Text(primaryLabel) }
        }
    }
}

@Composable
private fun GpxDoneAction(onDone: () -> Unit) {
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("gpx-import-done"),
    ) { Text("Готово") }
}

private data class FailureCopy(val title: String, val body: String, val action: String)

private fun failureCopy(reason: GpxImportFailure): FailureCopy = when (reason) {
    GpxImportFailure.INVALID_GPX -> FailureCopy(
        "Не удалось обработать GPX-файл",
        "Файл повреждён или имеет неподдерживаемый формат.",
        "Выбрать другой файл",
    )
    GpxImportFailure.LIMIT -> FailureCopy(
        "Файл слишком большой",
        "GPX-файл превышает допустимый размер или количество данных.",
        "Выбрать другой файл",
    )
    GpxImportFailure.READ -> FailureCopy(
        "Не удалось прочитать файл",
        "Файл недоступен или повреждён. Выберите его снова.",
        "Выбрать другой файл",
    )
    GpxImportFailure.NO_SPACE -> FailureCopy(
        "Недостаточно места",
        "Освободите место на телефоне и выберите файл снова.",
        "Выбрать файл",
    )
}

/** Duplicate confirmation. Back/outside tap is a refusal; silence is never consent. */
@Composable
internal fun GpxDuplicateDialog(
    territoryName: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Этот GPX-файл уже импортирован") },
        text = {
            Text(
                "Файл с таким же содержимым уже есть в территории «$territoryName». " +
                    "Импортировать ещё раз?",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("gpx-duplicate-confirm"),
            ) { Text("Импортировать ещё раз") }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("gpx-duplicate-decline"),
            ) { Text("Не импортировать") }
        },
        modifier = Modifier.testTag("gpx-duplicate-dialog"),
    )
}
