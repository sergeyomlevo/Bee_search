package org.beesearch.app.data.backuprepository

import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.beesearch.app.data.backupsnapshot.SnapshotMediaReference
import org.junit.Assert.*
import org.junit.Test

/**
 * Read-only required-media evidence: only actual canonical repository bytes prove a required blob, and
 * every failure stays in its own narrow category.
 */
class RepositoryMediaEvidenceTest {
    private val storage = MemoryRepositoryStorage().apply { directories += "Media" }

    private fun shaOf(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun reference(bytes: ByteArray, extension: String = "jpg") =
        SnapshotMediaReference(shaOf(bytes), bytes.size.toLong(), extension)

    private fun publish(bytes: ByteArray, name: String) {
        storage.files["Media/$name"] = bytes
    }

    private fun expectFailure(
        error: RepositoryError,
        categoryPrefix: String,
        references: List<SnapshotMediaReference>,
        check: () -> Unit = {},
    ) {
        try {
            RepositoryMediaEvidence(storage).verify(references, check)
            fail("expected $error")
        } catch (e: CancellationException) {
            throw e
        } catch (e: RepositoryException) {
            assertEquals(categoryPrefix, e.detail?.category?.substringBefore(':') ?: "")
            assertEquals(error, e.error)
        }
    }

    @Test
    fun everyRequiredBlobIsVerifiedFromTheActualRepositoryBytes() {
        val first = ByteArray(32) { 1 }
        val second = ByteArray(48) { 2 }
        publish(first, "${shaOf(first)}.jpg")
        publish(second, "${shaOf(second)}.bin")

        val report = RepositoryMediaEvidence(storage).verify(listOf(reference(first), reference(second, "bin"))) {}

        assertEquals(2, report.requiredCount)
        assertEquals(2, report.verifiedCount)
    }

    @Test
    fun zeroReferencesAreAVacuouslyVerifiedSet() {
        val report = RepositoryMediaEvidence(storage).verify(emptyList()) {}

        assertEquals(0, report.requiredCount)
        assertEquals(0, report.verifiedCount)
    }

    @Test
    fun aMissingBlobIsMissingEvidence() {
        val bytes = ByteArray(16) { 4 }

        expectFailure(RepositoryError.MEDIA_EVIDENCE_MISSING, "MISSING", listOf(reference(bytes)))
    }

    @Test
    fun aDifferentSizeIsMismatchedEvidence() {
        val bytes = ByteArray(16) { 4 }
        publish(ByteArray(15) { 4 }, "${shaOf(bytes)}.jpg")

        expectFailure(RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SIZE", listOf(reference(bytes)))
    }

    @Test
    fun aDifferentBodyWithTheDeclaredSizeIsMismatchedEvidence() {
        val bytes = ByteArray(16) { 4 }
        publish(ByteArray(16) { 5 }, "${shaOf(bytes)}.jpg")

        expectFailure(RepositoryError.MEDIA_EVIDENCE_MISMATCH, "SHA", listOf(reference(bytes)))
    }

    @Test
    fun aBlobUnderAnotherCanonicalExtensionIsInconsistentEvidence() {
        val bytes = ByteArray(16) { 4 }
        publish(bytes, "${shaOf(bytes)}.mp4")

        expectFailure(RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NONCANONICAL_PATH", listOf(reference(bytes)))
    }

    @Test
    fun aSecondEntryForTheSameShaIsInconsistentEvidence() {
        val bytes = ByteArray(16) { 4 }
        publish(bytes, "${shaOf(bytes)}.jpg")
        publish(bytes, "${shaOf(bytes)}.bin")

        expectFailure(RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "AMBIGUOUS_SHA", listOf(reference(bytes)))
    }

    @Test
    fun aDirectoryWhereTheBlobBelongsIsInconsistentEvidence() {
        val bytes = ByteArray(16) { 4 }
        storage.directories += "Media/${shaOf(bytes)}.jpg"

        expectFailure(RepositoryError.MEDIA_EVIDENCE_INCONSISTENT, "NOT_REGULAR_FILE", listOf(reference(bytes)))
    }

    @Test
    fun everyReferenceIsRequiredAndTheFailureNamesTheSha() {
        val good = ByteArray(16) { 1 }
        val missing = ByteArray(16) { 9 }
        publish(good, "${shaOf(good)}.jpg")

        try {
            RepositoryMediaEvidence(storage).verify(listOf(reference(good), reference(missing))) {}
            fail("expected missing evidence")
        } catch (e: RepositoryException) {
            assertEquals(RepositoryError.MEDIA_EVIDENCE_MISSING, e.error)
            assertEquals("MISSING:${shaOf(missing)}", e.detail?.category)
        }
    }

    @Test
    fun repositoryAccessFailuresKeepTheirOwnCategory() {
        val bytes = ByteArray(16) { 4 }
        publish(bytes, "${shaOf(bytes)}.jpg")
        storage.beforeReader = { throw RepositoryException(RepositoryError.PROVIDER_FAILURE) }

        try {
            RepositoryMediaEvidence(storage).verify(listOf(reference(bytes))) {}
            fail("expected provider failure")
        } catch (e: RepositoryException) {
            assertEquals(RepositoryError.PROVIDER_FAILURE, e.error)
        }
    }

    @Test
    fun cancellationStopsTheEvidencePass() {
        val bytes = ByteArray(16) { 4 }
        publish(bytes, "${shaOf(bytes)}.jpg")

        try {
            RepositoryMediaEvidence(storage).verify(listOf(reference(bytes))) { throw CancellationException("stop") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("stop", e.message)
        }
    }
}
