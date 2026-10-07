package pcsnapshot

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Repository-aware fixtures.
 *
 * The non-empty graph fixture is reused as-is, but its media identities are replaced with the real
 * SHA-256 of the payloads the repository will hold: repository-aware verification compares actual
 * bytes, so a placeholder hash could never pass.
 */
private object RepositoryFixture {
    const val variant = "Dev"

    fun payloads(): Pair<ByteArray, ByteArray> =
        ByteArray(10) { it.toByte() } to ByteArray(20) { (it * 2).toByte() }

    fun header(repositoryId: String = Fixture.repositoryId, variant: String = this.variant): ByteArray =
        ("{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1," +
            "\"repositoryId\":\"$repositoryId\",\"variant\":\"$variant\"}").toByteArray()

    /** A complete snapshot whose two required blobs describe the two payloads above. */
    fun snapshot(extraReferences: List<String> = emptyList(), full: Boolean = false): ByteArray {
        val (objectBytes, attachmentBytes) = payloads()
        val shaObject = Fixture.sha256(objectBytes)
        val shaAttachment = Fixture.sha256(attachmentBytes)
        val files = NonEmptyFixture.entries()

        files["data/physical-object-media.jsonl"] = (
            "{\"id\":\"${NonEmptyFixture.media}\",\"physicalObjectId\":\"${NonEmptyFixture.apiary}\"," +
                "\"type\":\"IMAGE\",\"relativePath\":\"photos/a.jpg\",\"originalFileName\":\"a.jpg\"," +
                "\"mimeType\":\"image/jpeg\",\"byteSize\":${objectBytes.size},\"sha256\":\"$shaObject\",\"createdAt\":1000}\n" +
                "{\"id\":\"${NonEmptyFixture.zeroMedia}\",\"physicalObjectId\":\"${NonEmptyFixture.logHive}\"," +
                "\"type\":\"VIDEO\",\"relativePath\":\"videos/unknown\",\"originalFileName\":null," +
                "\"mimeType\":null,\"byteSize\":0,\"sha256\":\"$shaObject\",\"createdAt\":1000}\n"
            ).toByteArray()

        files["data/observation-point-attachments.jsonl"] = (
            "{\"id\":\"${NonEmptyFixture.attachment}\",\"observationPointId\":\"${NonEmptyFixture.point}\"," +
                "\"type\":\"PHOTO\",\"relativePath\":\"notes/a.jpg\",\"originalFileName\":\"a.jpg\"," +
                "\"mimeType\":\"image/jpeg\",\"byteSize\":${attachmentBytes.size},\"sha256\":\"$shaAttachment\",\"createdAt\":3000}\n" +
                "{\"id\":\"${NonEmptyFixture.incompleteAttachment}\",\"observationPointId\":\"${NonEmptyFixture.point}\"," +
                "\"type\":\"PHOTO\",\"relativePath\":\"notes/missing\",\"originalFileName\":null,\"mimeType\":null,\"createdAt\":3000}\n"
            ).toByteArray()

        files["references/media-blobs.jsonl"] = (
            "{\"byteSize\":${objectBytes.size},\"canonicalExtension\":\"jpg\",\"sha256\":\"$shaObject\"}\n" +
                "{\"byteSize\":${attachmentBytes.size},\"canonicalExtension\":\"jpg\",\"sha256\":\"$shaAttachment\"}\n" +
                extraReferences.joinToString("")
            ).toByteArray()

        files["manifest.json"] = Fixture.manifest(files)
        val built = Fixture.zip(files)
        return if (full) Fixture.fullEvidence(built) else built
    }

    fun requiredShas(): Pair<String, String> {
        val (objectBytes, attachmentBytes) = payloads()
        return Fixture.sha256(objectBytes) to Fixture.sha256(attachmentBytes)
    }
}

/**
 * Repository-aware verification: the selected snapshot's required bytes must really be in the
 * repository, and repository identity must agree with the snapshot context.
 */
class RepositoryVerifierTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun repository(
        snapshotBytes: ByteArray = RepositoryFixture.snapshot(),
        headerBytes: ByteArray = RepositoryFixture.header(),
    ): Pair<Path, Path> {
        val root = temporary.newFolder("repository").toPath()
        Files.write(root.resolve("repository.json"), headerBytes)
        Files.createDirectories(root.resolve("Media"))
        val snapshot = Fixture.canonicalCopy(root.resolve("Snapshots"), snapshotBytes)
        return root to snapshot
    }

    private fun publish(root: Path, payload: ByteArray, name: String) {
        Files.write(root.resolve("Media").resolve(name), payload)
    }

    private fun bothBlobs(root: Path) {
        val (objectBytes, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, objectBytes, "$shaObject.jpg")
        publish(root, attachmentBytes, "$shaAttachment.jpg")
    }

    private fun issueCodes(report: RepositoryVerificationReport) = report.issues.map { it.code }

    @Test
    fun validRepositoryWithEveryRequiredBlobPasses() {
        val (root, snapshot) = repository()
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals(listOf<String>(), issueCodes(report))
        assertEquals("PASS", report.verdict)
        assertEquals(Fixture.repositoryId, report.repositoryId)
        assertEquals("Dev", report.variant)
        assertEquals(2, report.requiredCount)
        assertEquals(2, report.verifiedRequired)
        assertEquals("PASS", report.snapshotVerdict)
    }

    @Test
    fun missingRequiredBlobFails() {
        val (root, snapshot) = repository()
        val (objectBytes, _) = RepositoryFixture.payloads()
        val (shaObject, _) = RepositoryFixture.requiredShas()
        publish(root, objectBytes, "$shaObject.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.MISSING))
        assertEquals(1, report.verifiedRequired)
    }

    @Test
    fun everyFailingBlobExplainsItself() {
        val (root, snapshot) = repository()

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertEquals(2, report.requiredCount)
        report.required.forEach { check ->
            assertEquals(RepositoryVerificationReport.MISSING, check.outcome)
            assertTrue("a failed check must explain itself: $check", !check.message.isNullOrBlank())
        }
    }

    /** A passing report must never carry a failure-flavoured message next to a verified outcome. */
    @Test
    fun aVerifiedBlobCarriesNoContradictoryMessage() {
        val (root, snapshot) = repository()
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("PASS", report.verdict)
        report.required.forEach { check ->
            assertEquals(RepositoryVerificationReport.VERIFIED, check.outcome)
            assertEquals(null, check.message)
        }
    }

    @Test
    fun wrongSizeFails() {
        val (root, snapshot) = repository()
        val (_, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, ByteArray(11) { 1 }, "$shaObject.jpg")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.SIZE_MISMATCH))
    }

    @Test
    fun wrongContentWithTheRightSizeFails() {
        val (root, snapshot) = repository()
        val (_, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, ByteArray(10) { 7 }, "$shaObject.jpg")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.SHA_MISMATCH))
    }

    @Test
    fun aRequiredBlobUnderAnotherCanonicalNameFails() {
        val (root, snapshot) = repository()
        val (objectBytes, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, objectBytes, "$shaObject.bin")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.MISSING))
        assertTrue(issueCodes(report).contains("DUPLICATE_SHA_ENTRY"))
    }

    @Test
    fun repositoryUuidMismatchFails() {
        val (root, snapshot) = repository(
            headerBytes = RepositoryFixture.header(repositoryId = "44444444-4444-4444-8444-444444444444"),
        )
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("REPOSITORY_UUID_MISMATCH"))
    }

    @Test
    fun variantMismatchFails() {
        val (root, snapshot) = repository(headerBytes = RepositoryFixture.header(variant = "Beta"))
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("VARIANT_MISMATCH"))
    }

    @Test
    fun anExtraUnrelatedBlobIsAllowed() {
        val (root, snapshot) = repository()
        bothBlobs(root)
        publish(root, ByteArray(5) { 3 }, "${"b".repeat(64)}.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals(listOf<String>(), issueCodes(report))
        assertEquals("PASS", report.verdict)
        assertEquals(listOf("Media/${"b".repeat(64)}.jpg"), report.extras)
    }

    @Test
    fun aNonCanonicalMediaEntryFails() {
        val (root, snapshot) = repository()
        bothBlobs(root)
        publish(root, ByteArray(3) { 1 }, "not-a-blob.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("NONCANONICAL_MEDIA_ENTRY"))
    }

    @Test
    fun aSnapshotWithoutMediaReferencesAndAnEmptyMediaDirectoryPasses() {
        val root = temporary.newFolder("empty-repository").toPath()
        Files.write(root.resolve("repository.json"), RepositoryFixture.header())
        Files.createDirectories(root.resolve("Media"))
        val snapshot = Fixture.canonicalCopy(root.resolve("Snapshots"), Fixture.validBytes())

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals(listOf<String>(), issueCodes(report))
        assertEquals("PASS", report.verdict)
        assertEquals(0, report.requiredCount)
    }

    @Test
    fun missingMediaDirectoryFailsWhenBlobsAreRequired() {
        val root = temporary.newFolder("no-media").toPath()
        Files.write(root.resolve("repository.json"), RepositoryFixture.header())
        val snapshot = Fixture.canonicalCopy(root.resolve("Snapshots"), RepositoryFixture.snapshot())

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("MEDIA_DIRECTORY_MISSING"))
    }

    /** The standalone mode keeps working exactly as before, on the same bytes. */
    @Test
    fun theStandaloneSnapshotModeIsUnchanged() {
        val (root, snapshot) = repository()
        bothBlobs(root)

        val standalone = Verifier().verify(snapshot)

        assertEquals(listOf<String>(), standalone.issues.map { it.code })
        assertEquals("PASS", standalone.verdict)
        assertEquals(17, standalone.verifiedEntries)
    }

    // --- the local full-evidence profile ---

    @Test
    fun aValidFullSnapshotWithACompleteRepositoryPasses() {
        val (root, snapshot) = repository(RepositoryFixture.snapshot(full = true))
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals(listOf<String>(), issueCodes(report))
        assertEquals("PASS", report.verdict)
        assertEquals("REPOSITORY_EVIDENCE_REQUIRED", report.snapshotVerdict)
        assertTrue(report.repositoryEvidenceRequired)
        assertEquals("FULL", report.snapshotProfile)
        assertEquals("LOCAL_VERIFIED", report.evidencePolicy)
        assertEquals(2, report.requiredCount)
        assertEquals(2, report.verifiedRequired)
    }

    @Test
    fun aFullSnapshotWithoutItsRequiredBlobsFails() {
        val (root, snapshot) = repository(RepositoryFixture.snapshot(full = true))

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertEquals(0, report.verifiedRequired)
        assertEquals(2, issueCodes(report).count { it == RepositoryVerificationReport.MISSING })
    }

    @Test
    fun aFullSnapshotWithAWrongSizeFails() {
        val (root, snapshot) = repository(RepositoryFixture.snapshot(full = true))
        val (_, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, ByteArray(11) { 1 }, "$shaObject.jpg")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.SIZE_MISMATCH))
    }

    @Test
    fun aFullSnapshotWithWrongBytesFails() {
        val (root, snapshot) = repository(RepositoryFixture.snapshot(full = true))
        val (_, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, ByteArray(10) { 7 }, "$shaObject.jpg")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.SHA_MISMATCH))
    }

    @Test
    fun aFullSnapshotWithAWrongCanonicalExtensionFails() {
        val (root, snapshot) = repository(RepositoryFixture.snapshot(full = true))
        val (objectBytes, attachmentBytes) = RepositoryFixture.payloads()
        val (shaObject, shaAttachment) = RepositoryFixture.requiredShas()
        publish(root, objectBytes, "$shaObject.mp4")
        publish(root, attachmentBytes, "$shaAttachment.jpg")

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains(RepositoryVerificationReport.MISSING))
        assertTrue(issueCodes(report).contains("DUPLICATE_SHA_ENTRY"))
    }

    @Test
    fun aFullSnapshotFromAnotherRepositoryIdentityFails() {
        val (root, snapshot) = repository(
            RepositoryFixture.snapshot(full = true),
            RepositoryFixture.header(repositoryId = "44444444-4444-4444-8444-444444444444"),
        )
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("REPOSITORY_UUID_MISMATCH"))
    }

    @Test
    fun aFullSnapshotFromAnotherVariantFails() {
        val (root, snapshot) = repository(
            RepositoryFixture.snapshot(full = true),
            RepositoryFixture.header(variant = "Beta"),
        )
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("FAIL", report.verdict)
        assertTrue(issueCodes(report).contains("VARIANT_MISMATCH"))
    }

    /** Manifest evidence is authoritative: satisfied bytes never promote a metadata-only snapshot. */
    @Test
    fun aMetadataOnlySnapshotIsNeverDescribedAsFullOrLocalVerified() {
        val (root, snapshot) = repository()
        bothBlobs(root)

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals("PASS", report.verdict)
        assertEquals("PASS", report.snapshotVerdict)
        assertFalse(report.repositoryEvidenceRequired)
        assertEquals("METADATA_ONLY", report.snapshotProfile)
        assertEquals("NO_MEDIA_EVIDENCE", report.evidencePolicy)
    }

    @Test
    fun aFullSnapshotWithZeroReferencesAndAMatchingRepositoryPasses() {
        val (root, snapshot) = repository(Fixture.fullEvidence(Fixture.validBytes()))

        val report = RepositoryVerifier().verify(root, snapshot)

        assertEquals(listOf<String>(), issueCodes(report))
        assertEquals("PASS", report.verdict)
        assertTrue(report.repositoryEvidenceRequired)
        assertEquals(0, report.requiredCount)
        assertEquals(0, report.verifiedRequired)
    }
}
