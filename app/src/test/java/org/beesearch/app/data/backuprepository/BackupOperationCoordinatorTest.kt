package org.beesearch.app.data.backuprepository

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.backupsnapshot.BackupSnapshotOperations
import org.beesearch.app.data.backupsnapshot.SnapshotIdentity
import org.beesearch.app.data.backupsnapshot.SnapshotMetrics
import org.beesearch.app.data.backupsnapshot.ValidatedSnapshot
import org.beesearch.app.data.backupoperation.CreateBackupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val REPOSITORY_ID: UUID = UUID.fromString("df4d52a9-7033-43c5-9620-e0e5b2fb3e16")

private fun validatedSnapshot(createdAtEpochMs: Long, snapshotId: UUID = UUID.randomUUID()): ValidatedSnapshot =
    ValidatedSnapshot(
        identity = SnapshotIdentity(snapshotId, REPOSITORY_ID, "Dev", createdAtEpochMs),
        wholeSha256 = "a".repeat(64),
        byteSize = 4096,
        metrics = SnapshotMetrics(
            recordCounts = emptyMap(),
            entryBytes = emptyMap(),
            maxRecordBytes = 0,
            zipBytes = 0,
            totalBytes = 0,
        ),
    )

private fun validCandidate(createdAtEpochMs: Long, path: String = "Snapshots/snapshot-valid.zip") =
    SnapshotCandidateResult(path, validatedSnapshot(createdAtEpochMs))

private fun unusableCandidate(path: String = "Snapshots/snapshot-damaged.zip") =
    SnapshotCandidateResult(path, snapshot = null, error = RepositoryError.INVALID_SNAPSHOT)

private fun discovery(vararg candidates: SnapshotCandidateResult) = SnapshotDiscovery(candidates.toList())

/** Records every call so the tests can prove exactly one creation happened. */
private class FakeSnapshotOperations : BackupSnapshotOperations {
    var discoveryResult: RepositoryResult<SnapshotDiscovery> = RepositoryResult.Success(discovery())
    var createResults = ArrayDeque<RepositoryResult<CommittedSnapshot>>()
    var createBackupResults = ArrayDeque<CreateBackupResult>()
    var onCreate: (suspend () -> Unit)? = null
    var createCalls = 0
    var summaryCalls = 0
    var strongDiscoverCalls = 0

    override suspend fun create(): RepositoryResult<CommittedSnapshot> {
        createCalls++
        onCreate?.invoke()
        return createResults.removeFirstOrNull() ?: error("no create result queued")
    }

    override suspend fun discover(): RepositoryResult<SnapshotDiscovery> {
        strongDiscoverCalls++
        error("ordinary coordinator reads must never call strong discover()")
    }

    override suspend fun readPublishedSummary(): RepositoryResult<PublishedBackupSummary> {
        summaryCalls++
        return when (val result = discoveryResult) {
            is RepositoryResult.Failure -> RepositoryResult.Failure(result.error, result.detail)
            is RepositoryResult.Success -> {
                val latest = result.value.candidates.mapNotNull { it.snapshot }
                    .maxWithOrNull(compareBy({ it.identity.createdAtEpochMs }, { it.identity.snapshotId.toString() }))
                    ?.identity
                    ?.let { PublishedBackupInfo(it.snapshotId, it.createdAtEpochMs) }
                RepositoryResult.Success(
                    PublishedBackupSummary(latest, result.value.candidates.any { it.snapshot == null }),
                )
            }
        }
    }

    suspend fun nextCreateResult(): CreateBackupResult {
        createBackupResults.removeFirstOrNull()?.let { return it }
        return when (val result = create()) {
            is RepositoryResult.Failure -> CreateBackupResult.Failed(result.error)
            is RepositoryResult.Success -> CreateBackupResult.Created(result.value, result.value.snapshot.references.size)
        }
    }
}

/**
 * Snapshot operations behind the manual backup button.
 *
 * These are the S2 acceptance cases: what the repository holds, what a failed or cancelled attempt
 * reports, and that a double tap cannot publish two backups.
 */
class BackupOperationCoordinatorTest {
    private val operations = FakeSnapshotOperations()
    private val coordinator = BackupOperationCoordinator(operations) { operations.nextCreateResult() }

    private fun committed(createdAtEpochMs: Long) = RepositoryResult.Success(
        CommittedSnapshot("Snapshots/snapshot-committed.zip", validatedSnapshot(createdAtEpochMs)),
    )

    // 1. Ready + zero snapshots.
    @Test
    fun repositoryWithoutSnapshotsReportsNone() = runBlocking {
        assertEquals(BackupSnapshotStatus.None, coordinator.inspect())
    }

