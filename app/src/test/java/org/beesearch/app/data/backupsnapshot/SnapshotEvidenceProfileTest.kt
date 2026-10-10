package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Snapshot V1 evidence-profile contract (wire schema §7.1): exactly two supported tuples, every
 * cross-combination refused, and the metadata-only profile byte-identical to what it was before this
 * profile existed.
 */
class SnapshotEvidenceProfileTest {
    @get:Rule val temp = TemporaryFolder()

    private val identity = SnapshotIdentity(
        UUID.fromString("11111111-1111-4111-8111-111111111111"),
        UUID.fromString("22222222-2222-4222-8222-222222222222"),
        "Dev",
        1_000L,
    )
    private val descriptors = listOf(SnapshotEntryDescriptor("data/territories.jsonl", 3, "a".repeat(64)))

    private fun encoded(profile: SnapshotEvidenceProfile = SnapshotEvidenceProfile.METADATA_ONLY) =
        String(SnapshotManifest.encode(identity, descriptors, 0, profile), Charsets.UTF_8)

    private fun expectInvalid(name: String, bytes: ByteArray) {
        try {
            SnapshotManifest.decode(bytes)
            fail("$name must be refused")
        } catch (e: SnapshotException) {
            assertEquals("$name: ${e.category}", SnapshotError.INVALID_FORMAT, e.error)
        }
    }

    private fun vectorFile(): Path {
        val relative = "docs/test-vectors/snapshot-v1-evidence-profiles.json"
        return listOf(Path.of(relative), Path.of("../$relative")).first { Files.isRegularFile(it) }
    }

    private fun tuple(o: JsonObject) = SnapshotEvidenceProfile.parse(
        o.getValue("snapshotProfile").jsonPrimitive.content,
        o.getValue("evidencePolicy").jsonPrimitive.content,
        o.getValue("creationResult").jsonPrimitive.content,
    )

    /** The shared vector file is the contract both implementations are checked against. */
    @Test
    fun theSharedVectorFileMatchesTheOnlyTwoSupportedTuples() {
        val root = Json.parseToJsonElement(Files.readString(vectorFile())).jsonObject
        assertEquals("snapshot-v1-evidence-profiles", root.getValue("contract").jsonPrimitive.content)
        assertEquals("beesearch-snapshot", root.getValue("snapshotFormat").jsonPrimitive.content)

        val supported = root.getValue("supported").jsonArray
        assertEquals(2, supported.size)
        supported.forEach { element ->
            val o = element.jsonObject
            val profile = tuple(o)
            assertEquals(o.getValue("profile").jsonPrimitive.content, profile.profile.token)
            assertEquals(
                o.getValue("repositoryEvidenceRequired").jsonPrimitive.boolean,
                profile.requiresRepositoryMediaEvidence,
            )
            assertTrue(o.getValue("creationIssues").jsonArray.isEmpty())
        }

        val unsupported = root.getValue("unsupported").jsonArray
        assertTrue(unsupported.size >= 8)
        unsupported.forEach { element ->
            val o = element.jsonObject
            val bytes = if (o.getValue("creationIssues").jsonArray.isEmpty()) {
                // A structurally unsupported tuple is refused by the profile itself.
                try {
                    tuple(o)
                    fail("unsupported tuple must be refused: $o")
                } catch (e: SnapshotException) {
                    assertEquals(SnapshotError.INVALID_FORMAT, e.error)
                }
                return@forEach
            } else {
                // A supported tuple with a non-empty creationIssues array is refused by the manifest.
                SnapshotManifest.encode(
                    identity,
                    descriptors,
                    0,
                    tuple(o),
                ).decodeToString()
                    .replace("\"creationIssues\":[]", "\"creationIssues\":[\"media evidence unavailable\"]")
                    .toByteArray()
            }
            expectInvalid("unsupported manifest $o", bytes)
        }
    }

    @Test
    fun onlyTheTwoTuplesAreSupportedAndOldSnapshotsKeepTheirMeaning() {
        assertEquals(listOf(SnapshotEvidenceProfile.METADATA_ONLY, SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED),
            SnapshotEvidenceProfile.SUPPORTED)
        assertFalse(SnapshotEvidenceProfile.METADATA_ONLY.requiresRepositoryMediaEvidence)
        assertTrue(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED.requiresRepositoryMediaEvidence)
        assertEquals("FULL/LOCAL_VERIFIED/COMPLETE", SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED.token)
        assertEquals("METADATA_ONLY/NO_MEDIA_EVIDENCE/COMPLETE", SnapshotEvidenceProfile.METADATA_ONLY.token)
    }

