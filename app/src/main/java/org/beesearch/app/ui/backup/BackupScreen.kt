package org.beesearch.app.ui.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
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
 * S1 establishes access to the fixed backup folder of this build variant; the single backup action
 * delegates capture, media protection, verification and publication to the operation coordinator.
 */
@Composable
internal fun BackupRoute(
    coordinator: BackupAccessCoordinator,
    operations: BackupOperationCoordinator,
    treeAccess: AndroidBackupTreeAccess,
    location: BackupLocation,
    openDirectory: suspend () -> Boolean,
    onBack: () -> Unit,
) {
    val viewModel: BackupAccessViewModel = viewModel(
        factory = BackupAccessViewModel.factory(coordinator, operations, treeAccess, location, openDirectory),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The system picker is the only way to obtain the fixed folder grant; it is pre-positioned at
    // that folder, and whatever the user confirms is validated before the repository is touched.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onTreePicked(uri)
    }

    LaunchedEffect(viewModel) { viewModel.refresh() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackupScreen(
        state = state,
        onBack = onBack,
        onRequestAccess = { picker.launch(viewModel.pickerInitialUri()) },
        onCreateBackup = viewModel::createBackup,
        onRetry = viewModel::refresh,
        onOpenDirectory = {
            scope.launch {
                if (!viewModel.openBackupDirectory()) Toast.makeText(context,
                    "Не удалось открыть папку резервных копий.", Toast.LENGTH_LONG).show()
            }
        },
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
    onOpenDirectory: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val presentation = (state as? BackupScreenState.Ready)?.operation?.let { it::class }
    // A terminal result begins at its heading, rather than inheriting the expanded Ready scroll.
    val scroll = key(presentation) { rememberScrollState() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Резервное копирование") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Назад" },
                    ) {
                        Icon(BackupIcons.Back, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            if (state is BackupScreenState.Ready) {
                val operation = state.operation
                val enabled = operation != BackupOperationUi.Creating
                val label = if (operation is BackupOperationUi.MediaFailed ||
                    operation is BackupOperationUi.Problem ||
                    operation is BackupOperationUi.Cancelled
                ) {
                    BACKUP_CREATE_RETRY
                } else {
                    BACKUP_CREATE_ACTION
                }
                Button(
                    onClick = onCreateBackup,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = BackupSuccessColor, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("backup-create"),
                ) {
                    // Reserve the same text-driven slot for Create and Retry at every font scale.
                    Box(contentAlignment = Alignment.Center) {
                        Text(BACKUP_CREATE_ACTION, color = Color.Transparent,
                            modifier = Modifier.clearAndSetSemantics {})
                        Text(label)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ready = state as? BackupScreenState.Ready
            val terminal = ready?.operation is BackupOperationUi.Creating ||
                ready?.operation is BackupOperationUi.Created ||
                ready?.operation is BackupOperationUi.MediaFailed
            if (!terminal) Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state is BackupScreenState.Ready) AccessStateBlock(state, onRequestAccess, onRetry)
                    Text(BACKUP_PATH_PREFIX, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            state.pathOrEmpty,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = if (state is BackupScreenState.Ready) TextDecoration.Underline else null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .then(if (state is BackupScreenState.Ready) Modifier.clickable(
                                    role = Role.Button,
                                    onClickLabel = "Открыть папку резервных копий",
                                    onClick = onOpenDirectory,
                                ) else Modifier)
                                .padding(8.dp).testTag("backup-path"),
                        )
                    }
                    if (state !is BackupScreenState.Ready) AccessStateBlock(state, onRequestAccess, onRetry)
                }
            }
            if (ready != null) {
                when (ready.operation) {
                    BackupOperationUi.Idle -> SnapshotSection(ready.snapshots)
                    BackupOperationUi.Creating -> WorkingBackupBlock()
                    is BackupOperationUi.Created -> SuccessBackupBlock(ready.snapshots, ready.operation)
                    is BackupOperationUi.MediaFailed -> MediaFailureBlock(ready.operation)
                    BackupOperationUi.Cancelled -> ProblemBlock(BACKUP_CANCELLED_MESSAGE)
                    is BackupOperationUi.Problem -> ProblemBlock(ready.operation.message)
                }
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

        is BackupScreenState.Ready -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("backup-ready"),
        ) {
            StatusIcon(BackupSuccessColor, BackupIconKind.Check, Modifier.size(24.dp))
            Text(BACKUP_READY_LABEL.removePrefix("✓ "), style = MaterialTheme.typography.titleMedium)
        }

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
 * Ready-state contents, promise boundary, and the latest published summary.
 */
@Composable
private fun SnapshotSection(
    snapshots: BackupSnapshotsUi,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("backup-disclosure"),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { expanded = !expanded },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(BACKUP_SECTION_TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) BackupIcons.ChevronUp else BackupIcons.ChevronDown,
                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(BACKUP_EXPLANATION, style = MaterialTheme.typography.bodyMedium)
                    if (expanded) {
                        listOf(
                            "Территории и ареалы",
                            "Точки наблюдения и погода",
                            "Наблюдения за пчёлами и циклы полётов",
                            "Дупла",
                            "Колоды",
                            "Настройки приложения",
                            "Фото и видео, относящиеся к этим данным",
                        ).forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) { Text(BACKUP_PROMISE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp).testTag("backup-promise")) }

        SnapshotStateBlock(snapshots)
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
private fun ProblemBlock(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("backup-message"),
    )
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
private fun MediaFailureBlock(operation: BackupOperationUi.MediaFailed) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        StatusIcon(MaterialTheme.colorScheme.error, BackupIconKind.Error, Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(BACKUP_MEDIA_ERROR_TITLE, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup-media-error"), textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$BACKUP_MEDIA_ERROR_PREFIX ${operation.failedCount} из ${operation.totalCount} $BACKUP_MEDIA_ERROR_SUFFIX",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                BACKUP_MEDIA_ERROR_RETRY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            }
        }
    }
}

@Composable
private fun WorkingBackupBlock() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp)
            Icon(BackupIcons.Working, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(BACKUP_CREATING, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("backup-creating"), textAlign = TextAlign.Center)
        Text(
            "Данные, фото и видео сохраняются и проверяются.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SuccessBackupBlock(
    snapshots: BackupSnapshotsUi,
    operation: BackupOperationUi.Created,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        StatusIcon(BackupSuccessColor, BackupIconKind.Check, Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(BACKUP_CREATED_TITLE, style = MaterialTheme.typography.titleLarge, color = BackupSuccessColor, modifier = Modifier.testTag("backup-created"), textAlign = TextAlign.Center)
        Text(
            if (operation.verifiedMediaCount == 0) BACKUP_CREATED_NO_MEDIA_BODY else BACKUP_CREATED_BODY,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center,
        )
        if (operation.verifiedMediaCount > 0) {
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.small, color = BackupSuccessColor.copy(alpha = .12f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(BackupIcons.Check, null, tint = BackupSuccessColor, modifier = Modifier.size(24.dp))
                    Text("Проверено: ${operation.verifiedMediaCount} из ${operation.verifiedMediaCount} файлов",
                        modifier = Modifier.padding(start = 8.dp).testTag("backup-verified-count"),
                        color = BackupSuccessColor)
                }
            }
        }
        // Publication succeeded, but an empty post-publication listing confirms no latest date.
        // Keep warnings visible; only the ordinary Ready screen claims that no copies exist.
        if (snapshots != BackupSnapshotsUi.None) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) { SnapshotStateBlock(snapshots) }
        }
    }
}

