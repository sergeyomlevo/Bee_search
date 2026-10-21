package org.beesearch.app.ui.userpoints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.repository.UserPointRepository

/** Global notice for a completed point deletion whose managed photo cleanup is pending. */
@Composable
internal fun UserPointCleanupNotice(
    repository: UserPointRepository,
    mediaStore: UserPointMediaStore,
) {
    val model: UserPointDeletionViewModel = viewModel(
        key = "user-point-deletion",
        factory = UserPointDeletionViewModel.factory(repository, mediaStore),
    )
    val state = model.state.collectAsStateWithLifecycle().value
    if (!state.showCleanupNotice) return

    AlertDialog(
        onDismissRequest = { if (!state.isWorking) model.dismissCleanupNotice() },
        title = { Text("Не удалось удалить часть фотографий") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("user-point-cleanup-notice"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { Text(state.cleanupMessage) }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = model::retryCleanup,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-cleanup-retry"),
                ) { Text(if (state.isWorking) "Повтор…" else "Повторить") }
                TextButton(
                    onClick = model::dismissCleanupNotice,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth().testTag("user-point-cleanup-close"),
                ) { Text("Закрыть") }
            }
        },
        dismissButton = null,
    )
}
