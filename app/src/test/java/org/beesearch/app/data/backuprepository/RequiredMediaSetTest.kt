package org.beesearch.app.data.backuprepository

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.beesearch.app.data.backupsnapshot.SnapshotDomainCodec
import org.beesearch.app.data.backupsnapshot.SnapshotException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
private const val SHA_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

/**
 * The one required-media definition.
 *
 * These tests pin the semantics both consumers rely on: Snapshot V1 references and media protection
 * must never disagree about which blobs exist, how they deduplicate, or which canonical extension
 * they get.
 */
class RequiredMediaSetTest {
    private fun candidate(sha: String?, size: Long?, mime: String?) = MediaIdentityCandidate(sha, size, mime)

    /** Wire-shaped media row, exactly as the snapshot reader hands it to the codec. */
    private fun wireRow(sha: String?, size: Long?, mime: String?): JsonObject = buildJsonObject {
        put("sha256", sha?.let(::JsonPrimitive) ?: JsonNull)
        put("byteSize", size?.let(::JsonPrimitive) ?: JsonNull)
        put("mimeType", mime?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun List<RequiredMediaBlob>.ordered() =
        map { Triple(it.sha256, it.byteSize, it.canonicalExtension) }

    @Test
    fun objectMediaAndAttachmentsResolveThroughTheSameRule() {
        val blobs = RequiredMediaSet.resolve(
            listOf(
                candidate(SHA_A, 10, "image/jpeg"),
                candidate(SHA_B, 20, "image/jpeg"),
            ),
        )
        assertEquals(listOf(SHA_A, SHA_B), blobs.map { it.sha256 })
        assertEquals(listOf("jpg", "jpg"), blobs.map { it.canonicalExtension })
        assertEquals(listOf(10L, 20L), blobs.map { it.byteSize })
    }

    @Test
    fun mp4AndUnknownMimeResolveToTheirCanonicalExtensions() {
        assertEquals(
            listOf("mp4", "bin"),
            RequiredMediaSet.resolve(
                listOf(
                    candidate(SHA_A, 100, "video/mp4"),
                    candidate(SHA_B, 200, null),
                ),
            ).map { it.canonicalExtension },
        )
        assertEquals(
            "bin",
            RequiredMediaSet.resolve(listOf(candidate(SHA_A, 1, "application/octet-stream")))
                .single().canonicalExtension,
        )
    }

    @Test
    fun genericHintsAreOverruledByOneRecognizedType() {
        assertEquals(
            "jpg",
            RequiredMediaSet.resolve(
                listOf(candidate(SHA_A, 5, null), candidate(SHA_A, 5, "image/jpeg")),
            ).single().canonicalExtension,
        )
        assertEquals(
            "mp4",
            RequiredMediaSet.resolve(
                listOf(candidate(SHA_A, 5, "video/mp4"), candidate(SHA_A, 5, null)),
            ).single().canonicalExtension,
        )
    }

    @Test
    fun oneShaYieldsOneBlobAndKeepsEveryParticipant() {
        val blobs = RequiredMediaSet.resolve(
            listOf(
                candidate(SHA_A, 5, "image/jpeg"),
                candidate(SHA_B, 6, null),
                candidate(SHA_A, 5, null),
            ),
        )
        assertEquals(2, blobs.size)
        val first = blobs.single { it.sha256 == SHA_A }
        assertEquals(listOf(0, 2), first.participantIndexes)
        assertEquals(listOf("image/jpeg", null), first.mimeHints)
        assertEquals(5L, first.byteSize)
    }

    @Test
    fun recognizedTypeConflictIsRejected() {
        try {
            RequiredMediaSet.resolve(listOf(candidate(SHA_A, 5, "image/jpeg"), candidate(SHA_A, 5, "video/mp4")))
            fail("a jpg+mp4 conflict must be rejected")
        } catch (e: RequiredMediaSetException) {
            assertEquals(RequiredMediaSet.CONFLICTING_TYPE, e.message)
        }
    }

    @Test
    fun conflictingSizesForOneShaAreRejected() {
        try {
            RequiredMediaSet.resolve(listOf(candidate(SHA_A, 5, "image/jpeg"), candidate(SHA_A, 6, "image/jpeg")))
            fail("one SHA with two sizes must be rejected")
        } catch (e: RequiredMediaSetException) {
            assertEquals(RequiredMediaSet.CONFLICTING_SIZE, e.message)
        }
    }

    @Test
    fun incompleteIdentitiesProduceNoBlobAndSupplyNoEvidence() {
        val blobs = RequiredMediaSet.resolve(
            listOf(
                candidate(null, 5, "image/jpeg"),
                candidate(SHA_A, 0, "image/jpeg"),
                candidate(SHA_A, -1, "video/mp4"),
                candidate(SHA_A, null, "video/mp4"),
                candidate(SHA_B, 7, "image/jpeg"),
            ),
        )
        assertEquals(listOf(SHA_B), blobs.map { it.sha256 })
        assertEquals("jpg", blobs.single().canonicalExtension)
    }

    @Test
    fun resultIsSortedBySha() {
        val blobs = RequiredMediaSet.resolve(
            listOf(
                candidate(SHA_C, 3, "image/jpeg"),
                candidate(SHA_A, 1, "image/jpeg"),
                candidate(SHA_B, 2, "image/jpeg"),
            ),
        )
        assertEquals(listOf(SHA_A, SHA_B, SHA_C), blobs.map { it.sha256 })
    }

    /**
     * The parity proof: the snapshot writer and media protection are fed identical input and must
     * produce identical required sets. A second eligibility implementation would diverge here.
     */
    @Test
    fun snapshotReferencesAndRequiredSetAgreeOnIdenticalInput() {
        val input = listOf(
            Triple(SHA_A, 5L, "image/jpeg"),
            Triple(SHA_A, 5L, null),
            Triple(SHA_B, 6L, "video/mp4"),
            Triple(SHA_C, 7L, "application/octet-stream"),
            Triple(null, 9L, "image/jpeg"),
            Triple(SHA_C, 0L, null),
        )

        val fromResolver = RequiredMediaSet.resolve(input.map { candidate(it.first, it.second, it.third) }).ordered()
        val fromSnapshot = SnapshotDomainCodec.references(
            input.map { wireRow(it.first, it.second, it.third) },
        ).map { Triple(it.sha256, it.byteSize, it.canonicalExtension) }

        assertEquals(fromResolver, fromSnapshot)
        assertEquals(
            listOf(
                Triple(SHA_A, 5L, "jpg"),
                Triple(SHA_B, 6L, "mp4"),
                Triple(SHA_C, 7L, "bin"),
            ),
            fromSnapshot,
        )
    }

    @Test
    fun bothPathsRefuseTheSameIncoherentMetadata() {
        val conflictingSize = listOf(
            Triple(SHA_A, 5L, "image/jpeg"),
            Triple(SHA_A, 6L, "image/jpeg"),
        )
        try {
            RequiredMediaSet.resolve(conflictingSize.map { candidate(it.first, it.second, it.third) })
            fail("the shared definition must reject conflicting sizes")
        } catch (e: RequiredMediaSetException) {
            assertEquals(RequiredMediaSet.CONFLICTING_SIZE, e.message)
        }
        try {
            SnapshotDomainCodec.references(conflictingSize.map { wireRow(it.first, it.second, it.third) })
            fail("snapshot references must reject the same input")
        } catch (e: SnapshotException) {
            assertTrue(e.message!!.contains(RequiredMediaSet.CONFLICTING_SIZE))
        }

        val conflictingType = listOf(
            Triple(SHA_A, 5L, "image/jpeg"),
            Triple(SHA_A, 5L, "video/mp4"),
        )
        try {
            SnapshotDomainCodec.references(conflictingType.map { wireRow(it.first, it.second, it.third) })
            fail("snapshot references must reject a recognized-type conflict")
        } catch (e: SnapshotException) {
            assertTrue(e.message!!.contains(RequiredMediaSet.CONFLICTING_TYPE))
        }
    }
}
