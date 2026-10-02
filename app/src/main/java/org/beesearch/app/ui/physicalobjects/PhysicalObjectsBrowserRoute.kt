@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.exchange.ExchangeFolder
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
    exchangeStorage: BeeSearchExchangeStorage,
    collectionExportFileName: String,
    onExportCollection: (Uri) -> Unit,
    onEmptyCollection: (PhysicalObjectType) -> Unit,
    onOpen: (UUID) -> Unit,
    onBack: () -> Unit,
    onResetSequence: ((UUID, PhysicalObjectType) -> Unit)? = null,
) {
    var confirmReset by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val state by produceState<PhysicalObjectsBrowserState>(
        initialValue = PhysicalObjectsBrowserState.Loading,
        territoryId,
        repository,
        type,
    ) {
        value = if (territoryId == null) {
            PhysicalObjectsBrowserState.Failed("Сначала выберите текущую территорию")
        } else {
            runCatching { repository.listForTerritory(territoryId) }
                .fold(
                    onSuccess = PhysicalObjectsBrowserState::Ready,
                    onFailure = { PhysicalObjectsBrowserState.Failed(it.message ?: "Не удалось загрузить объекты") },
                )
        }
    }
    val createExportDocument = rememberLauncherForActivityResult(
        CreateExchangeDocument(
            mimeType = "application/zip",
            initialFolder = exchangeStorage.initialDocumentUri(ExchangeFolder.DATA),
        ),
    ) { destination: Uri? ->
        destination?.let(onExportCollection)
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.listTitle()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    if (territoryId != null && type.supportsCollectionExport()) {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Действия со списком" }
                                .testTag("physical-objects-menu"),
                        ) { Text("⋮") }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(type.collectionExportActionLabel()) },
                                enabled = state is PhysicalObjectsBrowserState.Ready,
                                modifier = Modifier.testTag("physical-objects-export-all"),
                                onClick = {
                                    menuExpanded = false
                                    (state as? PhysicalObjectsBrowserState.Ready)?.let { ready ->
                                        if (ready.value.isEmpty(type)) {
                                            onEmptyCollection(type)
                                        } else {
                                            createExportDocument.launch(collectionExportFileName)
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PhysicalObjectsBrowserContent(
                state = state,
                type = type,
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
private fun PhysicalObjectsBrowserContent(
    state: PhysicalObjectsBrowserState,
    type: PhysicalObjectType,
    onOpen: (UUID) -> Unit,
    onRequestReset: (() -> Unit)? = null,
) {
    when (val current = state) {
        PhysicalObjectsBrowserState.Loading -> Text("Загрузка объектов…", modifier = Modifier.padding(16.dp))
        is PhysicalObjectsBrowserState.Failed -> Text(
            current.message,
            modifier = Modifier.padding(16.dp).then(
                if (current.message == "Сначала выберите текущую территорию") {
                    Modifier.testTag("physical-objects-no-territory")
                } else {
                    Modifier
                },
            ),
        )
        is PhysicalObjectsBrowserState.Ready -> PhysicalObjectsBrowser(
            type = type,
            hollows = current.value.hollows,
            logHives = current.value.logHives,
            onOpen = onOpen,
            onResetSequence = onRequestReset,
        )
    }
}

private fun PhysicalObjectType.supportsCollectionExport(): Boolean =
    this == PhysicalObjectType.HOLLOW || this == PhysicalObjectType.LOG_HIVE

internal fun PhysicalObjectType.collectionExportActionLabel(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "Экспортировать все дупла"
    PhysicalObjectType.LOG_HIVE -> "Экспортировать все колоды"
    PhysicalObjectType.APIARY -> error("Экспорт коллекции пасек не поддерживается")
}

private fun TerritoryPhysicalObjects.count(type: PhysicalObjectType): Int = when (type) {
    PhysicalObjectType.HOLLOW -> hollows.size
    PhysicalObjectType.LOG_HIVE -> logHives.size
    PhysicalObjectType.APIARY -> apiaries.size
}

private fun TerritoryPhysicalObjects.isEmpty(type: PhysicalObjectType): Boolean = count(type) == 0

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
