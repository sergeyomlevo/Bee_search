@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.physicalobjects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
) = PhysicalObjectCard(
    designation = value.designation, typeLabel = "Дупло", territoryLabel = territoryLabel,
    creatorLabel = creatorLabel, createdAt = value.createdAt.displayDateTime(), latitude = value.latitude,
    longitude = value.longitude, properties = value.properties?.let {
        listOf("Дерево" to it.tree, "Высота летка" to "${it.entranceHeightCm} см", "Азимут" to "${it.entranceAzimuthDeg}°", "Наружный диаметр" to "${it.outerDiameterCm} см") +
            listOfNotNull(it.internalDiameterCm?.let { d -> "Внутренний диаметр" to "$d см" }, it.notes?.let { n -> "Дополнительно" to n })
    } ?: emptyList(), media = value.media, onEdit = onEdit,
)

@Composable
fun LogHiveCard(
    value: LogHive, territoryLabel: String, creatorLabel: String, onEdit: () -> Unit = {},
) = PhysicalObjectCard(
    designation = value.designation, typeLabel = "Колода", territoryLabel = territoryLabel,
    creatorLabel = creatorLabel, createdAt = value.createdAt.displayDateTime(), latitude = value.latitude,
    longitude = value.longitude, properties = value.properties?.let {
        listOf("Дерево" to it.tree, "Высота летка" to "${it.entranceHeightCm} см", "Азимут" to "${it.entranceAzimuthDeg}°", "Наружный диаметр" to "${it.outerDiameterCm} см", "Материал" to it.material, "Внутренний диаметр" to "${it.internalDiameterCm} см", "Высота внутреннего объёма" to "${it.internalHeightCm} см") + listOfNotNull(it.notes?.let { n -> "Дополнительно" to n })
    } ?: emptyList(), media = value.media, onEdit = onEdit,
)

@Composable
private fun PhysicalObjectCard(
    designation: String, typeLabel: String, territoryLabel: String, creatorLabel: String,
    createdAt: String, latitude: Double, longitude: Double, properties: List<Pair<String, String>>,
    media: List<PhysicalObjectMedia>, onEdit: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(designation, style = MaterialTheme.typography.headlineSmall)
        Text(typeLabel)
        Text("Территория: $territoryLabel")
        Text("Создал: $creatorLabel")
        Text("Создано: $createdAt")
        Text("Координаты: $latitude, $longitude")
        properties.forEach { (label, value) -> Row(Modifier.fillMaxWidth()) { Text("$label: "); Text(value) } }
        Text("Медиа: ${media.size}")
        media.forEach { item ->
            Text(
                "${if (item.type == PhysicalObjectMediaType.VIDEO) "Видео" else "Фото"}: " +
                    "${item.originalFileName ?: "без имени"} · ${item.byteSize / 1024} КБ",
            )
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("physical-object-edit")) { Text("Редактировать") }
    }
}

private fun java.time.Instant.displayDateTime(): String = atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
