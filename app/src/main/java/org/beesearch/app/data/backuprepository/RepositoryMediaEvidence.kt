package org.beesearch.app.data.backuprepository

import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.beesearch.app.data.backupsnapshot.SnapshotMediaReference

/** What one read-only evidence pass proved about a captured required media set. */
internal data class RepositoryMediaEvidenceReport(val requiredCount: Int, val verifiedCount: Int)

/**
 * Read-only strong verification of one captured required media set against the bound repository.
 *
 * This is the S6B evidence gate. It never ingests, never writes, never deletes and never trusts a
 * filename, a provider-reported size or a metadata SHA: every required blob is located by its declared
 * canonical identity, and its actual repository bytes are hashed with a bounded buffer.
 *
 * It reuses Repository V1's canonical placement rules (exactly one entry per SHA, canonical
 * `Media/<sha256>.<canonicalExtension>`, regular readable entry, exact byte size, actual SHA-256), so
 * S6A reuse and S6B evidence cannot disagree about what a valid canonical blob is.
 *
 * Failures stay narrow and typed, because a caller must distinguish a media-evidence failure from an
 * ordinary snapshot/container failure:
 *
 * - [RepositoryError.MEDIA_EVIDENCE_MISSING] — the canonical blob is absent;
 * - [RepositoryError.MEDIA_EVIDENCE_MISMATCH] — present, but size or actual bytes differ, or it cannot
 *   be read;
 * - [RepositoryError.MEDIA_EVIDENCE_INCONSISTENT] — the canonical identity itself is inconsistent (a
 *   second entry for the same SHA, a non-canonical name or extension, a directory);
 * - repository identity/access failures keep their own existing errors, and cancellation propagates.
 */
internal class RepositoryMediaEvidence(private val storage: RepositoryStorage) {
    fun verify(references: List<SnapshotMediaReference>, check: () -> Unit): RepositoryMediaEvidenceReport {
        val bySha = listMedia().groupBy { it.path.substringAfterLast('/').substringBefore('.').lowercase() }
        for (reference in references) {
            check()
            verifyOne(reference, bySha[reference.sha256.lowercase()].orEmpty(), check)
        }
        return RepositoryMediaEvidenceReport(references.size, references.size)
    }

    private fun verifyOne(reference: SnapshotMediaReference, listed: List<RepositoryEntry>, check: () -> Unit) {
        val expected = "Media/${reference.sha256}.${reference.canonicalExtension}"
        if (listed.size > 1) fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "AMBIGUOUS_SHA")
        val single = listed.singleOrNull()
        if (single == null) {
            val stray = storage.inspect(expected)
                ?: fail(reference, RepositoryError.MEDIA_EVIDENCE_MISSING, "MISSING")
            // Present but unlisted, or a directory where a blob belongs: the placement is not a blob.
            if (stray.isDirectory) fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NOT_REGULAR_FILE")
            fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "UNLISTED_ENTRY")
        }
        if (single.isDirectory) fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NOT_REGULAR_FILE")
        // A candidate under any name but the declared canonical path is a placement inconsistency even
        // when its bytes match: the declared canonical extension is part of the required identity.
        if (single.path != expected) fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NONCANONICAL_PATH")
        val entry = storage.inspect(expected)
            ?: fail(reference, RepositoryError.MEDIA_EVIDENCE_MISSING, "MISSING")
        if (entry.isDirectory) fail(reference, RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NOT_REGULAR_FILE")
        if (entry.byteSize != reference.byteSize) {
            fail(reference, RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SIZE", observed = entry.byteSize,
                limit = reference.byteSize)
        }
        val actual = try {
            storage.openReader(expected).use { digest(reference, it, reference.byteSize, check) }
        } catch (e: CancellationException) { throw e }
        catch (e: RepositoryException) { throw e }
        catch (e: Exception) { throw RepositoryException(RepositoryError.MEDIA_EVIDENCE_MISMATCH, e) }
        if (actual != reference.sha256) fail(reference, RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SHA")
        // A blob that changed size while it was being read is not evidence.
        if (storage.inspect(expected)?.byteSize != reference.byteSize) {
            fail(reference, RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SIZE_CHANGED")
        }
    }

    private fun listMedia(): List<RepositoryEntry> = try {
        storage.list("Media")
    } catch (e: CancellationException) { throw e }
    catch (e: RepositoryException) { throw e }
    catch (e: Exception) { throw RepositoryException(RepositoryError.PROVIDER_FAILURE, e) }

    /** Bounded-memory digest over the actual repository bytes; refuses a longer entry than declared. */
    private fun digest(reference: SnapshotMediaReference, input: InputStream, size: Long, check: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            check()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            count += n
            if (count > size) fail(reference, RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SIZE")
            digest.update(buffer, 0, n)
        }
        if (count != size) fail(reference, RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SIZE")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fail(
        reference: SnapshotMediaReference,
        error: RepositoryError,
        category: String,
        observed: Long? = null,
        limit: Long? = null,
    ): Nothing = throw RepositoryException(
        error,
        detail = RepositoryFailureDetail("$category:${reference.sha256}", observed, limit),
    )
}
