package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val SELECTED_OBJECT_CARD_TAG = "selected-object-card"
internal const val SELECTED_OBJECT_OPEN_TAG = "selected-object-open"
private const val CLOSE_SELECTED_OBJECT_DESCRIPTION = "Закрыть выбранный объект"

/** Compact presentation of the selected map object; opening the full record remains the caller's job. */
@Composable
internal fun MapSelectedObjectCard(
    marker: MapObjectMarker,
    onOpen: (MapObjectMarker) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(SELECTED_OBJECT_CARD_TAG)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResearchObjectMarker(type = marker.type.researchMarkerType(), visualSize = ResearchMarkerCatalog.NORMAL_SIZE_DP.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = marker.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { contentDescription = marker.label },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { onOpen(marker) },
                    modifier = Modifier.testTag(SELECTED_OBJECT_OPEN_TAG),
                ) { Text("Открыть запись") }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = CLOSE_SELECTED_OBJECT_DESCRIPTION },
                ) { Text("✕") }
            }
        }
    }
}
