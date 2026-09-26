@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
) {
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
            )
        }
    }
}

@Composable
private fun PhysicalObjectsBrowserRoute(
    territoryId: UUID?,
    type: PhysicalObjectType,
    repository: PhysicalObjectRepository,
    onOpen: (UUID) -> Unit,
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
