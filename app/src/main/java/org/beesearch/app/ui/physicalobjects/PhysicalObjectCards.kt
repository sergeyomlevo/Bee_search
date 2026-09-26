@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.PhysicalObjectMedia
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale

sealed interface PhysicalObjectListItem {
    val id: java.util.UUID
    val designation: String
    val typeLabel: String
}

private data class HollowItem(val value: Hollow) : PhysicalObjectListItem {
    override val id get() = value.id
    override val designation get() = value.designation
    override val typeLabel = "Дупло"
}

private data class LogHiveItem(val value: LogHive) : PhysicalObjectListItem {
    override val id get() = value.id
    override val designation get() = value.designation
    override val typeLabel = "Колода"
}

@Composable
fun PhysicalObjectsBrowser(
    hollows: List<Hollow>, logHives: List<LogHive>, onOpen: (java.util.UUID) -> Unit,
) {
    val items = hollows.map(::HollowItem) + logHives.map(::LogHiveItem)
    if (items.isEmpty()) {
        Text("В этой территории пока нет Дупел и Колод.", modifier = Modifier.padding(16.dp).testTag("physical-objects-empty"))
        return
    }
    LazyColumn(modifier = Modifier.testTag("physical-objects-browser")) {
        items(items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.designation) },
                supportingContent = { Text(item.typeLabel) },
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) }.testTag("physical-object-${item.id}"),
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun HollowCard(
    value: Hollow, territoryLabel: String, creatorLabel: String, onEdit: () -> Unit = {},
    onEditCoordinates: () -> Unit = {}, onShowOnMap: () -> Unit = {},
    mediaFile: (PhysicalObjectMedia) -> File? = { null },
    onOpenMedia: (PhysicalObjectMedia) -> Unit = {},
) = PhysicalObjectCard(
    typeLabel = "Дупло", territoryLabel = territoryLabel,
    creatorLabel = creatorLabel, createdAt = value.createdAt.displayDateTime(), latitude = value.latitude,
    longitude = value.longitude, properties = value.properties?.let {
            listOf("Дерево" to it.tree, "Высота летка" to "${it.entranceHeightCm.displayMeasurement()} см", "Азимут" to "${it.entranceAzimuthDeg}° · ${azimuthSector(it.entranceAzimuthDeg)}", "Наружный диаметр" to "${it.outerDiameterCm.displayMeasurement()} см") +
            listOfNotNull(it.internalDiameterCm?.let { d -> "Внутренний диаметр" to "${d.displayMeasurement()} см" }, it.notes?.let { n -> "Дополнительно" to n })
    } ?: emptyList(), media = value.media, onEdit = onEdit,
    onEditCoordinates = onEditCoordinates, onShowOnMap = onShowOnMap,
    mediaFile = mediaFile, onOpenMedia = onOpenMedia,
)

@Composable
fun LogHiveCard(
    value: LogHive, territoryLabel: String, creatorLabel: String, onEdit: () -> Unit = {},
    onEditCoordinates: () -> Unit = {}, onShowOnMap: () -> Unit = {},
    mediaFile: (PhysicalObjectMedia) -> File? = { null },
    onOpenMedia: (PhysicalObjectMedia) -> Unit = {},
) = PhysicalObjectCard(
    typeLabel = "Колода", territoryLabel = territoryLabel,
    creatorLabel = creatorLabel, createdAt = value.createdAt.displayDateTime(), latitude = value.latitude,
    longitude = value.longitude, properties = value.properties?.let {
            listOf("Дерево" to it.tree, "Высота летка" to "${it.entranceHeightCm.displayMeasurement()} см", "Азимут" to "${it.entranceAzimuthDeg}° · ${azimuthSector(it.entranceAzimuthDeg)}", "Наружный диаметр" to "${it.outerDiameterCm.displayMeasurement()} см", "Материал" to it.material, "Внутренний диаметр" to "${it.internalDiameterCm.displayMeasurement()} см", "Высота внутреннего объёма" to "${it.internalHeightCm.displayMeasurement()} см") + listOfNotNull(it.notes?.let { n -> "Дополнительно" to n })
    } ?: emptyList(), media = value.media, onEdit = onEdit,
    onEditCoordinates = onEditCoordinates, onShowOnMap = onShowOnMap,
    mediaFile = mediaFile, onOpenMedia = onOpenMedia,
)

@Composable
private fun PhysicalObjectCard(
    typeLabel: String, territoryLabel: String, creatorLabel: String,
    createdAt: String, latitude: Double, longitude: Double, properties: List<Pair<String, String>>,
    media: List<PhysicalObjectMedia>, onEdit: () -> Unit,
    onEditCoordinates: () -> Unit, onShowOnMap: () -> Unit,
    mediaFile: (PhysicalObjectMedia) -> File?, onOpenMedia: (PhysicalObjectMedia) -> Unit,
) {
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()).testTag("physical-object-detail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Просмотр объекта · $typeLabel", style = MaterialTheme.typography.titleMedium)
        if (media.isNotEmpty()) {
            var selectedId by remember(media) { mutableStateOf(media.first().id) }
            val selected = media.firstOrNull { it.id == selectedId } ?: media.first()
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.8f).clickable { onOpenMedia(selected) }
                    .testTag("physical-object-detail-media-hero"),
            ) {
                PhysicalObjectMediaPreview(
                    file = mediaFile(selected),
                    isVideo = selected.type == org.beesearch.app.domain.model.PhysicalObjectMediaType.VIDEO,
                    contentDescription = selected.originalFileName ?: "Медиа объекта",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (media.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("physical-object-detail-media"),
                ) {
                    items(media, key = { it.id }) { item ->
                        Box(
                            Modifier.size(76.dp)
                                .clickable { selectedId = item.id }
                                .testTag("physical-object-detail-media-${item.id}"),
                        ) {
                            PhysicalObjectMediaPreview(
                                file = mediaFile(item),
                                isVideo = item.type == org.beesearch.app.domain.model.PhysicalObjectMediaType.VIDEO,
                                contentDescription = item.originalFileName ?: "Медиа объекта",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                properties.forEachIndexed { index, (label, value) ->
                    PropertyRow(label, value)
                    if (index != properties.lastIndex) HorizontalDivider()
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Координаты: ${"%.6f".format(Locale.ROOT, latitude)}, ${"%.6f".format(Locale.ROOT, longitude)}",
                    modifier = Modifier.testTag("physical-object-coordinates"),
                )
                AdaptiveDetailActions(
                    first = {
                        OutlinedButton(onClick = onShowOnMap, modifier = Modifier.fillMaxWidth().testTag("physical-object-show-map")) { Text("Показать на карте") }
                    },
                    second = {
                        OutlinedButton(onClick = onEditCoordinates, modifier = Modifier.fillMaxWidth().testTag("physical-object-edit-coordinates")) { Text("Изменить место") }
                    },
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow("Территория", territoryLabel)
                HorizontalDivider()
                PropertyRow("Создал", creatorLabel)
                HorizontalDivider()
                PropertyRow("Создано", createdAt)
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("physical-object-edit")) { Text("Редактировать характеристики") }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text("$label:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.9f))
                Text(value, modifier = Modifier.weight(1.1f))
            }
        }
    }
}

@Composable
private fun AdaptiveDetailActions(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

private fun java.time.Instant.displayDateTime(): String = atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

private fun azimuthSector(deg: Int): String = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[(deg + 22) / 45 % 8]

private fun Double.displayMeasurement(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
