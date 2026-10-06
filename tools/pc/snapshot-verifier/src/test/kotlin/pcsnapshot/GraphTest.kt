package pcsnapshot

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Independent graph/schema vectors; mutations refresh raw entry descriptors only. */
class GraphTest {
    @Rule @JvmField val temp = TemporaryFolder()

    private fun verify(files: LinkedHashMap<String, ByteArray> = NonEmptyFixture.entries()): VerificationReport {
        files["manifest.json"] = Fixture.manifest(files)
        val bytes = Fixture.zip(files)
        val path: Path = Fixture.canonicalCopy(temp.root.toPath(), bytes)
        return Verifier().verify(path)
    }

    private fun mutated(path: String, edit: (String) -> String): VerificationReport {
        val files = NonEmptyFixture.entries()
        val source = files.getValue(path).toString(Charsets.UTF_8)
        files[path] = edit(source).toByteArray()
        return verify(files)
    }

    @Test fun completeNonEmptyGraphPassesWithFixedCanonicalFilename() {
        val report = verify()
        assertEquals("PASS", report.verdict)
        assertEquals(17, report.verifiedEntries)
    }

    @Test fun futureCreatedAtIsWireValid() {
        val files = NonEmptyFixture.entries()
        files["manifest.json"] = Fixture.manifest(files, referenceCount = 2)
            .toString(Charsets.UTF_8).replace("\"createdAtEpochMs\":0", "\"createdAtEpochMs\":9007199254740991").toByteArray()
        val path = Fixture.canonicalCopy(temp.root.toPath(), Fixture.zip(files))
        assertEquals("PASS", Verifier().verify(path).verdict)
    }

