package org.beesearch.app.data.backuprepository

import java.util.UUID

/** What the system picker returned: a usable Bee Search tree, or something else entirely. */
internal sealed interface BackupTreeSelection {
    data class Picked(val locator: String, val treeDocumentId: String) : BackupTreeSelection

    /** No tree at all, a non-tree URI or a tree from a provider Bee Search does not support. */
    data object Unsupported : BackupTreeSelection
}

/**
 * Grouped, user-meaningful problems.
 *
 * Raw [RepositoryError] names never reach the UI: several of them mean the same thing to a field
 * user, and the groups are what the screen can actually offer an action for. The same groups cover
 * repository *access* problems and manual *backup creation* problems, so one screen keeps one
 * problem vocabulary instead of two competing ones.
 */
internal enum class BackupAccessProblem {
    /** The fixed folder grant is missing, revoked or unusable. Granting access again can fix it. */
    PERMISSION,

    /** The stored or picked tree is not a supported fixed location. Granting again can fix it. */
    UNSUPPORTED_LOCATION,

    /** Unclassified storage/provider failure. Granting access again is the only offered recovery. */
    PROVIDER,

    /** The folder holds a different repository identity. Never repaired automatically. */
    IDENTITY_MISMATCH,

    /** The connected repository's own `repository.json` is gone from the fixed folder. */
    REPOSITORY_MISSING,

    /** `repository.json` exists but is damaged or was written by a different Bee Search version. */
    REPOSITORY_NOT_RECOGNIZED,

    /** Unexpected content or a file where a directory belongs. Never repaired automatically. */
    AMBIGUOUS_OR_INVALID,

    /** The device-local binding is inconsistent or could not be persisted. */
    BINDING,

    /** The phone cannot afford the fixed reserve even for a new repository. */
    CAPACITY,

    /** The current research state could not be captured as one consistent graph. */
    LOGICAL_DATA,

    /** A backup could not be created or verified safely; existing backups stay unchanged. */
    SNAPSHOT,

    /** The backup operation stopped before it finished. */
    CANCELLED,
    ;

    /** True only for problems that granting access to the fixed folder again may resolve. */
    val recoverableByGrant: Boolean
        get() = this == PERMISSION || this == UNSUPPORTED_LOCATION || this == PROVIDER
}

/** Which identity operation produced the connection, for the caller's own reporting. */
internal enum class BackupAccessAction { INITIALIZED, ADOPTED, RECONNECTED }

/** Read-only screen status. Loading and working belong to the screen, not to the repository. */
internal sealed interface BackupAccessStatus {
    /** No durable binding and a usable fixed folder: the user still has to grant access. */
    data object GrantRequired : BackupAccessStatus

    data class Ready(val repositoryId: UUID) : BackupAccessStatus

    /** A binding exists but is not usable right now; granting access again is offered. */
    data class AccessLost(val problem: BackupAccessProblem) : BackupAccessStatus

    /** Not recoverable by granting access again. */
    data class Failed(val problem: BackupAccessProblem) : BackupAccessStatus
}

/** Outcome of one explicit access setup attempt. */
internal sealed interface BackupAccessOutcome {
    data class Ready(val repositoryId: UUID, val action: BackupAccessAction) : BackupAccessOutcome

    /** The user selected something that is not the fixed Bee Search Backup folder. */
    data object WrongFolder : BackupAccessOutcome

    data class Failed(val problem: BackupAccessProblem) : BackupAccessOutcome
}

/**
 * The production repository access setup: one fixed folder, one explicit grant, and the existing
 * D095/D096 identity rules deciding initialize / adopt / reconnect.
 *
 * The order is deliberate. A selection that is not the exact fixed folder is rejected before any
 * repository call, so a wrong folder cannot create, bind or initialise anything. For the exact
 * folder, an existing durable binding reconnects to the same UUID only; without a binding an
 * existing `repository.json` is adopted and a new repository is created only in an otherwise valid
 * empty skeleton. Everything else fails closed and leaves the durable binding untouched.
 *
 * Free of Android types on purpose: this whole state machine is testable on the host.
 */
