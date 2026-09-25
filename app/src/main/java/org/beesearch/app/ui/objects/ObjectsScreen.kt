@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.objects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import org.beesearch.app.ui.physicalobjects.PhysicalObjectsBrowserRoute
import java.util.UUID

@Composable
internal fun ObjectsScreen(
    onBack: () -> Unit,
    onOpenArea: () -> Unit,
    onOpenObservationPoints: () -> Unit,
    currentTerritoryId: UUID? = null,
    physicalObjectRepository: PhysicalObjectRepository? = null,
    onOpenPhysicalObject: (UUID) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Объекты") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(AREA_OBJECT_TITLE) },
                supportingContent = { Text("Именованная область территории и её участки офлайн-карты") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenArea)
                    .testTag("objects-area"),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Точки наблюдения") },
                supportingContent = { Text("Карта, таблица и история наблюдений") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenObservationPoints)
                    .testTag("objects-observation-points"),
            )
            HorizontalDivider()
            Text(
                "Дупла и колоды",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                if (physicalObjectRepository != null) {
                    PhysicalObjectsBrowserRoute(
                        territoryId = currentTerritoryId,
                        repository = physicalObjectRepository,
                        onOpen = onOpenPhysicalObject,
                    )
                }
            }
        }
    }
}

internal const val AREA_OBJECT_TITLE = "Ареал"
