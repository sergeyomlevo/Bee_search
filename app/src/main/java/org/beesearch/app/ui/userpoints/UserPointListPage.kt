package org.beesearch.app.ui.userpoints

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.ui.points.CompactScreenHeader
import java.util.UUID

@Composable
internal fun UserPointListPage(
    state: UserPointDisplayState,
    onBack: () -> Unit,
    onOpen: (UUID) -> Unit,
    onToggle: (UUID, Boolean) -> Unit,
    onRetryRead: () -> Unit,
    onRetryVisibility: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag("user-point-list-page"),
    ) {
        CompactScreenHeader(
            title = "Мои точки",
            onBack = onBack,
            modifier = Modifier.testTag("user-point-list-header"),
        )
        when {
            state.loading -> UserPointListLoading(Modifier.weight(1f))
            state.error != null -> UserPointListError(Modifier.weight(1f), onRetryRead)
            state.points.isEmpty() -> UserPointListEmpty(Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("user-point-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.points, key = { it.id }) { point ->
                    UserPointListRow(
                        point = point,
                        writing = point.id in state.writing,
                        writeError = point.id in state.writeErrors,
                        onOpen = { onOpen(point.id) },
                        onToggle = { onToggle(point.id, it) },
                        onRetryVisibility = { onRetryVisibility(point.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserPointListLoading(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().testTag("user-point-list-loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UserPointListError(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp).testTag("user-point-list-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Не удалось загрузить Мои точки", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Повторить")
        }
    }
}

@Composable
private fun UserPointListEmpty(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(32.dp).testTag("user-point-list-empty"),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("Нет сохранённых точек", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UserPointListRow(
    point: UserPoint,
    writing: Boolean,
    writeError: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRetryVisibility: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("user-point-row-${point.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(
                checked = point.isVisible,
                onCheckedChange = onToggle,
                enabled = !writing,
                modifier = Modifier
                    .testTag("user-point-visibility-${point.id}")
                    .semantics { role = Role.Switch; contentDescription = "Показывать на карте: ${point.title}" },
            )
            Text(
                text = point.title,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpen)
                    .padding(vertical = 12.dp)
                    .testTag("user-point-title-${point.id}"),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (writeError) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 80.dp, end = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "Не удалось сохранить видимость",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onRetryVisibility,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("user-point-retry-visibility-${point.id}"),
                ) { Text("Повторить") }
            }
        }
    }
}
