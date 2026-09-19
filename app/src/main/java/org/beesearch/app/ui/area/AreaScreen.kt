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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.beesearch.app.data.exchange.AreaExchangeMirror
import org.beesearch.app.data.exchange.AreaSendResult
import org.beesearch.app.data.exchange.AreaTransport
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.DeleteAreaDialog
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.SEND_AREA_DESCRIPTION
import org.beesearch.app.ui.map.SEND_AREA_LABEL
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_DESCRIPTION
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_LABEL
import org.beesearch.app.ui.map.areaUnionKm2
import org.beesearch.app.ui.map.formatSquareKilometers

internal const val AREA_SCREEN_TAG = "area-screen"
internal const val AREA_NOT_CREATED_TAG = "area-not-created"
internal const val AREA_CARD_TAG = "area-card"
internal const val AREA_CORRUPT_TAG = "area-corrupt"
internal const val AREA_NAME_TAG = "area-name"
internal const val AREA_SECTION_COUNT_TAG = "area-section-count"
internal const val AREA_TOTAL_AREA_TAG = "area-total-area"
internal const val CREATE_AREA_TAG = "create-area"
internal const val VIEW_AREA_ON_MAP_TAG = "view-area-on-map"
internal const val SEND_AREA_TAG = "send-area"
internal const val DELETE_AREA_TAG = "delete-area"
internal const val CREATE_AREA_DESCRIPTION = "Создать ареал офлайн-карты"
internal const val DELETE_AREA_DESCRIPTION = "Удалить ареал территории"

/** Label of the derived total area. The value itself is never stored. */
internal const val AREA_TOTAL_AREA_LABEL = "Общая площадь"
internal const val AREA_SECTION_COUNT_LABEL = "Участков"

/**
 * The Ареал of the current Territory: one object, so this screen is a card rather than a list.
 *
 * The card answers three questions only - what the Ареал is, how to look at it on the map and how to
 * send its file. Editing участки is a separate step from the view mode, so a user who only wants to
 * look at the Ареал never lands in the editor. The name is set when the Ареал is created and is
 * treated as stable afterwards; there is deliberately no separate rename action.
 */
@Composable
internal fun AreaRoute(
    territory: Territory?,
    areaStore: MapAreaStore,
    areaMirror: AreaExchangeMirror,
    areaTransport: AreaTransport,
    onCreate: () -> Unit,
    onViewOnMap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var read by remember { mutableStateOf<MapAreaReadResult>(MapAreaReadResult.Absent) }
    var deleteVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val territoryId = territory?.id
    val area = (read as? MapAreaReadResult.Present)?.area

    LaunchedEffect(territoryId, areaStore, territory?.name) {
        val loaded = if (territoryId == null) {
            MapAreaReadResult.Absent
        } else {
            try {
                areaStore.load(territoryId, territory.name)
            } catch (_: Exception) {
                MapAreaReadResult.Corrupt("не удалось прочитать ареал")
            }
        }
        read = loaded
        // Lazy backfill: an Ареал that was saved before this iteration, or whose public file the user
        // deleted, gets its managed Area file back the next time this screen is opened. The canonical
        // Ареал is only read here, and a failure to write the file changes nothing.
        (loaded as? MapAreaReadResult.Present)?.let { areaMirror.sync(it.area) }
    }

    AreaScreen(
        territoryCode = territory?.code,
        read = read,
        message = message,
        sending = sending,
        onCreate = onCreate,
        onViewOnMap = onViewOnMap,
        onSend = {
            val current = area
            if (current != null && !sending) {
                sending = true
                scope.launch {
                    val result = areaTransport.send(current)
                    sending = false
                    // A failed send never touches the Ареал; the user only needs to know.
                    if (result is AreaSendResult.Failed) message = result.message
                }
            }
        },
        onDelete = { deleteVisible = true },
        onBack = onBack,
        modifier = modifier,
    )

    val currentId = territoryId
    if (deleteVisible && area != null && currentId != null) {
        DeleteAreaDialog(
            areaName = area.name,
            onConfirm = {
                deleteVisible = false
                scope.launch {
                    when (val result = areaStore.delete(currentId)) {
                        // The Ареал is gone; Territory, observations and the map package stay. The
                        // managed Area file is removed by the store that owns the exchange mirror.
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
    sending: Boolean,
    onCreate: () -> Unit,
    onViewOnMap: () -> Unit,
    onSend: () -> Unit,
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
                        sending = sending,
                        onViewOnMap = onViewOnMap,
                        onSend = onSend,
                        onDelete = onDelete,
                    )

                    // A damaged value is never presented as "not created", and it is never
                    // created over or deleted from here.
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
                        .testTag(CREATE_AREA_TAG)
                        .semantics { contentDescription = CREATE_AREA_DESCRIPTION },
                ) { Text(CREATE_AREA_LABEL) }
            }
        }
    }
}

/**
 * What the user needs to know about a saved Ареал: its name, how many участки it has and how large
 * it is. No UUID, no raw bounding boxes and no file details - those are implementation, and the file
 * itself is handled by «Отправить ареал».
 */
@Composable
private fun AreaCard(
    area: MapArea,
    sending: Boolean,
    onViewOnMap: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_CARD_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(area.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(AREA_NAME_TAG))
            Text(
                text = "$AREA_SECTION_COUNT_LABEL: ${area.bounds.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AREA_SECTION_COUNT_TAG),
            )
            Text(
                // Overlapping участки are counted once and the gaps between them are not counted at
                // all, so this is the area of the Ареал itself rather than of its outer rectangle.
                text = "$AREA_TOTAL_AREA_LABEL: ${formatSquareKilometers(areaUnionKm2(area.bounds))} км²",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AREA_TOTAL_AREA_TAG),
            )
            Button(
                onClick = onViewOnMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VIEW_AREA_ON_MAP_TAG)
                    .semantics { contentDescription = VIEW_AREA_ON_MAP_DESCRIPTION },
            ) { Text(VIEW_AREA_ON_MAP_LABEL) }
            Button(
                onClick = onSend,
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SEND_AREA_TAG)
                    .semantics { contentDescription = SEND_AREA_DESCRIPTION },
            ) { Text(if (sending) "Отправка…" else SEND_AREA_LABEL) }
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DELETE_AREA_TAG)
                    .semantics { contentDescription = DELETE_AREA_DESCRIPTION },
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
