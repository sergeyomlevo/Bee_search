@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.area

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.AreaNameDialog
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.DeleteAreaDialog
import org.beesearch.app.ui.map.EDIT_AREA_SECTIONS_LABEL
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.RENAME_AREA_LABEL
import org.beesearch.app.ui.map.normalizedAreaName

internal const val AREA_SCREEN_TAG = "area-screen"
internal const val AREA_NOT_CREATED_TAG = "area-not-created"
internal const val AREA_CARD_TAG = "area-card"
internal const val AREA_CORRUPT_TAG = "area-corrupt"
internal const val CREATE_AREA_DESCRIPTION = "Создать ареал офлайн-карты"
internal const val EDIT_AREA_SECTIONS_DESCRIPTION = "Изменить участки ареала"
internal const val RENAME_AREA_DESCRIPTION = "Переименовать ареал"
internal const val DELETE_AREA_DESCRIPTION = "Удалить ареал территории"

/** The Ареал of the current Territory: one object, so this screen is a card rather than a list. */
@Composable
internal fun AreaRoute(
    territory: Territory?,
    areaStore: MapAreaStore,
    onBack: () -> Unit,
    onEditSections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var read by remember { mutableStateOf<MapAreaReadResult>(MapAreaReadResult.Absent) }
    var renameVisible by remember { mutableStateOf(false) }
    var deleteVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val territoryId = territory?.id

    LaunchedEffect(territoryId, areaStore, territory?.name) {
        read = if (territoryId == null) {
            MapAreaReadResult.Absent
        } else {
            try {
                areaStore.load(territoryId, territory.name)
            } catch (_: Exception) {
                MapAreaReadResult.Corrupt("не удалось прочитать ареал")
            }
        }
    }

    AreaScreen(
        territoryCode = territory?.code,
        read = read,
        message = message,
        onCreate = onEditSections,
        onEditSections = onEditSections,
        onRename = { renameVisible = true },
        onDelete = { deleteVisible = true },
        onBack = onBack,
        modifier = modifier,
    )

    val prepared = read as? MapAreaReadResult.Present
    val currentId = territoryId
    if (renameVisible && prepared != null && currentId != null) {
        var name by remember(prepared.area.id, prepared.area.name) { mutableStateOf(prepared.area.name) }
        var blank by remember(prepared.area.id) { mutableStateOf(false) }
        AreaNameDialog(
            name = name,
            blankName = blank,
            onNameChange = {
                name = it
                if (blank) blank = false
            },
            onConfirm = {
                val normalized = normalizedAreaName(name)
                if (normalized == null) {
                    blank = true
                    return@AreaNameDialog
                }
                renameVisible = false
                scope.launch {
                    when (val result = areaStore.rename(currentId, normalized)) {
                        is MapAreaChangeResult.Saved -> read = MapAreaReadResult.Present(result.area)
                        is MapAreaChangeResult.Refused -> message = result.reason
                        MapAreaChangeResult.Deleted -> read = MapAreaReadResult.Absent
                    }
                }
            },
            onDismiss = { renameVisible = false },
        )
    }

    if (deleteVisible && prepared != null && currentId != null) {
        DeleteAreaDialog(
            areaName = prepared.area.name,
            onConfirm = {
                deleteVisible = false
                scope.launch {
                    when (val result = areaStore.delete(currentId)) {
                        // The Ареал is gone; Territory, observations and the map package stay.
                        MapAreaChangeResult.Deleted -> read = MapAreaReadResult.Absent
                        is MapAreaChangeResult.Refused -> message = result.reason
                        is MapAreaChangeResult.Saved -> read = MapAreaReadResult.Present(result.area)
                    }
                }
            },
            onDismiss = { deleteVisible = false },
        )
    }
}

@Composable
internal fun AreaScreen(
    territoryCode: String?,
    read: MapAreaReadResult,
    message: String?,
    onCreate: () -> Unit,
    onEditSections: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ареал") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(AREA_SCREEN_TAG),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = territoryCode?.let { "Территория: $it" } ?: "Текущая территория не выбрана",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            message?.let { detail ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(detail, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                when (read) {
                    MapAreaReadResult.Absent -> AreaNotCreated(
                        territoryMissing = territoryCode == null,
                        onCreate = onCreate,
                    )

                    is MapAreaReadResult.Present -> AreaCard(
                        area = read.area,
                        onEditSections = onEditSections,
                        onRename = onRename,
                        onDelete = onDelete,
                    )

                    // A damaged value is never presented as "not created", and it is never
                    // created over, renamed or deleted from here.
                    is MapAreaReadResult.Corrupt -> AreaCorrupt()
                    is MapAreaReadResult.Legacy -> AreaCorrupt()
                }
            }
        }
    }
}

@Composable
private fun AreaNotCreated(
    territoryMissing: Boolean,
    onCreate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_NOT_CREATED_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ареал не создан", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (territoryMissing) {
                    "Сначала выберите текущую территорию в Настройках."
                } else {
                    "Участки ареала задаются на карте: добавьте один или несколько участков и сохраните ареал."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!territoryMissing) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-area"),
                ) { Text(CREATE_AREA_LABEL) }
            }
        }
    }
}

@Composable
private fun AreaCard(
    area: MapArea,
    onEditSections: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_CARD_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Название:", style = MaterialTheme.typography.labelMedium)
            Text(area.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("area-name"))
            Text(
                text = "Участков: ${area.bounds.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("area-section-count"),
            )
            Button(
                onClick = onEditSections,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit-area-sections"),
            ) { Text(EDIT_AREA_SECTIONS_LABEL) }
            TextButton(
                onClick = onRename,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename-area"),
            ) { Text(RENAME_AREA_LABEL) }
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete-area"),
            ) { Text(DELETE_AREA_LABEL) }
        }
    }
}

@Composable
private fun AreaCorrupt() {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_CORRUPT_TAG),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Данные ареала повреждены", style = MaterialTheme.typography.titleMedium)
            Text(
                "Сохранённые участки не читаются, поэтому обычное редактирование и сохранение недоступны. " +
                    "Данные не изменяются.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
