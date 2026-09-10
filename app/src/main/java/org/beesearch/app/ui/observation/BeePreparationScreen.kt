@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import java.util.UUID
import org.beesearch.app.BeePreparationUiState

@Composable
internal fun BeePreparationScreen(
    point: ObservationPoint,
    preparation: BeePreparationUiState,
    isMutating: Boolean,
    isCompleting: Boolean,
    onAddBee: (String, MarkPosition) -> Unit,
    onRemoveBee: (UUID) -> Unit,
    onRecordNoBeesFound: () -> Unit,
    onComplete: () -> Unit,
    onStartInitialGroupRelease: () -> Unit = {},
) {
    var showCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoBeesConfirmation by rememberSaveable { mutableStateOf(false) }
    var isLaunchInstructionExpanded by rememberSaveable { mutableStateOf(false) }
    val preparedBeeListState = rememberLazyListState()
    var previouslyDisplayedBeeIds by remember { mutableStateOf<List<UUID>?>(null) }
    var shouldFocusWorkingArea by rememberSaveable { mutableStateOf(true) }
    if (showCompletionConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showCompletionConfirmation = false },
            title = { Text("Завершить наблюдение?") },
            text = {
                Text(
                    "Точка станет завершённым историческим наблюдением. " +
                        "После успешного сохранения можно будет создать следующую точку.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionConfirmation = false
                        onComplete()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-complete-observation"),
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompletionConfirmation = false },
                    enabled = !isCompleting,
                ) { Text("Отмена") }
            },
        )
    }
    if (showNoBeesConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showNoBeesConfirmation = false },
            title = { Text("Пчёлы отсутствуют?") },
            text = {
                Text(
                    "Точка наблюдения будет сохранена с результатом " +
                        "«пчёлы отсутствуют» и завершена.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoBeesConfirmation = false
                        onRecordNoBeesFound()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-no-bees"),
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoBeesConfirmation = false },
                    enabled = !isCompleting,
                ) { Text("Отмена") }
            },
        )
    }
    val stateMatchesPoint = preparation.pointId == point.id
    val bees = if (stateMatchesPoint) preparation.bees else emptyList()
    val isLoading = preparation.isLoading || !stateMatchesPoint
    val isReleaseStarted = stateMatchesPoint && preparation.isReleaseStarted
    val beePresenceResult = if (stateMatchesPoint) preparation.beePresenceResult else null
    val canRecordNoBees = !isLoading &&
        !isReleaseStarted &&
        bees.isEmpty() &&
        beePresenceResult == null
    val currentBeeIds = bees.map { it.id }
    val editorItemIndex = 1 + bees.size + (if (bees.isEmpty()) 1 else 0)
    val launchInstructionItemIndex = editorItemIndex + 2

    LaunchedEffect(currentBeeIds, isLoading, isReleaseStarted) {
        if (isLoading || isReleaseStarted) return@LaunchedEffect
        val previousIds = previouslyDisplayedBeeIds
        val addedBeeId = previousIds?.let { previous ->
            currentBeeIds.lastOrNull { it !in previous }
        }
        if (previousIds == null || addedBeeId != null) {
            shouldFocusWorkingArea = true
        }
        previouslyDisplayedBeeIds = currentBeeIds
    }

    LaunchedEffect(isLaunchInstructionExpanded, isLoading, isReleaseStarted) {
        if (!isLoading && !isReleaseStarted && !isLaunchInstructionExpanded) {
            shouldFocusWorkingArea = true
        }
    }

    LaunchedEffect(
        shouldFocusWorkingArea,
        isLaunchInstructionExpanded,
        editorItemIndex,
        launchInstructionItemIndex,
        isLoading,
        isReleaseStarted,
    ) {
        if (isLoading || isReleaseStarted) return@LaunchedEffect
        when {
            isLaunchInstructionExpanded -> {
                preparedBeeListState.animateScrollToItem(launchInstructionItemIndex)
            }
            shouldFocusWorkingArea -> {
                preparedBeeListState.animateScrollToItem(
                    index = editorItemIndex,
                    scrollOffset = -preparedBeeListState.layoutInfo.viewportSize.height / 3,
                )
                shouldFocusWorkingArea = false
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Подготовка точки",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        onClick = { showCompletionConfirmation = true },
                        enabled = !isCompleting &&
                            !isMutating &&
                            beePresenceResult != null,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(
                            text = if (isCompleting) "Завершение…" else "Завершить",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("bee-preparation-screen"),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else if (isReleaseStarted) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("bee-preparation-list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Подготовленные пчёлы", style = MaterialTheme.typography.titleSmall)
                    }
                    items(bees, key = { it.id }) { bee ->
                        PreparedBeeRow(
                            bee = bee,
                            canRemove = false,
                            onRemove = {},
                        )
                    }
                    item {
                        Text(
                            "Первый выпуск уже начат. Состав пчёл зафиксирован.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    item {
                        Button(
                            onClick = onStartInitialGroupRelease,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("initial-group-release"),
                        ) { Text("Выпустить всех") }
                    }
                }
            } else {
                LazyColumn(
                    state = preparedBeeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth()
                        .testTag("prepared-bee-list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text("Подготовленные пчёлы", style = MaterialTheme.typography.titleSmall)
                    }
                    items(bees, key = { it.id }) { bee ->
                        PreparedBeeRow(
                            bee = bee,
                            canRemove = !isMutating,
                            onRemove = { onRemoveBee(bee.id) },
                        )
                    }
                    if (bees.isEmpty()) {
                        item { Text("Пока не добавлено ни одной пчелы.") }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                            .testTag("preparation-editor"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canRecordNoBees) {
                                OutlinedButton(
                                    onClick = { showNoBeesConfirmation = true },
                                    enabled = !isCompleting && !isMutating,
                                    modifier = Modifier.fillMaxWidth().testTag("record-no-bees"),
                                ) { Text("Пчёлы отсутствуют") }
                            }

                            BeeSelector(
                                bees = bees,
                                enabled = !isMutating,
                                onAddBee = onAddBee,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = onStartInitialGroupRelease,
                            enabled = bees.isNotEmpty() &&
                                beePresenceResult == BeePresenceResult.BEES_FOUND &&
                                !isMutating &&
                                !isCompleting,
                            modifier = Modifier.fillMaxWidth().testTag("initial-group-release"),
                        ) { Text(if (isMutating) "Сохранение…" else "Выпустить всех") }
                    }
                    item {
                        PreparationLaunchInstruction(
                            isExpanded = isLaunchInstructionExpanded,
                            onExpandedChange = { isLaunchInstructionExpanded = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparationLaunchInstruction(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "ВАЖНО! Первый выпуск",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = { onExpandedChange(!isExpanded) },
                modifier = Modifier.testTag("launch-instruction-toggle"),
            ) { Text(if (isExpanded) "Свернуть" else "Подробнее") }
        }
        if (isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "После открытия клеточки пчёлы обычно вылетают почти одновременно, " +
                        "с разницей в несколько секунд. В этот момент нажмите «Выпустить всех» — " +
                        "для всех подготовленных пчёл будет зафиксировано одинаковое время вылета.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Если пчела не улетела, отмените для неё вылет и дождитесь, " +
                        "когда она действительно улетит.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PreparedBeeRow(
    bee: Bee,
    canRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarkColorSwatch(bee.markColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    BeeMarkCatalog.colorDisplayName(bee.markColor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    BeeMarkCatalog.positionDisplayName(bee.markPosition),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.testTag("remove-prepared-bee-${bee.id}"),
            ) { Text("Удалить") }
        }
    }
}