    @Test
    fun metadataOnlyEncodingKeepsItsExactFieldSetAndValues() {
        val text = encoded()
        assertEquals(text, encoded(SnapshotEvidenceProfile.METADATA_ONLY))
        val fieldNames = listOf("snapshotFormat", "snapshotFormatVersion", "snapshotId", "repositoryId", "variant",
            "createdAtEpochMs", "snapshotProfile", "creationResult", "evidencePolicy", "entries", "creationIssues",
            "mediaReferences")
        fieldNames.forEach { field -> assertEquals("$field must appear once", 1, Regex("\"$field\":").findAll(text).count()) }
        assertTrue(text.contains("\"snapshotProfile\":\"METADATA_ONLY\""))
        assertTrue(text.contains("\"creationResult\":\"COMPLETE\""))
        assertTrue(text.contains("\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\""))
        assertTrue(text.contains("\"creationIssues\":[]"))
        assertFalse(text.contains("FULL"))
        assertFalse(text.contains("LOCAL_VERIFIED"))

        val decoded = SnapshotManifest.decode(text.toByteArray())
        assertEquals(SnapshotEvidenceProfile.METADATA_ONLY, decoded.evidenceProfile)
        assertFalse(decoded.evidenceProfile.requiresRepositoryMediaEvidence)
        assertEquals(0L, decoded.mediaReferenceCount)
        assertEquals(1, decoded.descriptors.size)
    }

    @Test
    fun fullEncodingDeclaresLocalVerifiedEvidenceAndRoundTrips() {
        val text = encoded(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED)
        assertTrue(text.contains("\"snapshotProfile\":\"FULL\""))
        assertTrue(text.contains("\"evidencePolicy\":\"LOCAL_VERIFIED\""))
        assertTrue(text.contains("\"creationResult\":\"COMPLETE\""))
        assertTrue(text.contains("\"creationIssues\":[]"))
        assertTrue(text.contains("\"snapshotFormatVersion\":3"))
        val decoded = SnapshotManifest.decode(text.toByteArray())
        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, decoded.evidenceProfile)
        assertTrue(decoded.evidenceProfile.requiresRepositoryMediaEvidence)
    }

    @Test
    fun everyUnsupportedCombinationOrUnknownTokenIsRefused() {
        val metadataOnly = encoded()
        expectInvalid("FULL with NO_MEDIA_EVIDENCE",
            metadataOnly.replace("\"snapshotProfile\":\"METADATA_ONLY\"", "\"snapshotProfile\":\"FULL\"").toByteArray())
        expectInvalid("METADATA_ONLY with LOCAL_VERIFIED",
            metadataOnly.replace("\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\"", "\"evidencePolicy\":\"LOCAL_VERIFIED\"").toByteArray())
        expectInvalid("unknown profile",
            metadataOnly.replace("\"snapshotProfile\":\"METADATA_ONLY\"", "\"snapshotProfile\":\"UNKNOWN_PROFILE\"").toByteArray())
        expectInvalid("unknown policy",
            metadataOnly.replace("\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\"", "\"evidencePolicy\":\"REMOTE_VERIFIED\"").toByteArray())
        expectInvalid("PC_VERIFIED policy",
            metadataOnly.replace("\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\"", "\"evidencePolicy\":\"PC_VERIFIED\"").toByteArray())
        expectInvalid("DEGRADED result",
            metadataOnly.replace("\"creationResult\":\"COMPLETE\"", "\"creationResult\":\"DEGRADED\"").toByteArray())
        expectInvalid("PARTIAL result",
            metadataOnly.replace("\"creationResult\":\"COMPLETE\"", "\"creationResult\":\"PARTIAL\"").toByteArray())
        expectInvalid("INCOMPLETE result",
            metadataOnly.replace("\"creationResult\":\"COMPLETE\"", "\"creationResult\":\"INCOMPLETE\"").toByteArray())
        expectInvalid("non-empty creationIssues",
            metadataOnly.replace("\"creationIssues\":[]", "\"creationIssues\":[\"x\"]").toByteArray())
        // A FULL manifest with an unsupported result is refused as well.
        expectInvalid("FULL with DEGRADED result",
            encoded(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED)
                .replace("\"creationResult\":\"COMPLETE\"", "\"creationResult\":\"DEGRADED\"").toByteArray())
    }

    @Test
    fun theArchiveWritesTheDeclaredProfileAndStillSeventeenEntries() {
        val empty = SnapshotDomainCodec.encode(
            Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()),
            PortableSettingsSnapshot(null, null, emptyMap()),
        )

        for ((profile, entries) in listOf(
            SnapshotEvidenceProfile.METADATA_ONLY to empty,
            SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED to empty,
        )) {
            val target = File(temp.root, "snapshot-${profile.profile.token}.zip")
            val built = SnapshotArchive().build(target, identity, entries, evidenceProfile = profile)
            assertEquals(17, built.metrics.entryBytes.size)
            assertEquals(profile, built.evidenceProfile)
            assertEquals(entries.references, built.references)
            val reread = SnapshotArchive().validate(target, identity.repositoryId, identity.variant)
            assertEquals(profile, reread.evidenceProfile)
            assertEquals(entries.references, reread.references)
            ZipFile(target).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(17, names.size)
                assertTrue(names.none { it.startsWith("Media/") })
                assertTrue(names.none { it.endsWith(".jpg") || it.endsWith(".mp4") })
                val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use { it.readBytes().decodeToString() }
                assertTrue(manifest.contains("\"snapshotProfile\":\"${profile.profile.token}\""))
                assertTrue(manifest.contains("\"evidencePolicy\":\"${profile.evidencePolicy.token}\""))
                assertTrue(manifest.contains("\"creationIssues\":[]"))
            }
        }
    }
}