private enum class BackupIconKind { Check, Error }
private val BackupSuccessColor = Color(0xFF2B7D5B)

@Composable
private fun StatusIcon(color: Color, kind: BackupIconKind, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(
            if (kind == BackupIconKind.Check) BackupIcons.Check else BackupIcons.Exclamation,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(.6f),
        ) }
    }
}

private object BackupIcons {
    val Back: ImageVector = ImageVector.Builder("BackupBack", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) { moveTo(20f, 11f); lineTo(7.8f, 11f); lineTo(13.4f, 5.4f); lineTo(12f, 4f); lineTo(4f, 12f); lineTo(12f, 20f); lineTo(13.4f, 18.6f); lineTo(7.8f, 13f); lineTo(20f, 13f); close() }
        .build()
    val Working: ImageVector = ImageVector.Builder("BackupWorking", 48.dp, 48.dp, 48f, 48f)
        .path(fill = SolidColor(Color.Black)) { moveTo(22f, 39f); lineTo(26f, 39f); lineTo(26f, 12f); lineTo(37f, 23f); lineTo(40f, 20f); lineTo(24f, 4f); lineTo(8f, 20f); lineTo(11f, 23f); lineTo(22f, 12f); close(); moveTo(8f, 41f); lineTo(40f, 41f); lineTo(40f, 45f); lineTo(8f, 45f); close() }
        .build()
    val ChevronDown: ImageVector = ImageVector.Builder("BackupChevronDown", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) { moveTo(5f, 8f); lineTo(12f, 15f); lineTo(19f, 8f); lineTo(20.5f, 9.5f); lineTo(12f, 18f); lineTo(3.5f, 9.5f); close() }
        .build()
    val ChevronUp: ImageVector = ImageVector.Builder("BackupChevronUp", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) { moveTo(5f, 16f); lineTo(12f, 9f); lineTo(19f, 16f); lineTo(20.5f, 14.5f); lineTo(12f, 6f); lineTo(3.5f, 14.5f); close() }
        .build()
    val Check: ImageVector = ImageVector.Builder("BackupCheck", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) { moveTo(9f, 16.2f); lineTo(4.8f, 12f); lineTo(3.4f, 13.4f); lineTo(9f, 19f); lineTo(21f, 7f); lineTo(19.6f, 5.6f); close() }
        .build()
    val Exclamation: ImageVector = ImageVector.Builder("BackupExclamation", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) { moveTo(11f, 4f); lineTo(13f, 4f); lineTo(13f, 15f); lineTo(11f, 15f); close(); moveTo(11f, 18f); lineTo(13f, 18f); lineTo(13f, 20f); lineTo(11f, 20f); close() }
        .build()
}
