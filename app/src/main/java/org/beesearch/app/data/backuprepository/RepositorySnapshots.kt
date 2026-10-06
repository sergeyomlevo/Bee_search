package org.beesearch.app.data.backuprepository

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.beesearch.app.data.backupsnapshot.*

internal data class CommittedSnapshot(val path: String, val snapshot: ValidatedSnapshot)
internal data class SnapshotCandidateResult(
    val path: String,
    val snapshot: ValidatedSnapshot? = null,
    val error: RepositoryError? = null,
    val detail: RepositoryFailureDetail? = null,
)
internal data class SnapshotDiscovery(val candidates: List<SnapshotCandidateResult>) {
    val latest: ValidatedSnapshot? get() = candidates.mapNotNull { it.snapshot }
        .maxWithOrNull(compareBy({ it.identity.createdAtEpochMs }, { it.identity.snapshotId.toString() }))
}

/** Called only under Foundation's maintenance lock and BoundRepository's binding gate. */
internal class RepositorySnapshots(
    private val storage: RepositoryStorage,
    private val capacity: RepositoryCapacityPolicy,
    private val identityCheck: () -> RepositoryHeader,
    private val cleanOwned: (UUID) -> Unit,
    private val workspace: File,
    private val archive: SnapshotArchive,
    private val limits: SnapshotLimits = SnapshotLimits(),
) {
    suspend fun create(capture: suspend () -> SnapshotDomainEntries, now: Long,
        check: () -> Unit): CommittedSnapshot {
        val header = identityCheck()
        storage.requirePublicationCapability()
        // Bounds include fixed ZIP, public Staging, readback ZIP, and validation spools.
        // Conservatively reserve all peak temporary bytes before any filesystem growth.
        capacity.require(storage.availableBytes(), peakBudget())
        check()
        val entries = capture()
        val operationId = UUID.randomUUID()
        val scratch = ownedWorkspace()
        try {
            var lastCapacityCheck = System.nanoTime()
            val guardedCheck = {
                check()
                val current = System.nanoTime()
                if (current - lastCapacityCheck >= 500_000_000L) {
                    capacity.require(storage.availableBytes(), peakBudget())
                    lastCapacityCheck = current
                }
            }
            val identity = SnapshotIdentity(UUID.randomUUID(), header.repositoryId, header.variant, now)
            val candidate = File(scratch, "candidate.zip")
            val built = archive.build(candidate, identity, entries, guardedCheck)
            check()
            identityCheck()
            capacity.require(storage.availableBytes(), peakBudget())
            val stage = storage.createOwnedStage(operationId)
            storage.openTruncatedWriter(stage).use { output ->
                candidate.inputStream().use { input -> copy(input, output, candidate.length(), guardedCheck) }
                output.flush()
            }
            storage.sync(stage)
            val staged = fixedReadback(stage, scratch, "stage.zip", guardedCheck)
            archive.validate(staged, header.repositoryId, header.variant, identity.snapshotId,
                built.wholeSha256, guardedCheck)
            staged.delete()
            val target = "Snapshots/snapshot-${identity.snapshotId}-${built.wholeSha256}.zip"
            if (storage.inspect(target) != null) throw RepositoryException(RepositoryError.SNAPSHOT_ID_CONFLICT)
            storage.requirePublicationCapability()
            capacity.require(storage.availableBytes(), capacity.slack(candidate.length()))
            check()
            // No provider lookups, hashing, capacity queries or suspension after this check.
            identityCheck()
            try { storage.moveOwnedStage(stage, target) } catch (e: RepositoryException) {
                if (storage.inspect(target) == null) throw e
                // A failed response is not success; final full reader still must pass below.
            }
            val finalFile = fixedReadback(target, scratch, "final.zip", guardedCheck)
            val verified = archive.validate(finalFile, header.repositoryId, header.variant,
                identity.snapshotId, built.wholeSha256, guardedCheck)
            identityCheck()
            return CommittedSnapshot(target, verified)
        } finally {
            cleanOwned(operationId)
            scratch.deleteRecursively() // exclusively generated private scratch, never repository content
        }
    }

    fun discover(check: () -> Unit): SnapshotDiscovery {
        val header = identityCheck()
        val files = storage.list("Snapshots")
        val candidates = files.map { entry ->
            check()
            val match = NAME.matchEntire(entry.path.substringAfterLast('/'))
            if (entry.isDirectory || match == null) return@map SnapshotCandidateResult(entry.path,
                error = RepositoryError.INVALID_SNAPSHOT)
            val scratch = ownedWorkspace()
            try {
                capacity.require(storage.availableBytes(), peakBudget())
                val file = fixedReadback(entry.path, scratch, "discovery.zip", check)
                val result = archive.validate(file, header.repositoryId, header.variant,
                    UUID.fromString(match.groupValues[1]), match.groupValues[2], check)
                identityCheck()
                SnapshotCandidateResult(entry.path, result)
            } catch (e: SnapshotException) {
                SnapshotCandidateResult(entry.path, error = e.repositoryError(), detail = e.repositoryDetail())
            } catch (e: RepositoryException) {
                SnapshotCandidateResult(entry.path, error = e.error, detail = e.detail)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { SnapshotCandidateResult(entry.path, error = RepositoryError.INVALID_SNAPSHOT) }
            finally { scratch.deleteRecursively() }
        }
        // UUID ambiguity is not hidden by picking one valid physical file.
        val conflicts = files.mapNotNull { NAME.matchEntire(it.path.substringAfterLast('/'))?.groupValues?.get(1) }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return SnapshotDiscovery(candidates.map {
            val id = NAME.matchEntire(it.path.substringAfterLast('/'))?.groupValues?.get(1)
            if (id in conflicts) it.copy(snapshot = null, error = RepositoryError.SNAPSHOT_ID_CONFLICT) else it
        })
    }

    private fun ownedWorkspace(): File {
        workspace.mkdirs()
        if (!workspace.isDirectory) throw RepositoryException(RepositoryError.WRITE_FAILED)
        return Files.createTempDirectory(workspace.toPath(), "repository-snapshot-").toFile()
    }

    private fun peakBudget(): Long = try {
        capacity.budget(Math.addExact(Math.multiplyExact(limits.zipBytes, 3L), limits.totalBytes))
    } catch (e: ArithmeticException) { throw RepositoryException(RepositoryError.CAPACITY_UNKNOWN, e) }

    private fun fixedReadback(path: String, scratch: File, name: String, check: () -> Unit): File {
        val entry = storage.inspect(path) ?: throw RepositoryException(RepositoryError.VERIFY_FAILED)
        if (entry.isDirectory || entry.byteSize < 0 || entry.byteSize > limits.zipBytes) {
            throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, path, entry.byteSize, limits.zipBytes)
        }
        val target = File(scratch, name)
        storage.openReader(path).use { input -> target.outputStream().use { copy(input, it, entry.byteSize, check) } }
        if (storage.inspect(path)?.byteSize != entry.byteSize) throw RepositoryException(RepositoryError.VERIFY_FAILED)
        return target
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream, expected: Long, check: () -> Unit) {
        val buffer = ByteArray(128 * 1024)
        var count = 0L
        var lastCheck = 0L
        while (true) {
            check()
            if (count - lastCheck >= 4L * 1024 * 1024) {
                capacity.require(storage.availableBytes(), peakBudget())
                lastCheck = count
            }
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            if (n.toLong() > expected - count) throw RepositoryException(RepositoryError.VERIFY_FAILED)
            output.write(buffer, 0, n); count += n
        }
        if (count != expected) throw RepositoryException(RepositoryError.VERIFY_FAILED)
    }

    companion object {
        private val NAME = Regex("snapshot-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-([0-9a-f]{64})\\.zip")
    }
}

internal fun SnapshotException.repositoryError(): RepositoryError = when (error) {
    SnapshotError.LOGICAL_STATE_INCONSISTENT -> RepositoryError.LOGICAL_STATE_INCONSISTENT
    SnapshotError.SNAPSHOT_LIMIT_EXCEEDED -> RepositoryError.SNAPSHOT_LIMIT_EXCEEDED
    SnapshotError.SNAPSHOT_ID_CONFLICT -> RepositoryError.SNAPSHOT_ID_CONFLICT
    SnapshotError.READ_FAILED -> RepositoryError.VERIFY_FAILED
    else -> RepositoryError.INVALID_SNAPSHOT
}
internal fun SnapshotException.repositoryDetail() = RepositoryFailureDetail(category, observed, limit)
