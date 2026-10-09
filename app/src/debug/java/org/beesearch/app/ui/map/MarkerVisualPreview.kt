package org.beesearch.app.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * DEBUG source set only. Screen-space specimens over the existing BeeMap, not geographic records.
 * No persistence, Room access, style mutations or extra map lifecycle owner.
 */
@Composable
internal fun MarkerVisualPreview(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(ResearchMarkerCatalog.NORMAL_SIZE_DP) }
    var compareDp by remember { mutableStateOf(true) }
    var crowded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ResearchMarkerType.OBSERVATION_POINT) }
    var controlsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val visualSize = if (compareDp) size.dp else researchMarkerSizeDp(size, density)
    BackHandler(enabled = open) { open = false }
    BoxWithConstraints(modifier) {
        if (!open) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 8.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                TextButton(onClick = { open = true }, modifier = Modifier.testTag("marker-preview-open")) {
                    Text("DEV: маркеры")
                }
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 8.dp, end = 8.dp)
                    .onSizeChanged { controlsHeightPx = it.height }
                    .testTag("marker-preview-controls"),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DEV · образцы", modifier = Modifier.weight(1f))
                        TextButton(onClick = { open = false }, modifier = Modifier.testTag("marker-preview-close")) {
                            Text("Закрыть")
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        ResearchMarkerCatalog.diagnosticSizesPx.forEach { candidate ->
                            TextButton(onClick = { size = candidate }) {
                                Text("${if (size == candidate) "✓ " else ""}$candidate ${if (compareDp) "dp" else "px"}")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { compareDp = !compareDp }) {
                            Text(if (compareDp) "К px" else "Сравнить dp")
                        }
                        TextButton(onClick = { crowded = !crowded }) {
                            Text(if (crowded) "Разнести" else "Рядом")
                        }
                    }
                    Text(
                        "Образцы, без записи данных.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Keep specimens below measured controls at any fontScale. They follow the viewport,
            // not a geographic coordinate; operational map symbols remain unchanged.
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(
                    top = maxOf(
                        64.dp + with(LocalDensity.current) { controlsHeightPx.toDp() } + 16.dp,
                        maxHeight / 2 + 48.dp,
                    ),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    ResearchMarkerType.entries.forEach { type ->
                        ResearchObjectMarker(
                            type = type,
                            visualSize = visualSize,
                            selected = type == selectedType,
                            modifier = Modifier.clickable(role = Role.Button) { selectedType = type },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.Center) {
                    ResearchMarkerType.entries.forEach { type ->
                        ResearchObjectMarker(type = type, visualSize = visualSize, selected = true)
                    }
                }
                if (crowded) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ResearchMarkerType.entries.forEachIndexed { index, type ->
                            ResearchObjectMarker(
                                type = type,
                                visualSize = visualSize,
                                selected = index == 2,
                                modifier = Modifier.align(Alignment.TopCenter).offset(
                                    x = ((index - 2) * 20).dp,
                                    y = if (index % 2 == 0) 0.dp else 14.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
