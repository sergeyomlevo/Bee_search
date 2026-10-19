package org.beesearch.app.data.gpx

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class GpxOriginalPresence { PRESENT_VERIFIED, MISSING, CORRUPT, ACCESS_FAILURE, UNSAFE_OBJECT, INCOMPLETE }
internal data class GpxOperationFiles(val owned: Boolean, val stagingBytes: Long?, val finalExists: Boolean)

/**
 * Blocking app-private file store; callers supply an IO context and cancellation checkpoint.
 * No Room, URI, business IDs, READY writer or backup implementation. All instances share
 * the same reentrant process lock. A future Coordinator must also hold [serialized] across
 * its current authorization/READY transaction; callbacks are not stale state snapshots.
 * Other processes must not mutate these roots; the future recovery owner serializes them.
 */
internal class GpxOriginalStore(
    filesDir: File,
    private val checkpoint: (GpxOriginalStage) -> Unit,
    deletionCheckpoint: (String) -> Unit,
) {
    constructor(filesDir: File, checkpoint: (GpxOriginalStage) -> Unit = {}) : this(filesDir, checkpoint, {})

    private val files = GpxOriginalFiles(filesDir.canonicalFile)
    private val publication = GpxOriginalPublication(files, checkpoint)
    private val deletion = GpxOriginalDeletion(files, deletionCheckpoint)

    fun deletionProof(request: UUID, original: UUID, identity: GpxOriginalIdentity): String? =
        operation(GpxOriginalStage.CLEANUP) { deletion.capture(request, original, identity) }

    fun cleanupDeletedOriginal(request: UUID, original: UUID, identity: GpxOriginalIdentity, proof: String?, authorize: () -> Boolean) =
        operation(GpxOriginalStage.CLEANUP) {
            require(authorize()); deletion.cleanup(request, original, identity, proof)
        }

    fun copyToStaging(
        request: UUID, original: UUID, open: () -> InputStream,
        expectedCompleted: GpxOriginalIdentity? = null,
        checkCancellation: () -> Unit = {},
    ): GpxOriginalIdentity = operation(GpxOriginalStage.COPY) {
        checkCancellation()
        files.prepare()
        val dir = directory(request)
        val part = payload(request, original)
        if (files.stat(dir) != null) {
            owned(request, original)
            if (expectedCompleted == null) fail(GpxOriginalStoreError.CONFLICT, GpxOriginalStage.COPY)
            files.verify(part, expectedCompleted, checkCancellation)
            return@operation expectedCompleted
        }
        Os.mkdir(dir.path, 448)
        files.create(File(dir, "owner")).use { output ->
            output.write(ownerBytes(request, original))
            output.flush()
            files.syncFile(output)
        }
        files.syncDirectory(dir)
        files.syncDirectory(files.staging)
        var created: android.system.StructStat? = null
        try {
            val result = object : java.io.FilterInputStream(open()) {
                override fun close() {
                    try { super.close() } catch (cancelled: CancellationException) { throw cancelled
                    } catch (error: IOException) {
                        throw GpxOriginalStoreException(GpxOriginalStoreError.IO_FAILURE, GpxOriginalStage.CLOSE, cause = error)
                    }
                }
            }.use { input ->
                files.create(part).use { output ->
                    created = Os.fstat(output.fd)
                    val identity = copyGpxOriginal(input, output, checkCancellation)
                    output.flush()
                    checkCancellation()
                    checkpoint(GpxOriginalStage.COPY)
                    files.syncFile(output)
                    identity
                }
            }
            files.syncDirectory(dir)
            files.verify(part, result, checkCancellation)
            checkCancellation()
            result
        } catch (primary: Throwable) {
            // Preserve the original throwable, especially the SAME cancellation, across cleanup.
            try {
                val current = files.stat(part)
                if (created != null && current != null && GpxOriginalFiles.same(created!!, current)) files.unlink(part)
                files.syncDirectory(dir)
            } catch (cleanup: Throwable) { primary.addSuppressed(cleanup) }
            throw primary
        }
    }

    /** Each successful call opens a new verified read-only descriptor. Consumer closes it. */
    fun openStaged(request: UUID, original: UUID, expected: GpxOriginalIdentity,
                   checkCancellation: () -> Unit = {}): InputStream = operation(GpxOriginalStage.OPEN) {
        owned(request, original)
        files.verify(payload(request, original), expected, checkCancellation)
        checkCancellation()
        files.open(payload(request, original))
    }

    /** Current PARSED authorization runs inside the same lock as the file transition. */
    fun publish(
        request: UUID, original: UUID, expected: GpxOriginalIdentity,
        authorizeParsed: () -> Boolean,
        checkCancellation: () -> Unit = {},
    ): GpxOriginalIdentity = operation(GpxOriginalStage.FINAL_COPY,
        { files.stat(publication.directory(original)) != null }) {
        checkCancellation()
        if (!authorizeParsed()) fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.RESERVE)
        owned(request, original)
        val part = payload(request, original)
        files.verify(part, expected, checkCancellation)
        publication.publish(request, original, expected, part, directory(request), checkCancellation)
    }

    fun inspectFinal(original: UUID, expected: GpxOriginalIdentity,
                     checkCancellation: () -> Unit = {}): GpxOriginalPresence = serialized {
        try {
            checkCancellation()
            files.prepare()
            if (files.stat(publication.directory(original)) == null) return@serialized GpxOriginalPresence.MISSING
            publication.verifyPublished(original, expected, checkCancellation)
            GpxOriginalPresence.PRESENT_VERIFIED
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: GpxOriginalStoreException) {
            when (error.code) {
                GpxOriginalStoreError.UNSAFE_PATH_OR_FILE_TYPE, GpxOriginalStoreError.OWNERSHIP_VIOLATION -> GpxOriginalPresence.UNSAFE_OBJECT
                GpxOriginalStoreError.INCOMPLETE_PUBLICATION -> GpxOriginalPresence.INCOMPLETE
                GpxOriginalStoreError.IDENTITY_MISMATCH, GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED,
                GpxOriginalStoreError.INVALID_EMPTY_SOURCE -> GpxOriginalPresence.CORRUPT
                else -> GpxOriginalPresence.ACCESS_FAILURE
            }
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ELOOP) GpxOriginalPresence.UNSAFE_OBJECT else GpxOriginalPresence.ACCESS_FAILURE
        } catch (_: IOException) { GpxOriginalPresence.ACCESS_FAILURE }
    }

    fun inspectOperation(request: UUID, original: UUID): GpxOperationFiles = operation(GpxOriginalStage.INSPECT) {
        files.prepare()
        val dir = directory(request)
        if (files.stat(dir) == null) return@operation GpxOperationFiles(false, null, files.stat(publication.directory(original)) != null)
        owned(request, original)
        val part = payload(request, original)
        val size = if (files.stat(part) == null) null else files.open(part).use { Os.fstat(it.fd).st_size }
        GpxOperationFiles(true, size, files.stat(publication.directory(original)) != null)
    }

    /** Names are candidates, never proof of DB ownership. Future backup joins them to its inventory. */
    fun listFinalCandidates(): List<UUID> = operation(GpxOriginalStage.INSPECT) {
        files.prepare()
        val names = files.originals.list() ?: throw IOException()
        names.map { name ->
            val id = runCatching { UUID.fromString(name) }.getOrNull()
            if (id == null || name != "$id") fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.INSPECT)
            files.requireDirectory(publication.directory(id))
            id
        }.sortedBy { it.toString() }
    }

    /** Read only after the caller supplies an expected descriptor from its persistent inventory. */
    fun openFinal(original: UUID, expected: GpxOriginalIdentity, checkCancellation: () -> Unit = {}): InputStream =
        operation(GpxOriginalStage.OPEN) {
            files.prepare()
            publication.verifyPublished(original, expected, checkCancellation)
            checkCancellation()
            files.open(publication.payload(original))
        }

    /** Authorization means current abort/no-READY before publication, or durable
     * FILE_PUBLISHED ownership before discarding a published operation's witness.
     * Incomplete finals must be compensated first through cleanupAbortedOriginal. */
    fun cleanupStaging(request: UUID, original: UUID,
                       expectedPublished: GpxOriginalIdentity? = null, authorize: () -> Boolean) = operation(GpxOriginalStage.CLEANUP) {
        if (!authorize()) fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.CLEANUP)
        owned(request, original)
        val dir = directory(request)
        val part = payload(request, original)
        if (files.stat(publication.directory(original)) != null) {
            if (expectedPublished == null) fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.CLEANUP)
            publication.authorizeStagingRemoval(request, original, expectedPublished, dir)
        } else if (files.stat(File(dir, "publication")) != null) {
            fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.CLEANUP)
        }
        if (files.stat(part) != null) files.unlink(part)
        val witness = File(dir, "publication")
        if (files.stat(witness) != null) files.unlink(witness)
        files.unlink(File(dir, "owner"))
        Os.remove(dir.path)
        files.syncDirectory(files.staging)
    }

    /** Current no-READY authority and bound filesystem evidence are both mandatory. */
    fun cleanupAbortedOriginal(
        request: UUID, original: UUID, expected: GpxOriginalIdentity,
        confirmDurablePublicationOwnership: () -> Boolean = { false },
        authorizeCurrentAbortWithoutReady: () -> Boolean,
    ) = operation(GpxOriginalStage.CLEANUP) {
        if (!authorizeCurrentAbortWithoutReady()) fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.CLEANUP)
        files.prepare()
        val hasOperation = files.stat(directory(request)) != null
        if (hasOperation) owned(request, original)
        publication.cleanup(request, original, expected,
            if (hasOperation) directory(request) else null, confirmDurablePublicationOwnership)
    }

    private fun owned(request: UUID, original: UUID) {
        files.prepare()
        val dir = directory(request)
        files.requireDirectory(dir)
        val allowed = setOf("owner", "${original}.part", "publication")
        val entries = dir.list()?.toSet() ?: throw IOException()
        if (!entries.contains("owner") || !allowed.containsAll(entries)) {
            fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.OWNER)
        }
        val expected = ownerBytes(request, original)
        val actual = files.open(File(dir, "owner")).use { input ->
            val buffer = ByteArray(expected.size + 1)
            var length = 0
            while (length < buffer.size) {
                val read = input.read(buffer, length, buffer.size - length)
                if (read < 0) break
                if (read > 0) length += read
            }
            buffer.copyOf(length)
        }
        if (!actual.contentEquals(expected)) fail(GpxOriginalStoreError.OWNERSHIP_VIOLATION, GpxOriginalStage.OWNER)
    }

    private fun directory(request: UUID) = File(files.staging, request.toString())
    private fun payload(request: UUID, original: UUID) = File(directory(request), "$original.part")
    private fun ownerBytes(request: UUID, original: UUID) = "BEESEARCH-GPX-OWNER-1\n$request\n$original\n".toByteArray(Charsets.US_ASCII)

    private fun <T> operation(stage: GpxOriginalStage, mayExist: () -> Boolean = { false }, block: () -> T): T = serialized {
        try { block() } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: GpxOriginalStoreException) {
            if (!runCatching(mayExist).getOrDefault(true) || failure.finalMayExist) throw failure
            throw GpxOriginalStoreException(failure.code, failure.stage, true, failure)
        } catch (error: ErrnoException) {
            val code = when (error.errno) {
                OsConstants.ENOSPC -> GpxOriginalStoreError.STORAGE_EXHAUSTED
                OsConstants.EEXIST -> GpxOriginalStoreError.CONFLICT
                OsConstants.ELOOP -> GpxOriginalStoreError.UNSAFE_PATH_OR_FILE_TYPE
                OsConstants.EXDEV, OsConstants.EOPNOTSUPP, OsConstants.ENOSYS -> GpxOriginalStoreError.UNSUPPORTED_FILESYSTEM_OPERATION
                else -> GpxOriginalStoreError.IO_FAILURE
            }
            throw GpxOriginalStoreException(code, stage, runCatching(mayExist).getOrDefault(true), error)
        } catch (error: IOException) {
            throw GpxOriginalStoreException(GpxOriginalStoreError.IO_FAILURE, stage, runCatching(mayExist).getOrDefault(true), error)
        }
    }

    private fun fail(code: GpxOriginalStoreError, stage: GpxOriginalStage, mayExist: Boolean = false): Nothing =
        throw GpxOriginalStoreException(code, stage, mayExist)

    companion object {
        private val lock = ReentrantLock()
        fun <T> serialized(block: () -> T): T = lock.withLock(block)
    }
}