    // 2. One valid snapshot.
    @Test
    fun singleValidSnapshotIsReportedAsTheLatest() = runBlocking {
        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(1_800_000_000_000)))
        assertEquals(
            BackupSnapshotStatus.Latest(1_800_000_000_000, hasUnusable = false),
            coordinator.inspect(),
        )
    }

    // 3. Several valid snapshots: the accepted SnapshotDiscovery latest semantics decide.
    @Test
    fun newestValidSnapshotWinsRegardlessOfListingOrder() = runBlocking {
        operations.discoveryResult = RepositoryResult.Success(
            discovery(
                validCandidate(1_800_000_000_000, "Snapshots/newest-first.zip"),
                validCandidate(1_700_000_000_000, "Snapshots/older.zip"),
                validCandidate(1_900_000_000_000, "Snapshots/later-looking-name.zip"),
            ),
        )
        assertEquals(
            BackupSnapshotStatus.Latest(1_900_000_000_000, hasUnusable = false),
            coordinator.inspect(),
        )
    }

    @Test
    fun equalCreationTimesAreBrokenBySnapshotIdLikeTheAcceptedSemantics() = runBlocking {
        val lower = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val higher = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        operations.discoveryResult = RepositoryResult.Success(
            discovery(
                SnapshotCandidateResult("Snapshots/b.zip", validatedSnapshot(1_800_000_000_000, higher)),
                SnapshotCandidateResult("Snapshots/a.zip", validatedSnapshot(1_800_000_000_000, lower)),
            ),
        )
        assertEquals(
            BackupSnapshotStatus.Latest(1_800_000_000_000, hasUnusable = false),
            coordinator.inspect(),
        )
    }

    // 4. A valid snapshot next to an unusable candidate: show the valid one and say so.
    @Test
    fun validSnapshotNextToUnusableCandidateIsShownWithAWarning() = runBlocking {
        operations.discoveryResult = RepositoryResult.Success(
            discovery(validCandidate(1_800_000_000_000), unusableCandidate()),
        )
        assertEquals(
            BackupSnapshotStatus.Latest(1_800_000_000_000, hasUnusable = true),
            coordinator.inspect(),
        )
    }

    // 5. Only unusable candidates: no last-backup claim at all.
    @Test
    fun onlyUnusableCandidatesMakeNoLastBackupClaim() = runBlocking {
        operations.discoveryResult = RepositoryResult.Success(
            discovery(unusableCandidate(), unusableCandidate("Snapshots/other-damaged.zip")),
        )
        assertEquals(BackupSnapshotStatus.Unusable, coordinator.inspect())
    }

    @Test
    fun unreadableSnapshotsAreReportedAsAnAccessProblem() = runBlocking {
        operations.discoveryResult = RepositoryResult.Failure(RepositoryError.PERMISSION_LOST)
        assertEquals(BackupSnapshotStatus.Failed(BackupAccessProblem.PERMISSION), coordinator.inspect())
    }

    // 6. Successful creation: committed and verified, then rediscovered.
    @Test
    fun successfulCreationReportsTheNewLatestFromRediscovery() = runBlocking {
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.onCreate = {
            operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(2_000_000_000_000)))
        }

        val outcome = coordinator.create()

        assertEquals(
            BackupCreateOutcome.Created(
                snapshots = BackupSnapshotStatus.Latest(2_000_000_000_000, hasUnusable = false),
                committedAtEpochMs = 2_000_000_000_000,
            ),
            outcome,
        )
        assertEquals(1, operations.createCalls)
        assertEquals(1, operations.summaryCalls)
    }

    @Test
    fun anEmptyPublishedSummaryAfterSuccessMakesNoLastBackupClaim() = runBlocking {
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.onCreate = { operations.discoveryResult = RepositoryResult.Success(discovery()) }

        val outcome = coordinator.create() as BackupCreateOutcome.Created

        assertEquals(2_000_000_000_000, outcome.committedAtEpochMs)
        assertEquals(BackupSnapshotStatus.None, outcome.snapshots)
        assertEquals(1, operations.createCalls)
        assertEquals(1, operations.summaryCalls)
    }

    // A read that fails or finds only unusable copies after a successful creation is reported as it
    // is: the screen must never hide a problem or an access loss behind a created backup.
    @Test
    fun aFailingRepositoryReadAfterASuccessfulCreationIsNotMasked() = runBlocking {
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.onCreate = {
            operations.discoveryResult = RepositoryResult.Failure(RepositoryError.PERMISSION_LOST)
        }

        val outcome = coordinator.create() as BackupCreateOutcome.Created

        assertEquals(2_000_000_000_000, outcome.committedAtEpochMs)
        assertEquals(BackupSnapshotStatus.Failed(BackupAccessProblem.PERMISSION), outcome.snapshots)
    }

    @Test
    fun unusableCandidatesAfterASuccessfulCreationAreNotHidden() = runBlocking {
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.onCreate = {
            operations.discoveryResult = RepositoryResult.Success(discovery(unusableCandidate()))
        }

        val outcome = coordinator.create() as BackupCreateOutcome.Created

        assertEquals(BackupSnapshotStatus.Unusable, outcome.snapshots)
    }

    // 7. A failed creation never looks successful, and the button works again.
    @Test
    fun failedCreationIsReportedAndTheNextAttemptCanRun() = runBlocking {
        operations.createResults.addLast(RepositoryResult.Failure(RepositoryError.WRITE_FAILED))
        operations.createResults.addLast(committed(2_000_000_000_000))

        assertEquals(BackupCreateOutcome.Failed(BackupAccessProblem.PROVIDER), coordinator.create())

        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(2_000_000_000_000)))
        assertTrue(coordinator.create() is BackupCreateOutcome.Created)
        assertEquals(2, operations.createCalls)
    }

    // 8. Not enough space.
    @Test
    fun insufficientCapacityIsReportedAsASpaceProblem() = runBlocking {
        operations.createResults.addLast(RepositoryResult.Failure(RepositoryError.CAPACITY_INSUFFICIENT))
        assertEquals(BackupCreateOutcome.Failed(BackupAccessProblem.CAPACITY), coordinator.create())
    }

    // 9. The research state could not be captured consistently.
    @Test
    fun inconsistentResearchStateIsReportedAsALogicalDataProblem() = runBlocking {
        operations.createResults.addLast(RepositoryResult.Failure(RepositoryError.LOGICAL_STATE_INCONSISTENT))
        assertEquals(BackupCreateOutcome.Failed(BackupAccessProblem.LOGICAL_DATA), coordinator.create())
    }

    // 11. A repository that no longer matches fails closed and never reports a created backup.
    @Test
    fun identityMismatchFailsClosedWithoutCreatingAnything() = runBlocking {
        operations.createResults.addLast(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH))
        assertEquals(BackupCreateOutcome.Failed(BackupAccessProblem.IDENTITY_MISMATCH), coordinator.create())
        assertTrue(BackupAccessProblem.IDENTITY_MISMATCH.recoverableByGrant.not())
    }

    // 12. Two rapid requests must not create two snapshots.
    @Test
    fun secondRequestWhileCreatingPublishesNothing() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.onCreate = { entered.complete(Unit); release.await() }
        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(2_000_000_000_000)))

        val first = CompletableDeferred<BackupCreateOutcome>()
        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch { first.complete(coordinator.create()) }
        entered.await()

        assertEquals(BackupCreateOutcome.AlreadyRunning, coordinator.create())

        release.complete(Unit)
        job.join()
        assertTrue(first.await() is BackupCreateOutcome.Created)
        assertEquals("exactly one creation may reach the service", 1, operations.createCalls)
    }

    // 13. Cancellation never produces a success.
    @Test
    fun typedCancellationIsReportedAsCancelled() = runBlocking {
        operations.createResults.addLast(RepositoryResult.Failure(RepositoryError.CANCELLED))
        assertEquals(BackupCreateOutcome.Cancelled, coordinator.create())
    }

    @Test
    fun coroutineCancellationLeavesNoCreatedOutcomeAndKeepsTheButtonUsable() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        var outcome: BackupCreateOutcome? = null
        var cancelled = false
        operations.onCreate = { entered.complete(Unit); hold.await() }

        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            try {
                outcome = coordinator.create()
            } catch (cancelledException: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw cancelledException
            }
        }
        entered.await()
        job.cancelAndJoin()

        assertTrue("the operation was cancelled", cancelled)
        assertNull("cancellation must never look like a created backup", outcome)

        // The gate was released, so a later attempt can still run.
        operations.onCreate = null
        operations.createResults.addLast(committed(2_000_000_000_000))
        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(2_000_000_000_000)))
        assertTrue(coordinator.create() is BackupCreateOutcome.Created)
    }

    // 14. Nothing is remembered: every read comes from the repository itself.
    @Test
    fun everyReadReflectsTheCurrentRepositoryState() = runBlocking {
        assertEquals(BackupSnapshotStatus.None, coordinator.inspect())

        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(1_800_000_000_000)))
        assertEquals(BackupSnapshotStatus.Latest(1_800_000_000_000, false), coordinator.inspect())

        operations.discoveryResult = RepositoryResult.Success(
            discovery(validCandidate(1_800_000_000_000), validCandidate(2_100_000_000_000)),
        )
        assertEquals(BackupSnapshotStatus.Latest(2_100_000_000_000, false), coordinator.inspect())
        assertEquals(3, operations.summaryCalls)
        assertEquals("ordinary reads must not use strong discovery", 0, operations.strongDiscoverCalls)
    }

    @Test
    fun mediaFailureForwardsCountsAndDoesNotReReadOrCreateALastBackup() = runBlocking {
        operations.discoveryResult = RepositoryResult.Success(discovery(validCandidate(1_800_000_000_000)))
        operations.createBackupResults.addLast(CreateBackupResult.MediaFailed(failedCount = 1, totalCount = 3))

        assertEquals(
            BackupCreateOutcome.MediaFailed(failedCount = 1, totalCount = 3),
            coordinator.create(),
        )
        assertEquals(0, operations.summaryCalls)
        assertEquals(
            BackupSnapshotStatus.Latest(1_800_000_000_000, hasUnusable = false),
            coordinator.inspect(),
        )
    }
}
