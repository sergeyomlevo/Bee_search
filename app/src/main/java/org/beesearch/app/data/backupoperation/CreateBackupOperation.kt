package org.beesearch.app.data.backupoperation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.beesearch.app.data.backuprepository.*
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.backupsnapshot.SnapshotException
import org.beesearch.app.data.backupsnapshot.SnapshotEvidenceProfile

internal sealed interface CreateBackupResult {
    data class Created(val committed: CommittedSnapshot, val verifiedMediaCount: Int) : CreateBackupResult
    data class MediaFailed(val failedCount: Int, val totalCount: Int) : CreateBackupResult
    data class Failed(val error: RepositoryError) : CreateBackupResult
    data object AlreadyRunning : CreateBackupResult
}

/** One capture -> per-blob protection -> existing strong FULL publication. No storage algorithm here. */
internal class CreateBackupOperation(
    private val capture: BackupOperationCapture,
    private val protect: suspend (CapturedMediaState) -> MediaProtectionReport,
    private val publish: suspend (SnapshotDomainEntries) -> RepositoryResult<CommittedSnapshot>,
    private val preflight: suspend () -> RepositoryResult<Unit>,
) {
    private val gate = Mutex()

    suspend fun create(): CreateBackupResult {
        if (!gate.tryLock()) return CreateBackupResult.AlreadyRunning
        return try {
            // Encoding captured metadata and resolving private source files must not block Compose.
            withContext(Dispatchers.IO) { runCaptured() }
        } catch (e: CancellationException) { throw e }
        catch (e: SnapshotException) { CreateBackupResult.Failed(e.repositoryError()) }
        catch (e: RepositoryException) { CreateBackupResult.Failed(e.error) }
        catch (_: Exception) { CreateBackupResult.Failed(RepositoryError.PROVIDER_FAILURE) }
        finally { gate.unlock() }
    }

    private suspend fun runCaptured(): CreateBackupResult {
            when (val ready = preflight()) {
                is RepositoryResult.Failure -> return CreateBackupResult.Failed(ready.error)
                is RepositoryResult.Success -> Unit
            }
            val captured = capture.capture()
            val report = protect(captured.media)
            if (report.summary == MediaProtectionSummary.CANCELLED) {
                return CreateBackupResult.Failed(RepositoryError.CANCELLED)
            }
            if (report.summary == MediaProtectionSummary.METADATA_INCONSISTENT) {
                return CreateBackupResult.Failed(RepositoryError.LOGICAL_STATE_INCONSISTENT)
            }
            report.blocker?.let { return CreateBackupResult.Failed(it) }
            if (report.protectedCount != report.requiredCount) {
                return CreateBackupResult.MediaFailed(report.requiredCount - report.protectedCount, report.requiredCount)
            }
            return when (val result = publish(captured.entries)) {
                is RepositoryResult.Failure -> CreateBackupResult.Failed(result.error)
                is RepositoryResult.Success -> {
                    // Publication's accepted gate verified this exact required set. Never count
                    // protected sources or restore a historical count from screen summary.
                    val snapshot = result.value.snapshot
                    if (snapshot.evidenceProfile != SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED ||
                        snapshot.references != captured.entries.references) {
                        CreateBackupResult.Failed(RepositoryError.VERIFY_FAILED)
                    } else CreateBackupResult.Created(result.value, snapshot.references.size)
                }
            }
    }
}
