package org.beesearch.app.ui.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.data.backuprepository.AndroidBackupTreeAccess
import org.beesearch.app.data.backuprepository.BackupAccessCoordinator
import org.beesearch.app.data.backuprepository.BackupLocation

/**
 * Settings → Резервное копирование.
 *
 * S1 scope only: show the fixed backup folder of this build variant and establish repository
 * access to it. Creating a backup, media protection, FULL snapshots, the PC handoff and offload are
 * later slices and deliberately absent here.
 */
@Composable
internal fun BackupRoute(
    coordinator: BackupAccessCoordinator,
    treeAccess: AndroidBackupTreeAccess,
    location: BackupLocation,
    onBack: () -> Unit,
) {
    val viewModel: BackupAccessViewModel = viewModel(
        factory = BackupAccessViewModel.factory(coordinator, treeAccess, location),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The system picker is the only way to obtain the fixed folder grant; it is pre-positioned at
    // that folder, and whatever the user confirms is validated before the repository is touched.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onTreePicked(uri)
    }

    LaunchedEffect(viewModel) { viewModel.refresh() }

    BackupScreen(
        state = state,
        onBack = onBack,
        onRequestAccess = { picker.launch(viewModel.pickerInitialUri()) },
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreen(
    state: BackupScreenState,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Резервное копирование") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(BACKUP_PATH_PREFIX, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        state.pathOrEmpty,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("backup-path"),
                    )
                    BackupStateBlock(
                        state = state,
                        onRequestAccess = onRequestAccess,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupStateBlock(
    state: BackupScreenState,
    onRequestAccess: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        BackupScreenState.Loading -> CircularProgressIndicator()

        BackupScreenState.Working -> {
            CircularProgressIndicator()
            Text(BACKUP_WORKING_LABEL, style = MaterialTheme.typography.bodyMedium)
        }

        is BackupScreenState.NeedsGrant -> {
            Text(BACKUP_GRANT_EXPLANATION, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRequestAccess,
                modifier = Modifier.fillMaxWidth().testTag("backup-grant"),
            ) { Text("Разрешить доступ") }
        }

        is BackupScreenState.Ready -> Text(
            BACKUP_READY_LABEL,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("backup-ready"),
        )

        is BackupScreenState.AccessLost -> {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("backup-message"),
            )
            Button(
                onClick = onRequestAccess,
                modifier = Modifier.fillMaxWidth().testTag("backup-restore-access"),
            ) { Text("Восстановить доступ") }
        }

        is BackupScreenState.Failed -> {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("backup-message"),
            )
            // A rejected selection is the user's own choice to correct, so the picker is offered
            // again; any other failed state only offers re-reading the current access.
            if (state.offerAccess) {
                Button(
                    onClick = onRequestAccess,
                    modifier = Modifier.fillMaxWidth().testTag("backup-grant"),
                ) { Text("Разрешить доступ") }
            } else {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("backup-retry")) {
                    Text("Проверить снова")
                }
            }
        }
    }
}