internal class BackupAccessCoordinator(
    private val bootstrap: BackupDirectoryBootstrap,
    private val repository: BoundRepository,
    private val location: BackupLocation,
) {
    /**
     * Current status for the screen.
     *
     * The fixed directory skeleton is materialised first: this repairs a removed `Media`,
     * `Snapshots` or `Staging` child, and fails closed when a file occupies one of those paths.
     * Identity is never initialised, adopted or repaired here — only directories are created.
     */
    suspend fun status(): BackupAccessStatus {
        val skeleton = bootstrap.ensure()
        return when (val probe = repository.probe()) {
            // No durable binding: only the skeleton is prepared, identity is never initialised here.
            RepositoryConnection.Unbound -> when (skeleton) {
                is RepositoryResult.Success -> BackupAccessStatus.GrantRequired
                is RepositoryResult.Failure -> BackupAccessStatus.Failed(skeleton.error.toBackupAccessProblem())
            }

            // A reachable bound repository means every expected directory exists as a directory, so
            // this state needs no skeleton decision of its own.
            is RepositoryConnection.Bound -> BackupAccessStatus.Ready(probe.binding.expectedRepositoryId)

            // Any failed probe already implies a durable binding existed: an unbound repository is
            // reported as Unbound before any storage is touched.
            is RepositoryConnection.Failed -> problemStatus(probe.error.toBackupAccessProblem())
        }
    }

    /**
     * Connects the repository to the exact fixed folder after the user granted access.
     *
     * [persistGrant] is called only after the selection is known to be the exact fixed folder, and
     * only its result decides whether the access may be used: a grant that was not persisted would
     * not survive a restart. A rejected selection therefore never leaves a durable permission behind
     * for a folder Bee Search does not use.
     */
    suspend fun connect(selection: BackupTreeSelection, persistGrant: () -> Boolean): BackupAccessOutcome {
        val picked = selection as? BackupTreeSelection.Picked ?: return BackupAccessOutcome.WrongFolder
        if (!location.accepts(picked.treeDocumentId)) return BackupAccessOutcome.WrongFolder
        if (!persistGrant()) return BackupAccessOutcome.Failed(BackupAccessProblem.PERMISSION)
        when (val skeleton = bootstrap.ensure()) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Failure ->
                return BackupAccessOutcome.Failed(skeleton.error.toBackupAccessProblem())
        }
        val probe = repository.probe()
        // A failed probe still means a binding exists; the fresh grant may be exactly what repairs
        // the stored location, so reconnecting is attempted. A wrong identity or an unreadable
        // binding still fails closed inside reconnect.
        return if (probe is RepositoryConnection.Unbound) {
            adoptOrInitialize(picked.locator)
        } else {
            reconnect(picked.locator)
        }
    }

    /** Same identity only: [BoundRepository.reconnect] never accepts a different repository UUID. */
    private suspend fun reconnect(locator: String): BackupAccessOutcome =
        when (val result = repository.reconnect(locator)) {
            is RepositoryResult.Success ->
                BackupAccessOutcome.Ready(result.value.expectedRepositoryId, BackupAccessAction.RECONNECTED)

            is RepositoryResult.Failure -> BackupAccessOutcome.Failed(result.error.toBackupAccessProblem())
        }

    /**
     * A reinstall or a lost local binding has no remembered repository, so an existing
     * `repository.json` is adopted. A new repository is created only when the header is genuinely
     * absent *and* the folder is an otherwise valid empty Bee Search skeleton; ambiguous content,
     * an invalid header or an unsupported format stops the flow without touching the folder.
     */
    private suspend fun adoptOrInitialize(locator: String): BackupAccessOutcome =
        when (val adoption = repository.adoptExisting(locator)) {
            is RepositoryResult.Success ->
                BackupAccessOutcome.Ready(adoption.value.expectedRepositoryId, BackupAccessAction.ADOPTED)

            is RepositoryResult.Failure ->
                if (adoption.error != RepositoryError.NOT_FOUND) {
                    BackupAccessOutcome.Failed(adoption.error.toBackupAccessProblem())
                } else {
                    when (val created = repository.initializeNew(locator)) {
                        is RepositoryResult.Success -> BackupAccessOutcome.Ready(
                            created.value.expectedRepositoryId,
                            BackupAccessAction.INITIALIZED,
                        )

                        is RepositoryResult.Failure ->
                            BackupAccessOutcome.Failed(created.error.toBackupAccessProblem())
                    }
                }
        }

    private fun problemStatus(problem: BackupAccessProblem): BackupAccessStatus =
        if (problem.recoverableByGrant) {
            BackupAccessStatus.AccessLost(problem)
        } else {
            BackupAccessStatus.Failed(problem)
        }
}

