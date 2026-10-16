package org.beesearch.app.data.gpx

import android.system.Os
import java.io.File
import java.io.FilterOutputStream
import java.util.UUID

/** Exclusive creation and its narrowly scoped crash evidence. Called under the Store lock.
 * Private roots and serialized, protocol-following writers are required; sidecars are not
 * a security boundary against a hostile process running under this application's UID.
 */
internal class GpxOriginalPublication(
    private val files: GpxOriginalFiles,
    private val checkpoint: (GpxOriginalStage) -> Unit,
) {
    fun directory(original: UUID) = File(files.originals, original.toString())
    fun payload(original: UUID) = File(directory(original), "original.gpx")

    fun publish(request: UUID, original: UUID, expected: GpxOriginalIdentity,
                staged: File, operation: File, check: () -> Unit): GpxOriginalIdentity {
        val dir = directory(original)
        files.requireDirectory(files.originals)
        if (files.stat(dir) != null) {
            val proof = try { bound(original, expected, request) } catch (error: GpxOriginalStoreException) {
                if (error.code != GpxOriginalStoreError.OWNERSHIP_VIOLATION) throw error
                throw GpxOriginalStoreException(GpxOriginalStoreError.CONFLICT, GpxOriginalStage.RESERVE, true, error)
            }
            requireWitness(operation, proof)
            // Never reopen an existing final for writing, even an owned incomplete copy.
            files.verify(payload(original), expected, check)
        } else {
            files.checkParents(dir)
            checkpoint(GpxOriginalStage.RESERVE)
            Os.mkdir(dir.path, 448) // Exclusive O reservation; EEXIST never means adoption.
            files.syncDirectory(files.originals)
            files.create(payload(original)).use { output ->
                val directoryStat = Os.lstat(dir.path)
                val payloadStat = Os.fstat(output.fd)
                val proof = proof(request, original, expected,
                    directoryStat.st_dev, directoryStat.st_ino, payloadStat.st_dev, payloadStat.st_ino)
                writeNew(File(dir, "owner"), proof)
                writeNew(File(operation, "publication"), proof)
                files.syncDirectory(dir)
                files.syncDirectory(operation)
                checkpoint(GpxOriginalStage.PROOF)
                files.open(staged).use { input ->
                    val checkedOutput = object : FilterOutputStream(output) {
                        override fun write(bytes: ByteArray, offset: Int, count: Int) {
                            out.write(bytes, offset, count)
                            checkpoint(GpxOriginalStage.FINAL_COPY)
                        }
                    }
                    val actual = copyGpxOriginal(input, checkedOutput, check)
                    if (actual != expected) fail(GpxOriginalStoreError.IDENTITY_MISMATCH)
                }
                output.flush()
                check()
                files.syncFile(output)
            }
        }
        // Also needed after a process died with complete bytes but before sync/verification.
        files.open(payload(original)).use { Os.fsync(it.fd) }
        val proof = bound(original, expected, request)
        requireWitness(operation, proof)
        files.verify(payload(original), expected, check)
        check()
        checkpoint(GpxOriginalStage.DIRECTORY_SYNC)
        files.syncDirectory(dir)
        val verified = File(dir, "verified")
        if (files.stat(verified) == null) writeNew(verified, proof)
        else if (!readRecord(verified).contentEquals(proof)) fail()
        files.syncDirectory(dir)
        files.syncDirectory(files.originals)
        verifyPublished(original, expected, check)
        checkpoint(GpxOriginalStage.VERIFY)
        check()
        return expected
    }

    fun verifyPublished(original: UUID, expected: GpxOriginalIdentity, check: () -> Unit) {
        val dir = directory(original)
        files.requireDirectory(dir)
        if (files.stat(File(dir, "verified")) == null) {
            fail(GpxOriginalStoreError.INCOMPLETE_PUBLICATION)
        }
        val proof = bound(original, expected)
        if (!readRecord(File(dir, "verified")).contentEquals(proof)) fail()
        files.verify(payload(original), expected, check)
    }

    fun authorizeStagingRemoval(request: UUID, original: UUID, expected: GpxOriginalIdentity,
                                operation: File) {
        val proof = bound(original, expected, request)
        requireWitness(operation, proof)
        verifyPublished(original, expected) { }
    }

    fun cleanup(request: UUID, original: UUID, expected: GpxOriginalIdentity,
                operation: File?, durableOwnership: () -> Boolean) {
        val dir = directory(original)
        if (files.stat(dir) == null) return
        val proof = bound(original, expected, request)
        if (operation != null && files.stat(File(operation, "publication")) != null) {
            requireWitness(operation, proof)
        } else if (!durableOwnership()) fail()
        // The immutable record binds the created payload inode, including an owned partial.
        // Byte mismatch is not permission to delete a different, unbound inode.
        val verified = File(dir, "verified")
        if (files.stat(verified) != null && !readRecord(verified).contentEquals(proof)) fail()
        checkpoint(GpxOriginalStage.CLEANUP)
        if (files.stat(verified) != null) files.unlink(verified)
        files.unlink(payload(original))
        files.unlink(File(dir, "owner"))
        Os.remove(dir.path)
        files.syncDirectory(files.originals)
        if (operation != null) {
            val witness = File(operation, "publication")
            if (files.stat(witness) != null) {
                requireWitness(operation, proof)
                files.unlink(witness)
                files.syncDirectory(operation)
            }
        }
    }

    private fun bound(original: UUID, expected: GpxOriginalIdentity, request: UUID? = null): ByteArray {
        GpxOriginalFiles.validateDescriptor(expected)
        val dir = directory(original)
        files.requireDirectory(dir)
        val entries = dir.list()?.toSet() ?: fail()
        if (!setOf("owner", "original.gpx", "verified").containsAll(entries)) fail()
        if (!entries.containsAll(setOf("owner", "original.gpx"))) fail()
        val raw = readRecord(File(dir, "owner"))
        val recordedRequest = try {
            UUID.fromString(raw.toString(Charsets.US_ASCII).split('\n').getOrNull(1) ?: fail())
        } catch (_: IllegalArgumentException) { fail() }
        if (request != null && recordedRequest != request) fail()
        files.open(payload(original)).use { input ->
            val directoryStat = Os.lstat(dir.path)
            val payloadStat = Os.fstat(input.fd)
            if (!raw.contentEquals(proof(recordedRequest, original, expected,
                    directoryStat.st_dev, directoryStat.st_ino, payloadStat.st_dev, payloadStat.st_ino))) fail()
        }
        return raw
    }

    private fun proof(request: UUID, original: UUID, expected: GpxOriginalIdentity,
                      directoryDevice: Long, directoryInode: Long, payloadDevice: Long, payloadInode: Long) =
        ("BEESEARCH-GPX-PUBLICATION-1\n$request\n$original\n${expected.length}\n${expected.sha256}\n" +
            "$directoryDevice\n$directoryInode\n$payloadDevice\n$payloadInode\n").toByteArray(Charsets.US_ASCII)

    private fun requireWitness(operation: File, proof: ByteArray) {
        val witness = File(operation, "publication")
        if (files.stat(witness) == null || !readRecord(witness).contentEquals(proof)) fail()
    }

    private fun readRecord(file: File): ByteArray = files.open(file).use { input ->
        val bytes = ByteArray(1025)
        var size = 0
        while (size < bytes.size) {
            val count = input.read(bytes, size, bytes.size - size)
            if (count < 0) break
            if (count > 0) size += count
        }
        if (size == bytes.size) fail()
        bytes.copyOf(size)
    }

    private fun writeNew(file: File, bytes: ByteArray) = files.create(file).use {
        it.write(bytes)
        it.flush()
        files.syncFile(it)
    }

    private fun fail(code: GpxOriginalStoreError = GpxOriginalStoreError.OWNERSHIP_VIOLATION): Nothing =
        throw GpxOriginalStoreException(code, GpxOriginalStage.PROOF)
}
