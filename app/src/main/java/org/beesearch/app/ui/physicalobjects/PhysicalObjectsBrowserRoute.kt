@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.TerritoryPhysicalObjects
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

private sealed interface PhysicalObjectsBrowserState {
    data object Loading : PhysicalObjectsBrowserState
    data class Ready(val value: TerritoryPhysicalObjects) : PhysicalObjectsBrowserState
    data class Failed(val message: String) : PhysicalObjectsBrowserState
}

/**
 * The `Дупла` or `Колоды` list.
 *
 * The category owns the list, the list owns the card: Back from a card returns to this list and Back
 * from the list returns to `Объекты`.
 */
@Composable
internal fun PhysicalObjectListRoute(
    type: PhysicalObjectType,
    territoryId: UUID?,
    repository: PhysicalObjectRepository,
    onOpen: (UUID) -> Unit,
    onBack: () -> Unit,
    onResetSequence: ((UUID, PhysicalObjectType) -> Unit)? = null,
) {
    var confirmReset by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.listTitle()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PhysicalObjectsBrowserRoute(
                territoryId = territoryId,
                type = type,
                repository = repository,
                onOpen = onOpen,
                onRequestReset = if (canResetSequence(type, territoryId, onResetSequence)) {
                    { confirmReset = true }
                } else {
                    null
                },
            )
        }
    }
    val scope = territoryId
    if (confirmReset && scope != null && onResetSequence != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(type.resetConfirmationTitle()) },
            text = { Text(type.resetConfirmationBody()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetSequence(scope, type)
                    },
                    modifier = Modifier.testTag("physical-objects-reset-confirm"),
                ) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReset = false },
                    modifier = Modifier.testTag("physical-objects-reset-cancel"),
                ) { Text("Отмена") }
            },
        )
    }
}

/**
 * Whether the empty-state reset action may be offered at all.
 *
 * This is only the convenience half of the rule: the visible list being empty is what the user sees,
 * while the repository re-checks every precondition in its own transaction and refuses to write
 * anything when the scope stopped being safe. Apiary has no numbering reset UI.
 */
private fun canResetSequence(
    type: PhysicalObjectType,
    territoryId: UUID?,
    onResetSequence: ((UUID, PhysicalObjectType) -> Unit)?,
): Boolean = onResetSequence != null && territoryId != null && type != PhysicalObjectType.APIARY

@Composable
private fun PhysicalObjectsBrowserRoute(
    territoryId: UUID?,
    type: PhysicalObjectType,
    repository: PhysicalObjectRepository,
    onOpen: (UUID) -> Unit,
    onRequestReset: (() -> Unit)? = null,
) {
    if (territoryId == null) {
        Text(
            "Сначала выберите текущую территорию",
            modifier = Modifier.padding(16.dp).testTag("physical-objects-no-territory"),
        )
        return
    }
    val state by produceState<PhysicalObjectsBrowserState>(
        initialValue = PhysicalObjectsBrowserState.Loading,
        territoryId,
        repository,
        type,
    ) {
        value = runCatching { repository.listForTerritory(territoryId) }
            .fold(
                onSuccess = PhysicalObjectsBrowserState::Ready,
                onFailure = { PhysicalObjectsBrowserState.Failed(it.message ?: "Не удалось загрузить объекты") },
            )
    }
    when (val current = state) {
        PhysicalObjectsBrowserState.Loading -> Text("Загрузка объектов…", modifier = Modifier.padding(16.dp))
        is PhysicalObjectsBrowserState.Failed -> Text(current.message, modifier = Modifier.padding(16.dp))
        is PhysicalObjectsBrowserState.Ready -> PhysicalObjectsBrowser(
            type = type,
            hollows = current.value.hollows,
            logHives = current.value.logHives,
            onOpen = onOpen,
            onResetSequence = onRequestReset,
        )
    }
}

/** The user-facing category name of one Physical Object type. */
internal fun PhysicalObjectType.listTitle(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "Дупла"
    PhysicalObjectType.LOG_HIVE -> "Колоды"
    PhysicalObjectType.APIARY -> "Пасеки"
}

internal fun PhysicalObjectType.emptyListMessage(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "В этой территории пока нет дупел."
    PhysicalObjectType.LOG_HIVE -> "В этой территории пока нет колод."
    PhysicalObjectType.APIARY -> "В этой территории пока нет пасек."
}

/** The confirmation title of the numbering reset, named by the concrete type. */
internal fun PhysicalObjectType.resetConfirmationTitle(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "Сбросить нумерацию дупел?"
    PhysicalObjectType.LOG_HIVE -> "Сбросить нумерацию колод?"
    PhysicalObjectType.APIARY -> "Сбросить нумерацию пасек?"
}

/** The confirmation body of the numbering reset: what the user gets after it. */
internal fun PhysicalObjectType.resetConfirmationBody(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "Следующее созданное дупло получит номер 1."
    PhysicalObjectType.LOG_HIVE -> "Следующая созданная колода получит номер 1."
    PhysicalObjectType.APIARY -> "Следующая созданная пасека получит номер 1."
}
