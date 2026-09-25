package org.beesearch.app.ui.physicalobjects

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import org.beesearch.app.domain.model.TerritoryPhysicalObjects
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

private sealed interface PhysicalObjectsBrowserState {
    data object Loading : PhysicalObjectsBrowserState
    data class Ready(val value: TerritoryPhysicalObjects) : PhysicalObjectsBrowserState
    data class Failed(val message: String) : PhysicalObjectsBrowserState
}

@Composable
internal fun PhysicalObjectsBrowserRoute(
    territoryId: UUID?,
    repository: PhysicalObjectRepository,
    onOpen: (UUID) -> Unit,
) {
    if (territoryId == null) {
        Text("Сначала выберите текущую территорию")
        return
    }
    val state by produceState<PhysicalObjectsBrowserState>(
        initialValue = PhysicalObjectsBrowserState.Loading,
        territoryId,
        repository,
    ) {
        value = runCatching { repository.listForTerritory(territoryId) }
            .fold(
                onSuccess = PhysicalObjectsBrowserState::Ready,
                onFailure = { PhysicalObjectsBrowserState.Failed(it.message ?: "Не удалось загрузить объекты") },
            )
    }
    when (val current = state) {
        PhysicalObjectsBrowserState.Loading -> Text("Загрузка объектов…")
        is PhysicalObjectsBrowserState.Failed -> Text(current.message)
        is PhysicalObjectsBrowserState.Ready -> PhysicalObjectsBrowser(
            hollows = current.value.hollows,
            logHives = current.value.logHives,
            onOpen = onOpen,
        )
    }
}
