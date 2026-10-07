package org.beesearch.app.data.backuprepository

import java.io.InputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.beesearch.app.data.backupsnapshot.*

/** Explicit maintenance API, not a backup scheduler. A single instance owns one root and lock.
 * Expected UUID is supplied by the established binding; never inferred from URI continuity.
 */
internal class RepositoryFoundation(
    private val storage: RepositoryStorage,
    private val variant: String,
    private val capacity: RepositoryCapacityPolicy = RepositoryCapacityPolicy(),
    private val maintenance: Mutex = Mutex(),
) {

    suspend fun bootstrap(): RepositoryResult<Unit> = operation { skeleton() }

    /** Explicit adoption/initialization only. Startup never calls this. */
    suspend fun initialize(): RepositoryResult<RepositoryHeader> = initialize(allowExisting = true)

    suspend fun initializeNew(): RepositoryResult<RepositoryHeader> = initialize(allowExisting = false)

    private suspend fun initialize(allowExisting: Boolean): RepositoryResult<RepositoryHeader> = operation {
        if (storage.inspect(HEADER) != null) {
            if (!allowExisting) fail(RepositoryError.AMBIGUOUS_REPOSITORY)
            val header = readHeader()
            skeleton()
            return@operation header
        }
        // Inspect ambiguity before creating any children in an uninitialized root.
        requireEmptySkeleton()
        skeleton()
        requireEmptySkeleton()
        val header = RepositoryHeader(UUID.randomUUID(), variant)
        val bytes = RepositoryHeaderCodec.encode(header)
        storage.requirePublicationCapability()
        capacity.require(storage.availableBytes(), capacity.budget(bytes.size.toLong()))
        val id = UUID.randomUUID()
        try {
            val stage = storage.createOwnedStage(id)
            storage.openTruncatedWriter(stage).use { it.write(bytes); it.flush() }
            storage.sync(stage)
            verify(stage, bytes.size.toLong(), sha(bytes))
            // Recheck ambiguity immediately before publication, excluding only our own operation.
            requireEmptySkeleton(id)
            capacity.require(storage.availableBytes(), capacity.slack(bytes.size.toLong()))
            publish(stage, HEADER, bytes.size.toLong(), sha(bytes))
            if (readHeader() != header) fail(RepositoryError.ROOT_IDENTITY_MISMATCH)
            header
        } finally { cleanOwned(id, header.repositoryId, allowAbsentHeader = true) }
    }

    suspend fun open(expectedRepositoryId: UUID): RepositoryResult<RepositoryHeader> = operation {
        identity(expectedRepositoryId)
    }

    /** Read-only header probe for explicit adoption; never initializes or repairs directories. */
    suspend fun inspectHeader(): RepositoryResult<RepositoryHeader> = operation { readHeader() }

    suspend fun inspectRoot(): RepositoryResult<Unit> = operation {
        val root = storage.inspect("") ?: fail(RepositoryError.NOT_FOUND)
        if (!root.isDirectory) fail(RepositoryError.DIRECTORY_CONFLICT)
    }

    suspend fun createMetadataSnapshot(expectedRepositoryId: UUID, workspace: File,
        capture: suspend () -> SnapshotDomainEntries, archive: SnapshotArchive = SnapshotArchive(),
        createdAtEpochMs: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false },
    ): RepositoryResult<CommittedSnapshot> = createSnapshot(expectedRepositoryId, workspace, capture, archive,
        createdAtEpochMs, SnapshotEvidenceProfile.METADATA_ONLY, cancelled)

    /**
     * The local full-evidence profile (wire contract §7.1).
     *
     * The required media set is taken from the SAME immutable capture that is serialized, and every
     * blob of it must be strongly present in this same bound repository immediately before
     * publication. A blob that is missing, differently sized, differently valued or not at its
     * canonical path fails the whole creation: nothing is published, nothing is ingested, and there is
     * no fallback to the metadata-only profile.
     */
    suspend fun createFullSnapshot(expectedRepositoryId: UUID, workspace: File,
        capture: suspend () -> SnapshotDomainEntries, archive: SnapshotArchive = SnapshotArchive(),
        createdAtEpochMs: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false },
    ): RepositoryResult<CommittedSnapshot> = createSnapshot(expectedRepositoryId, workspace, capture, archive,
        createdAtEpochMs, SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, cancelled)

    private suspend fun createSnapshot(expectedRepositoryId: UUID, workspace: File,
        capture: suspend () -> SnapshotDomainEntries, archive: SnapshotArchive,
        createdAtEpochMs: Long, evidenceProfile: SnapshotEvidenceProfile, cancelled: () -> Boolean,
    ): RepositoryResult<CommittedSnapshot> = operation {
        val context = coroutineContext
        RepositorySnapshots(storage, capacity, { identity(expectedRepositoryId) },
            { cleanOwned(it, expectedRepositoryId) }, workspace, archive).create(capture, createdAtEpochMs,
            check = {
                context.ensureActive()
                if (cancelled()) fail(RepositoryError.CANCELLED)
            },
            evidenceProfile = evidenceProfile)
    }

    suspend fun discoverSnapshots(expectedRepositoryId: UUID, workspace: File,
        archive: SnapshotArchive = SnapshotArchive(),
    ): RepositoryResult<SnapshotDiscovery> = operation {
        val context = coroutineContext
        RepositorySnapshots(storage, capacity, { identity(expectedRepositoryId) },
            { cleanOwned(it, expectedRepositoryId) }, workspace, archive).discover { context.ensureActive() }
    }


    suspend fun ingest(
        expectedRepositoryId: UUID,
        source: BlobSource,
        cancelled: () -> Boolean = { false },
    ): RepositoryResult<CommittedBlob> = operation {
        fun checkCancel() { if (cancelled()) fail(RepositoryError.CANCELLED) }
        identity(expectedRepositoryId)
        checkCancel()
        source.checkUnchanged()
        val size = source.byteSize
        if (size < 0) fail(RepositoryError.SOURCE_CHANGED)
        val context = coroutineContext
        val first = sourceDigest(source, size) { context.ensureActive(); checkCancel() }
        source.expectedSha256?.let {
            if (!SHA.matches(it) || it != first) fail(RepositoryError.SOURCE_CHANGED)
        }
        source.checkUnchanged()
        existing(first, size)?.let { return@operation it }
        val extension = CanonicalExtension.resolve(source.mimeHints)
        val final = "Media/$first.$extension"
        storage.requirePublicationCapability()
        capacity.require(storage.availableBytes(), capacity.budget(size))
        identity(expectedRepositoryId)
        val operationId = UUID.randomUUID()
        try {
            val stage = storage.createOwnedStage(operationId)
            var written = 0L
            var lastCapacityCheck = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            storage.openTruncatedWriter(stage).use { output ->
                openSource(source).use { input ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        context.ensureActive(); checkCancel()
                        if (written - lastCapacityCheck >= CAPACITY_INTERVAL) {
                            capacity.require(storage.availableBytes(), capacity.remainingBudget(size, written))
                            lastCapacityCheck = written
                        }
                        val count = sourceRead(input, buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (count.toLong() > size - written) fail(RepositoryError.SOURCE_CHANGED)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        written += count
                    }
                    if (written != size || hex(digest.digest()) != first) fail(RepositoryError.SOURCE_CHANGED)
                }
                output.flush()
            }
            source.checkUnchanged()
            // Second source pass also catches same-size mutation with unchanged/coarse timestamps.
            if (sourceDigest(source, size) { context.ensureActive(); checkCancel() } != first) {
                fail(RepositoryError.SOURCE_CHANGED)
            }
            source.checkUnchanged()
            checkCancel(); context.ensureActive()
            storage.sync(stage)
            verify(stage, size, first) { context.ensureActive(); checkCancel() }
            identity(expectedRepositoryId)
            storage.requirePublicationCapability()
            capacity.require(storage.availableBytes(), capacity.slack(size))
            // Serialized local operations do not race publication. External writers are unsupported.
            existing(first, size)?.let { return@operation it }
            publish(stage, final, size, first,
                beforeMove = { identity(expectedRepositoryId) },
                check = { context.ensureActive(); checkCancel() })
            identity(expectedRepositoryId)
            CommittedBlob(first, size, extension, false)
        } finally { cleanOwned(operationId, expectedRepositoryId) }
    }

    private fun skeleton() {
        storage.ensureDirectory("")
        DIRECTORIES.forEach(storage::ensureDirectory)
    }

    private fun requireEmptySkeleton(ownedOperation: UUID? = null) {
        if (storage.list("").any { !it.isDirectory || it.path !in DIRECTORIES }) {
            fail(RepositoryError.AMBIGUOUS_REPOSITORY)
        }
        DIRECTORIES.forEach { directory ->
            if (storage.inspect(directory) != null && storage.list(directory).any { it.path != "Staging/$ownedOperation" }) {
                fail(RepositoryError.AMBIGUOUS_REPOSITORY)
            }
        }
    }

    private fun readHeader(): RepositoryHeader {
        val entry = storage.inspect(HEADER) ?: fail(RepositoryError.NOT_FOUND)
        if (entry.isDirectory || entry.byteSize !in 1..4096) fail(RepositoryError.INVALID_HEADER)
        val bytes = storage.openReader(HEADER).use { input ->
            val buffer = ByteArray(4097)
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                count += n
            }
            if (count != entry.byteSize.toInt()) fail(RepositoryError.INVALID_HEADER)
            buffer.copyOf(count)
        }
        val header = RepositoryHeaderCodec.decode(bytes)
        if (header.variant != variant) fail(RepositoryError.ROOT_IDENTITY_MISMATCH)
        return header
    }

    private fun identity(expected: UUID): RepositoryHeader = readHeader().also {
        if (it.repositoryId != expected) fail(RepositoryError.ROOT_IDENTITY_MISMATCH)
        DIRECTORIES.forEach { path ->
            if (storage.inspect(path)?.isDirectory != true) fail(RepositoryError.DIRECTORY_CONFLICT)
        }
    }

    private fun existing(hash: String, size: Long): CommittedBlob? {
        val entries = storage.list("Media").filter {
            it.path.substringAfterLast('/').substringBefore('.').equals(hash, ignoreCase = true)
        }
        if (entries.isEmpty()) return null
        if (entries.size != 1) fail(RepositoryError.METADATA_INCONSISTENCY)
        val entry = entries.single()
        val extension = entry.path.substringAfterLast('.')
        if (entry.path != "Media/$hash.$extension" || extension !in setOf("jpg", "mp4", "bin")) {
            fail(RepositoryError.METADATA_INCONSISTENCY)
        }
        verify(entry.path, size, hash)
        return CommittedBlob(hash, size, extension, true)
    }

    private fun publish(stage: String, final: String, size: Long, hash: String,
        beforeMove: () -> Unit = {}, check: () -> Unit = {}) {
        // No listing, hashing, capacity query or suspension between this check and move.
        // Provider mutation inside its own move is not an atomic conditional operation.
        beforeMove()
        try { storage.moveOwnedStage(stage, final) } catch (e: RepositoryException) {
            // A provider may move successfully and fail its response. Never infer success from existence.
            if (storage.inspect(final) == null) throw e
            verify(final, size, hash, check)
            return
        }
        verify(final, size, hash, check)
    }

    private fun verify(path: String, size: Long, hash: String, check: () -> Unit = {}) {
        val entry = storage.inspect(path) ?: fail(RepositoryError.VERIFY_FAILED)
        if (entry.isDirectory || entry.byteSize != size) fail(RepositoryError.VERIFY_FAILED)
        val actual = storage.openReader(path).use { digest(it, size, RepositoryError.VERIFY_FAILED, check) }
        if (actual != hash || storage.inspect(path)?.byteSize != size) fail(RepositoryError.VERIFY_FAILED)
    }

    private fun sourceDigest(source: BlobSource, size: Long, check: () -> Unit): String = try {
        source.open().use { digest(it, size, RepositoryError.SOURCE_CHANGED, check) }
    } catch (e: RepositoryException) { throw e } catch (e: CancellationException) { throw e } catch (e: Exception) {
        throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
    }

    private fun digest(input: InputStream, size: Long, mismatch: RepositoryError, check: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var count = 0L
        while (true) {
            check()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            if (n.toLong() > size - count) fail(mismatch)
            count += n; digest.update(buffer, 0, n)
        }
        if (count != size) fail(mismatch)
        return hex(digest.digest())
    }

    private fun sourceRead(input: InputStream, buffer: ByteArray): Int = try { input.read(buffer) }
    catch (e: Exception) { throw RepositoryException(RepositoryError.SOURCE_CHANGED, e) }

    private fun openSource(source: BlobSource): InputStream = try { source.open() }
    catch (e: RepositoryException) { throw e }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw RepositoryException(RepositoryError.SOURCE_CHANGED, e) }

    private fun cleanOwned(id: UUID, expectedRepositoryId: UUID, allowAbsentHeader: Boolean = false) {
        // Best effort on error/cancellation; a failed delete leaves non-committed owned staging.
        try {
            val header = storage.inspect(HEADER)
            if (header == null && !allowAbsentHeader) return
            if (header != null && readHeader().repositoryId != expectedRepositoryId) return
            storage.deleteOwnedStage(id)
        } catch (_: RepositoryException) { /* preserved for review */ }
    }

    private suspend fun <T> operation(block: suspend () -> T): RepositoryResult<T> = withContext(Dispatchers.IO) {
        maintenance.withLock {
            try { RepositoryResult.Success(block()) }
            catch (e: RepositoryException) { RepositoryResult.Failure(e.error, e.detail) }
            catch (e: SnapshotException) { RepositoryResult.Failure(e.repositoryError(), e.repositoryDetail()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { RepositoryResult.Failure(RepositoryError.PROVIDER_FAILURE) }
        }
    }

    private fun fail(error: RepositoryError): Nothing = throw RepositoryException(error)
    private fun sha(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private const val HEADER = "repository.json"
        private val DIRECTORIES = listOf("Media", "Snapshots", "Staging")
        private val SHA = Regex("[0-9a-f]{64}")
        private const val BUFFER = 128 * 1024
        private const val CAPACITY_INTERVAL = 4L * 1024 * 1024
    }
}
