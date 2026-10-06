package org.beesearch.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.data.backuprepository.AndroidBackupTreeAccess
import org.beesearch.app.data.backuprepository.BackupAccessCoordinator
import org.beesearch.app.data.backuprepository.BackupAccessOutcome
import org.beesearch.app.data.backuprepository.BackupAccessStatus
import org.beesearch.app.data.backuprepository.BackupCreateOutcome
import org.beesearch.app.data.backuprepository.BackupLocation
import org.beesearch.app.data.backuprepository.BackupOperationCoordinator
import org.beesearch.app.data.backuprepository.BackupSnapshotStatus

/**
 * Screen state holder for `Настройки → Резервное копирование`.
 *
 * It owns no repository or snapshot semantics: access decisions live in
 * [BackupAccessCoordinator] and snapshot operations in [BackupOperationCoordinator]. This class only
 * runs them in the screen's own coroutine scope, keeps exactly one creation at a time, and
 * translates typed results into one screen state. Field capture never depends on anything here.
 */
internal class BackupAccessViewModel(
    private val coordinator: BackupAccessCoordinator,
    private val operations: BackupOperationCoordinator,
    private val treeAccess: AndroidBackupTreeAccess,
    private val location: BackupLocation,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = BACKUP_DATE_LOCALE,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupScreenState>(BackupScreenState.Loading)
    val state: StateFlow<BackupScreenState> = _state.asStateFlow()

    /** The running manual creation, if any. Null or finished means the button is usable again. */
    private var creation: Job? = null

    private val displayPath: String get() = "$USER_VISIBLE_DOWNLOADS/${location.relativePath}"

    /** The fixed Bee Search Backup folder of this build variant, for the system picker. */
    fun pickerInitialUri(): Uri = treeAccess.pickerInitialUri(location)

    /**
     * Re-reads the repository state; never binds, adopts, initialises or writes anything.
     *
     * A creation that is still running owns the screen state until it finishes, so returning to the
     * screen cannot restart it or overwrite its result.
     */
    fun refresh() {
        if (creation?.isActive == true) return
        viewModelScope.launch {
            _state.value = BackupScreenState.Working
            _state.value = loadedScreenState()
        }
    }

    /**
     * Creates one backup.
     *
     * Only the ready state offers this, and only one creation can run: a repeated tap while the
     * operation is in flight is ignored here and refused by the operation coordinator as well.
     */
    fun createBackup() {
        if (creation?.isActive == true) return
        val ready = _state.value as? BackupScreenState.Ready ?: return
        _state.value = ready.copy(operation = BackupOperationUi.Creating)
        creation = viewModelScope.launch {
            val outcome = try {
                operations.create()
            } catch (cancelled: CancellationException) {
                // Cancellation must never be presented as a created backup.
                _state.value = ready.copy(operation = BackupOperationUi.Cancelled)
                throw cancelled
            }
            _state.value = when (outcome) {
                is BackupCreateOutcome.Created ->
                    outcome.toScreenState(displayPath, zone, locale, ready.snapshots)

                // Another creation owns the screen state; leaving it untouched keeps the running
                // progress visible instead of pretending nothing is happening.
                BackupCreateOutcome.AlreadyRunning -> ready.copy(operation = BackupOperationUi.Creating)

                BackupCreateOutcome.Cancelled ->
                    outcome.toScreenState(displayPath, zone, locale, ready.snapshots)

                is BackupCreateOutcome.Failed -> if (outcome.problem.belongsToAccessState) {
                    // An access-class failure is corroborated before the screen changes state: the S1
                    // access states own a *confirmed* access loss, while a problem the probe cannot
                    // confirm keeps its own message and a retry here.
                    accessClassFailureScreenState(
                        path = displayPath,
                        failure = outcome,
                        accessAfterFailure = coordinator.status(),
                        previousSnapshots = ready.snapshots,
                        zone = zone,
                        locale = locale,
                    )
                } else {
                    // A storage or data failure always keeps its own message and a retry: granting
                    // access again would neither explain nor fix it.
                    outcome.toScreenState(displayPath, zone, locale, ready.snapshots)
                }
            }
        }
    }

    /**
     * Handles the picker result.
     *
     * A cancelled picker returns nothing and is not an error: the screen simply re-reads the
     * current state. Only a supported tree has its grant persisted, so a foreign URI cannot leave
     * a stray persisted permission behind.
     */
    fun onTreePicked(uri: Uri?) {
        if (uri == null) {
            refresh()
            return
        }
        viewModelScope.launch {
            _state.value = BackupScreenState.Working
            val selection = treeAccess.describe(uri)
            // The coordinator validates the exact folder first and only then asks for the grant to be
            // persisted, so a rejected selection never becomes durable access to a foreign folder:
            // measured on the target device, the rejected tree keeps only its transient session grant
            // (persisted=false) while the accepted fixed folder gets a persisted one.
            _state.value = when (val outcome = coordinator.connect(selection) { treeAccess.remember(uri) }) {
                // Access is established: show what the repository holds right away instead of
                // leaving the last-backup line unread until the screen is opened again.
                is BackupAccessOutcome.Ready -> loadedScreenState()

                else -> outcome.toScreenState(displayPath)
            }
        }
    }

    /** Access state plus, when access is ready, what the repository currently holds. */
    private suspend fun loadedScreenState(): BackupScreenState {
        val access = coordinator.status()
        if (access !is BackupAccessStatus.Ready) return access.toScreenState(displayPath)
        val snapshots = operations.inspect()
        if (snapshots is BackupSnapshotStatus.Failed && snapshots.problem.belongsToAccessState) {
            // Access can be lost between the access probe and the snapshot read. Only a probe that
            // confirms it replaces the snapshot problem; otherwise the problem stays visible instead
            // of a healthy-looking screen that hides why the read failed.
            val rechecked = coordinator.status()
            if (rechecked !is BackupAccessStatus.Ready) return rechecked.toScreenState(displayPath)
        }
        return access.toScreenState(
            path = displayPath,
            snapshots = snapshots.toSnapshotsUi(zone, locale),
            operation = BackupOperationUi.Idle,
        )
    }

    companion object {
        fun factory(
            coordinator: BackupAccessCoordinator,
            operations: BackupOperationCoordinator,
            treeAccess: AndroidBackupTreeAccess,
            location: BackupLocation,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupAccessViewModel(coordinator, operations, treeAccess, location) as T
        }
    }
}

/** Backup times are shown to a Russian-speaking user whatever the device locale is. */
internal val BACKUP_DATE_LOCALE: Locale = Locale.forLanguageTag("ru")