/**
 * Groups repository failures into problems a field user can act on.
 *
 * An unbound repository is grouped with the permission problems because granting access again is
 * the action that resolves it. Snapshot-specific failures are grouped as generic storage failures
 * here: the snapshot screen owns their presentation.
 */
internal fun RepositoryError.toBackupAccessProblem(): BackupAccessProblem = when (this) {
    RepositoryError.UNBOUND,
    RepositoryError.PERMISSION_LOST,
    RepositoryError.BOUND_ROOT_UNAVAILABLE,
    -> BackupAccessProblem.PERMISSION

    RepositoryError.NOT_FOUND,
    RepositoryError.BOUND_REPOSITORY_MISSING,
    -> BackupAccessProblem.REPOSITORY_MISSING

    RepositoryError.ROOT_IDENTITY_MISMATCH -> BackupAccessProblem.IDENTITY_MISMATCH

    RepositoryError.INVALID_HEADER,
    RepositoryError.UNSUPPORTED_FORMAT,
    -> BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED

    RepositoryError.AMBIGUOUS_REPOSITORY,
    RepositoryError.DIRECTORY_CONFLICT,
    -> BackupAccessProblem.AMBIGUOUS_OR_INVALID

    RepositoryError.UNSUPPORTED_PUBLICATION_PATH -> BackupAccessProblem.UNSUPPORTED_LOCATION

    RepositoryError.BINDING_INVALID,
    RepositoryError.BINDING_CHANGED,
    RepositoryError.BINDING_PERSISTENCE_FAILED,
    -> BackupAccessProblem.BINDING

    RepositoryError.CAPACITY_UNKNOWN,
    RepositoryError.CAPACITY_INSUFFICIENT,
    -> BackupAccessProblem.CAPACITY

    // Snapshot-specific and operation-specific failures are grouped as their own problems so the
    // screen can say what actually happened instead of calling them generic storage failures.
    RepositoryError.LOGICAL_STATE_INCONSISTENT -> BackupAccessProblem.LOGICAL_DATA
    RepositoryError.INVALID_SNAPSHOT,
    RepositoryError.SNAPSHOT_LIMIT_EXCEEDED,
    RepositoryError.SNAPSHOT_ID_CONFLICT,
    // Media-evidence failures stay distinct at the operation-result level, where a backend caller
    // must tell "this snapshot's required media is not verifiable here" from a container failure.
    // For the diagnosis groups of the access screen they are a snapshot-creation problem.
    RepositoryError.MEDIA_EVIDENCE_MISSING,
    RepositoryError.MEDIA_EVIDENCE_MISMATCH,
    RepositoryError.MEDIA_EVIDENCE_INCONSISTENT,
    -> BackupAccessProblem.SNAPSHOT

    RepositoryError.CANCELLED -> BackupAccessProblem.CANCELLED

    RepositoryError.PROVIDER_FAILURE,
    RepositoryError.WRITE_FAILED,
    RepositoryError.SYNC_FAILED,
    RepositoryError.VERIFY_FAILED,
    RepositoryError.PUBLISH_FAILED,
    RepositoryError.METADATA_INCONSISTENCY,
    RepositoryError.SOURCE_CHANGED,
    RepositoryError.DELETE_FAILED,
    -> BackupAccessProblem.PROVIDER
}