    @Test fun danglingFkIsLogicalStateInconsistent() = assertCode(
        mutated("data/bees.jsonl") { it.replace(NonEmptyFixture.point, "30000000-0000-4000-8000-000000000099") }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun wrongSubtypeIsLogicalStateInconsistent() = assertCode(
        mutated("data/apiaries.jsonl") { it.replace(NonEmptyFixture.apiary, NonEmptyFixture.hollow) }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun duplicateRecordKeyIsRejected() = assertCode(
        mutated("data/territories.jsonl") { it + it }, "DUPLICATE_RECORD_KEY")

    @Test fun malformedWeatherMatrixIsRejected() = assertCode(
        mutated("data/observation-point-weather.jsonl") { it.replace("\"status\":\"PENDING\"", "\"status\":\"LOADED\"") }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun elevenBeesAtOnePointAreRejected() {
        val extra = (10..19).joinToString("") { n ->
            "{\"id\":\"30000000-0000-4000-8000-${n.toString().padStart(12, '0')}\",\"observationPointId\":\"${NonEmptyFixture.point}\",\"markColor\":\"c$n\",\"markPosition\":\"THORAX\",\"createdAt\":3000,\"sourceObjectId\":null}\n"
        }
        assertCode(mutated("data/bees.jsonl") { it + extra }, "LOGICAL_STATE_INCONSISTENT")
    }

    @Test fun portableBadTerritoryIsRejected() = assertCode(
        mutated("settings/portable.json") { it.replace(NonEmptyFixture.territory, "30000000-0000-4000-8000-000000000099") }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun portableBadObserverIsRejected() = assertCode(
        mutated("settings/portable.json") { it.replace(NonEmptyFixture.observer, "30000000-0000-4000-8000-000000000099") }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun missingWeatherRowIsRejected() = assertCode(
        mutated("data/observation-point-weather.jsonl") { "" }, "LOGICAL_STATE_INCONSISTENT")

    @Test fun unknownAndMissingFieldsAreSchemaInvalid() {
        assertCode(mutated("data/territories.jsonl") { it.replace("\"district\":\"Field\"", "\"district\":\"Field\",\"extra\":1") }, "WIRE_SCHEMA_INVALID")
        assertCode(mutated("data/territories.jsonl") { it.replace(",\"district\":\"Field\"", "") }, "WIRE_SCHEMA_INVALID")
    }

    @Test fun uppercaseUuidAndShaAreRejected() {
        assertCode(mutated("data/territories.jsonl") { it.replace(NonEmptyFixture.territory, NonEmptyFixture.territory.uppercase()) }, "UUID_INVALID")
        assertCode(mutated("data/physical-object-media.jsonl") { it.replace(NonEmptyFixture.shaImage, NonEmptyFixture.shaImage.uppercase()) }, "SHA_INVALID")
    }

    @Test fun unknownEnumIsSchemaInvalid() = assertCode(
        mutated("data/physical-objects.jsonl") { it.replace("\"objectType\":\"APIARY\"", "\"objectType\":\"UNKNOWN\"") }, "WIRE_SCHEMA_INVALID")

    @Test fun zeroReferenceAndMissingReferenceSetMismatch() {
        assertCode(mutated("references/media-blobs.jsonl") { it.replace("\"byteSize\":10", "\"byteSize\":0") }, "MEDIA_REFERENCE_SET_MISMATCH")
        assertCode(mutated("references/media-blobs.jsonl") { it.replace(Regex("\\{\\\"byteSize\\\":20[^\\n]+\\n"), "") }, "MEDIA_REFERENCE_SET_MISMATCH")
    }

    @Test fun eligibleMediaIdentityConflictsAreRejected() {
        assertCode(mutated("data/observation-point-attachments.jsonl") { it.replace(NonEmptyFixture.shaAttachment, NonEmptyFixture.shaImage) }, "MEDIA_IDENTITY_CONFLICT")
        assertCode(mutated("data/observation-point-attachments.jsonl") { it.replace(NonEmptyFixture.shaAttachment, NonEmptyFixture.shaImage).replace("\"byteSize\":20", "\"byteSize\":10").replace("\"mimeType\":\"image/jpeg\"", "\"mimeType\":\"video/mp4\"") }, "MEDIA_IDENTITY_CONFLICT")
    }

    @Test fun invalidMapCoverageIsRejected() = assertCode(
        mutated("settings/map-coverage.jsonl") { it.replace("v1|55.2,37.3,55.0,37.0", "v1|91,37.3,55.0,37.0") }, "MAP_COVERAGE_INVALID")

    @Test fun invalidV2GeometryIsRejected() = assertCode(
        mutated("settings/map-coverage.jsonl") { it.replace("v1|55.2,37.3,55.0,37.0", "v2|{\\\"areaId\\\":\\\"30000000-0000-4000-8000-000000000013\\\",\\\"name\\\":\\\"Area\\\",\\\"bounds\\\":[]}") }, "MAP_COVERAGE_INVALID")

    @Test fun unrelatedCollectionsMayShareUuid() {
        val files = NonEmptyFixture.entries()
        files.replaceAll { _, bytes -> bytes.toString(Charsets.UTF_8).replace(NonEmptyFixture.observer, NonEmptyFixture.territory).toByteArray() }
        assertEquals("PASS", verify(files).verdict)
    }

    @Test fun missingSequenceScopeRowIsAllowed() {
        assertEquals("PASS", mutated("data/physical-object-sequences.jsonl") {
            it.lineSequence().filter { line -> line.isNotEmpty() && !line.contains("LOG_HIVE") }.joinToString("\n") + "\n"
        }.verdict)
    }

    @Test fun domainFieldOrderIsNotSemantic() {
        assertEquals("PASS", mutated("data/territories.jsonl") {
            "{\"updatedAt\":2000,\"district\":\"Field\",\"createdAt\":1000,\"region\":\"Central\",\"name\":\"North Meadow\",\"code\":\"T-01\",\"id\":\"${NonEmptyFixture.territory}\"}\n"
        }.verdict)
    }

    @Test fun recordOrderIsRejected() = assertCode(mutated("data/physical-objects.jsonl") {
        it.lines().filter { line -> line.isNotBlank() }.reversed().joinToString("\n") + "\n"
    }, "RECORD_ORDER_INVALID")

    @Test fun referenceCountMismatchIsRejected() {
        val files = NonEmptyFixture.entries()
        files["manifest.json"] = Fixture.manifest(files, referenceCount = 1)
        val report = Verifier().verify(Fixture.canonicalCopy(temp.root.toPath(), Fixture.zip(files)))
        assertCode(report, "MEDIA_REFERENCE_COUNT_MISMATCH")
    }

    private fun assertCode(report: VerificationReport, code: String) {
        assertEquals("FAIL", report.verdict)
        assertTrue("expected $code, got ${report.issues.map { it.code }}", report.issues.any { it.code == code })
    }
}
