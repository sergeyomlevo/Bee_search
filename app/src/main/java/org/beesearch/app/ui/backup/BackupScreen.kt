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
import androidx.compose.foundation.layout.size
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
import org.beesearch.app.data.backuprepository.BackupOperationCoordinator

/**
 * Settings → Резервное копирование.
 *
 * S1 establishes access to the fixed backup folder of this build variant; S2 lets the user create
 * the existing METADATA_ONLY research-metadata backup and shows what the repository currently holds.
 * Media bytes, FULL snapshots, the PC handoff, offload and restore are later slices and deliberately
 * absent here.
 */
@Composable
internal fun BackupRoute(
    coordinator: BackupAccessCoordinator,
    operations: BackupOperationCoordinator,
    treeAccess: AndroidBackupTreeAccess,
    location: BackupLocation,
    onBack: () -> Unit,
) {
    val viewModel: BackupAccessViewModel = viewModel(
        factory = BackupAccessViewModel.factory(coordinator, operations, treeAccess, location),
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
        onCreateBackup = viewModel::createBackup,
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
    onCreateBackup: () -> Unit = {},
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
                    AccessStateBlock(
                        state = state,
                        onRequestAccess = onRequestAccess,
                        onRetry = onRetry,
                    )
                }
            }

            val ready = state as? BackupScreenState.Ready
            if (ready != null) {
                SnapshotSection(
                    snapshots = ready.snapshots,
                    operation = ready.operation,
                    onCreateBackup = onCreateBackup,
                )
            }
        }
    }
}

@Composable
private fun AccessStateBlock(
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

/**
 * What the backup contains, when the last one was made, and the single action that changes it.
 *
 * The photo/video exclusion is stated next to the action, so the meaning of the backup is visible
 * where the user decides to make one, and again after a successful creation.
 */
@Composable
private fun SnapshotSection(
    snapshots: BackupSnapshotsUi,
    operation: BackupOperationUi,
    onCreateBackup: () -> Unit,
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
            Text(BACKUP_SECTION_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(BACKUP_EXPLANATION, style = MaterialTheme.typography.bodyMedium)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    BACKUP_MEDIA_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp).testTag("backup-media-note"),
                )
            }

            SnapshotStateBlock(snapshots)

            when (operation) {
                BackupOperationUi.Idle -> CreateButton(onClick = onCreateBackup, enabled = true)

                BackupOperationUi.Creating -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        BACKUP_CREATING,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("backup-creating"),
                    )
                    // The action stays visible but disabled: exactly one creation can run.
                    CreateButton(onClick = onCreateBackup, enabled = false)
                }

                BackupOperationUi.Created -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                BACKUP_CREATED_TITLE,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.testTag("backup-created"),
                            )
                            Text(
                                BACKUP_CREATED_BODY,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                BACKUP_CREATED_MEDIA_NOTE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    CreateButton(onClick = onCreateBackup, enabled = true)
                }

                BackupOperationUi.Cancelled -> ProblemBlock(
                    message = BACKUP_CANCELLED_MESSAGE,
                    onRetry = onCreateBackup,
                )

                is BackupOperationUi.Problem -> ProblemBlock(
                    message = operation.message,
                    onRetry = onCreateBackup,
                )
            }
        }
    }
}

@Composable
private fun SnapshotStateBlock(snapshots: BackupSnapshotsUi) {
    when (snapshots) {
        // The repository has not been read yet in this visit: nothing is claimed about it.
        BackupSnapshotsUi.Reading -> Unit

        BackupSnapshotsUi.None -> Text(
            BACKUP_NO_SNAPSHOTS,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("backup-none"),
        )

        is BackupSnapshotsUi.Latest -> {
            Text(BACKUP_LAST_PREFIX, style = MaterialTheme.typography.labelMedium)
            Text(
                snapshots.createdAt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("backup-latest"),
            )
            if (snapshots.warning != null) WarningBlock(snapshots.warning)
        }

        is BackupSnapshotsUi.Problem -> WarningBlock(snapshots.message)
    }
}

/** A failed attempt states what happened and offers the one action that can follow: try again. */
@Composable
private fun ProblemBlock(message: String, onRetry: () -> Unit) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("backup-message"),
    )
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().testTag("backup-create-retry"),
    ) { Text(BACKUP_CREATE_RETRY) }
}

/** A healthy last backup is still shown, but the screen does not pretend everything is verified. */
@Composable
private fun WarningBlock(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp).testTag("backup-snapshot-warning"),
        )
    }
}

@Composable
private fun CreateButton(onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("backup-create"),
    ) { Text(BACKUP_CREATE_ACTION) }
}
