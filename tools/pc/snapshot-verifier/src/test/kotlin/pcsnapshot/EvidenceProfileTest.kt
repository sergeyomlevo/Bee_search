package pcsnapshot

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The two supported Snapshot V1 evidence tuples (wire schema §7.1) as an independent reader sees them.
 *
 * The shared vector file is the contract: this suite checks the PC parser against it, and the Android
 * suite checks the production model against the same file.
 */
class EvidenceProfileTest {
    @get:Rule val temp = TemporaryFolder()

    private fun vectorFile(): Path {
        val relative = "docs/test-vectors/snapshot-v1-evidence-profiles.json"
        return listOf(Path.of(relative), Path.of("../$relative"), Path.of("../../$relative"), Path.of("../../../$relative"))
            .first { Files.isRegularFile(it) }
    }

    private fun text(o: com.google.gson.JsonObject, key: String) = o.get(key).asString

    @Test
    fun theSharedVectorFileMatchesTheIndependentProfileParser() {
        val root = JsonParser.parseString(Files.readString(vectorFile())).asJsonObject
        assertEquals("snapshot-v1-evidence-profiles", text(root, "contract"))

        val supported = root.getAsJsonArray("supported")
        assertEquals(2, supported.size())
        supported.forEach { element ->
            val o = element.asJsonObject
            val profile = EvidenceProfile.parse(text(o, "snapshotProfile"), text(o, "evidencePolicy"), text(o, "creationResult"))
            assertNotNull(o.toString(), profile)
            assertEquals(text(o, "profile"), profile!!.profile)
            assertEquals(o.get("repositoryEvidenceRequired").asBoolean, profile.repositoryEvidenceRequired)
        }

        val unsupported = root.getAsJsonArray("unsupported")
        assertTrue(unsupported.size() >= 8)
        unsupported.forEach { element ->
            val o = element.asJsonObject
            val issues = o.getAsJsonArray("creationIssues")
            if (issues.size() > 0) {
                // A supported tuple with non-empty creationIssues fails in the manifest, not the tuple.
                assertNotNull(EvidenceProfile.parse(text(o, "snapshotProfile"), text(o, "evidencePolicy"), text(o, "creationResult")))
                val report = Verifier().verify(Fixture.canonicalCopy(temp.newFolder().toPath(),
                    Fixture.withCreationIssues(Fixture.fullEvidence(Fixture.validBytes()), "[\"x\"]")))
                assertEquals("FAIL", report.verdict)
                assertTrue(report.issues.map { it.code }.contains("MANIFEST_INVALID"))
            } else {
                assertNull(o.toString(), EvidenceProfile.parse(text(o, "snapshotProfile"), text(o, "evidencePolicy"), text(o, "creationResult")))
            }
        }
    }

    @Test
    fun standaloneFullStructureIsValidButNeverFinalRepositoryEvidence() {
        val file = Fixture.canonicalCopy(temp.root.toPath(), Fixture.fullEvidence(Fixture.validBytes()))

        val report = Verifier().verify(file)

        assertEquals(listOf<String>(), report.issues.map { it.code })
        assertEquals("REPOSITORY_EVIDENCE_REQUIRED", report.verdict)
        assertTrue(report.repositoryEvidenceRequired)
        assertEquals(EvidenceProfile.FULL_LOCAL_VERIFIED, report.evidenceProfile)
        assertEquals("FULL", report.snapshotProfile)
        assertEquals("LOCAL_VERIFIED", report.evidencePolicy)
        assertEquals("COMPLETE", report.creationResult)
        assertEquals(17, report.verifiedEntries)
    }

    @Test
    fun standaloneMetadataOnlyVerificationIsUnchanged() {
        val file = Fixture.canonicalCopy(temp.root.toPath())

        val report = Verifier().verify(file)

        assertEquals(listOf<String>(), report.issues.map { it.code })
        assertEquals("PASS", report.verdict)
        assertFalse(report.repositoryEvidenceRequired)
        assertEquals(EvidenceProfile.METADATA_ONLY, report.evidenceProfile)
        assertEquals("METADATA_ONLY", report.snapshotProfile)
        assertEquals("NO_MEDIA_EVIDENCE", report.evidencePolicy)
    }

    @Test
    fun everyUnsupportedTupleFailsStandaloneWithTheUnsupportedProfileIssue() {
        val cases = listOf(
            Triple("FULL", "NO_MEDIA_EVIDENCE", "COMPLETE"),
            Triple("METADATA_ONLY", "LOCAL_VERIFIED", "COMPLETE"),
            Triple("FULL", "LOCAL_VERIFIED", "DEGRADED"),
            Triple("FULL", "LOCAL_VERIFIED", "PARTIAL"),
            Triple("FULL", "LOCAL_VERIFIED", "INCOMPLETE"),
            Triple("UNKNOWN_PROFILE", "NO_MEDIA_EVIDENCE", "COMPLETE"),
            Triple("METADATA_ONLY", "REMOTE_VERIFIED", "COMPLETE"),
            Triple("FULL", "PC_VERIFIED", "COMPLETE"),
        )
        for ((profile, policy, result) in cases) {
            val bytes = Fixture.withEvidenceProfile(Fixture.validBytes(), profile, policy, result)
            val file = Fixture.canonicalCopy(temp.newFolder().toPath(), bytes)

            val report = Verifier().verify(file)

            assertEquals("$profile/$policy/$result", "FAIL", report.verdict)
            assertTrue("$profile/$policy/$result", report.issues.map { it.code }.contains("MANIFEST_UNSUPPORTED_PROFILE"))
            assertNull(report.evidenceProfile)
        }
    }
}
