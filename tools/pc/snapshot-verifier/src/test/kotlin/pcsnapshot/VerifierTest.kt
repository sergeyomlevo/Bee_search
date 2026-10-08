package pcsnapshot

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifierTest {
    @Rule @JvmField val temp = TemporaryFolder()

    private fun verify(bytes: ByteArray = Fixture.validBytes(), canonical: Boolean = true): VerificationReport {
        val path = if (canonical) Fixture.canonicalCopy(temp.root.toPath(), bytes)
        else Fixture.write(temp.root.toPath(), "snapshot.zip", bytes)
        return Verifier().verify(path)
    }

    @Test fun validEmptyCorpusPasses() {
        val report = verify()
        assertEquals("PASS", report.verdict)
        assertEquals(17, report.verifiedEntries)
    }

    @Test fun filenameHashChangeFails() {
        val bytes = Fixture.validBytes()
        val path = Fixture.write(temp.root.toPath(), "snapshot-${Fixture.snapshotId}-${"0".repeat(64)}.zip", bytes)
        val report = Verifier().verify(path)
        assertEquals("FAIL", report.verdict)
        assertTrue(report.issues.any { it.code.contains("FILENAME", true) || it.code.contains("HASH", true) })
    }

    @Test fun containerCommentWithRecomputedFilenameHashIsAccepted() {
        val bytes = Fixture.zip(Fixture.entries(Fixture.validBytes()), "comment")
        val path = Fixture.write(temp.root.toPath(), "snapshot-${Fixture.snapshotId}-${Fixture.sha256(bytes)}.zip", bytes)
        val report = Verifier().verify(path)
        assertEquals("PASS", report.verdict)
    }

    @Test fun parseableEntryMutationWithRecomputedWholeFilenameFailsDigest() {
        val files = Fixture.entries(Fixture.validBytes())
        files["data/territories.jsonl"] = " { }\n".toByteArray()
        val report = verify(Fixture.zip(files))
        assertEquals("FAIL", report.verdict)
        assertTrue(report.issues.any { it.code.contains("DIGEST", true) || it.code.contains("HASH", true) })
    }

    @Test fun sameSizeCorruptionWithStaleManifestDigestFails() {
        val files = Fixture.entries(Fixture.validBytes(mapOf("data/territories.jsonl" to "{\"id\":1}\n".toByteArray())))
        files["data/territories.jsonl"] = "{\"id\":2}\n".toByteArray()
        assertEquals("FAIL", verify(Fixture.zip(files)).verdict)
    }

    @Test fun truncationsFailClosed() {
        val bytes = Fixture.validBytes()
        val local = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val cen = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        fun indexOf(needle: ByteArray): Int = (0..bytes.size - needle.size).first { i -> bytes.copyOfRange(i, i + needle.size).contentEquals(needle) }
        val localAt = indexOf(local)
        val cenAt = indexOf(cen)
        listOf(localAt + 2, localAt + 34, localAt + 40, cenAt - 1, cenAt + 2, bytes.size / 2).forEach { end ->
            assertEquals("FAIL", verify(bytes.copyOf(end)).verdict)
        }
    }

    @Test fun missingExtraDirectoryAndUnsafeNamesFail() {
        val base = Fixture.entries(Fixture.validBytes())
        val missing = LinkedHashMap(base).also { it.remove("data/bees.jsonl") }
        assertEquals("FAIL", verify(Fixture.zip(missing)).verdict)
        val extra = LinkedHashMap(base).also { it["extra.json"] = ByteArray(0) }
        assertEquals("FAIL", verify(Fixture.zip(extra)).verdict)
        listOf("dir/", "../escape", "/absolute", "data\\bad.jsonl").forEach { unsafe ->
            val altered = LinkedHashMap(base).also { it[unsafe] = ByteArray(0) }
            assertEquals("FAIL", verify(Fixture.zip(altered)).verdict)
        }
    }

    @Test fun duplicateZipEntryFails() {
        val files = Fixture.entries(Fixture.validBytes()).also { it["data/zzzz.jsonl"] = ByteArray(0) }
        val bytes = Fixture.replaceAscii(Fixture.zip(files), "data/zzzz.jsonl", "data/bees.jsonl")
        assertEquals("FAIL", verify(bytes).verdict)
    }

    @Test fun localHeaderAndCentralDirectoryNameDisagreementFails() {
        val altered = Fixture.mutateFirstLocalName(Fixture.validBytes(), "data/apiaries.jsonl", "data/xxxxxxxx.jsonl")
        assertTrue(verify(altered).issues.any { it.code == "ZIP_VIEW_MISMATCH" })
    }

    @Test fun unsupportedManifestEnumsAndCreationIssuesFail() {
        fun altered(field: String, value: String): ByteArray {
            val files = Fixture.entries(Fixture.validBytes())
            val manifest = files["manifest.json"]!!.toString(Charsets.UTF_8).replace(field, value)
            files["manifest.json"] = manifest.toByteArray()
            return Fixture.zip(files)
        }
        assertEquals("FAIL", verify(altered("METADATA_ONLY", "UNKNOWN")).verdict)
        assertEquals("FAIL", verify(altered("NO_MEDIA_EVIDENCE", "LOCAL_VERIFIED")).verdict)
        assertEquals("PASS", verify(altered("\"snapshotFormatVersion\":1", "\"snapshotFormatVersion\":2")).verdict)
        assertEquals("FAIL", verify(altered("\"creationResult\":\"COMPLETE\"", "\"creationResult\":\"DEGRADED\"")).verdict)
        assertEquals("FAIL", verify(altered("\"creationIssues\":[]", "\"creationIssues\":[{}]")).verdict)
    }

    @Test fun duplicateManifestKeyFails() {
        val files = Fixture.entries(Fixture.validBytes())
        val m = files["manifest.json"]!!.toString(Charsets.UTF_8)
        files["manifest.json"] = m.replaceFirst("{", "{\"snapshotId\":\"${Fixture.snapshotId}\",").toByteArray()
        assertEquals("FAIL", verify(Fixture.zip(files)).verdict)
    }

    @Test fun nonFiniteManifestDomainNumberFails() {
        val bytes = Fixture.validBytes(mapOf("data/territories.jsonl" to "{\"latitude\":1e309}\n".toByteArray()))
        assertEquals("FAIL", verify(bytes).verdict)
    }

    @Test fun duplicateDomainIdentityAndPortableForeignKeyFail() {
        val row = territory("33333333-3333-4333-8333-333333333333")
        val duplicate = Fixture.validBytes(mapOf("data/territories.jsonl" to
            (row + row).toByteArray()))
        assertTrue(verify(duplicate).issues.any { it.code == "DUPLICATE_RECORD_KEY" })
        val dangling = Fixture.validBytes(mapOf("settings/portable.json" to
            "{\"currentObserverId\":\"44444444-4444-4444-8444-444444444444\",\"currentTerritoryId\":null}".toByteArray()))
        assertTrue(verify(dangling).issues.any { it.code == "LOGICAL_STATE_INCONSISTENT" })
    }

    @Test fun domainRecordsOutOfCanonicalOrderFail() {
        val records = territory("ffffffff-ffff-4fff-8fff-ffffffffffff") + territory("00000000-0000-4000-8000-000000000000")
        assertTrue(verify(Fixture.validBytes(mapOf("data/territories.jsonl" to records.toByteArray())))
            .issues.any { it.code == "RECORD_ORDER_INVALID" })
    }

    @Test fun filenameSnapshotIdMustMatchManifest() {
        val bytes = Fixture.validBytes()
        val path = Fixture.write(temp.root.toPath(),
            "snapshot-aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa-${Fixture.sha256(bytes)}.zip", bytes)
        assertTrue(Verifier().verify(path).issues.any { it.code == "SNAPSHOT_ID_MISMATCH" })
    }

    @Test fun emptyReferenceEntryStillChecksManifestCount() {
        val files = Fixture.entries(Fixture.validBytes())
        files["manifest.json"] = files.getValue("manifest.json").toString(Charsets.UTF_8)
            .replace("\"recordCount\":0", "\"recordCount\":1").toByteArray()
        assertTrue(verify(Fixture.zip(files)).issues.any { it.code == "MEDIA_REFERENCE_COUNT_MISMATCH" })
    }

    @Test fun limitsEqualityIsAllowedAndOneOverFails() {
        assertEquals(64L * 1024 * 1024, Contract.ZIP)
        assertEquals(128L * 1024 * 1024, Contract.TOTAL)
        assertEquals(1024L * 1024, Contract.ONE)
        assertEquals(32L * 1024 * 1024, Contract.REFERENCES)
        assertEquals("PASS", verify().verdict)
        assertEquals("FAIL", verify(ByteArray(Contract.ZIP.toInt() + 1)).verdict)
        listOf(
            "zip" to Contract.ZIP, "total" to Contract.TOTAL, "manifest" to Contract.ONE,
            "portable" to Contract.ONE, "references" to Contract.REFERENCES, "record" to Contract.ONE
        ).forEach { (scope, maximum) ->
            Budget(maximum, scope).add(maximum)
            try {
                Budget(maximum, scope).add(maximum + 1)
                throw AssertionError("$scope accepted one byte over limit")
            } catch (_: CheckFailure) { }
        }
    }

    @Test fun streamedExpansionOverTotalBudgetFails() {
        val report = verify(Fixture.zipBombBytes())
        assertEquals("FAIL", report.verdict)
        assertTrue(report.issues.any { it.code.contains("LIMIT", true) || it.code.contains("ZIP", true) })
    }

    @Test fun changedContainerWithoutRenamingFailsWholeHash() {
        val original = Fixture.validBytes()
        val changed = Fixture.zip(Fixture.entries(original), "changed comment")
        val path = Fixture.write(temp.root.toPath(), "snapshot-${Fixture.snapshotId}-${Fixture.sha256(original)}.zip",changed)
        assertTrue(Verifier().verify(path).issues.any { it.code == "SNAPSHOT_FILENAME_SHA_MISMATCH" })
    }

    @Test fun closedManifestAndDescriptorFieldsAreEnforced() {
        for (mutation in listOf<(String)->String>(
            { it.replaceFirst("{", "{\"unknown\":null,") },
            { it.replaceFirst("\"byteSize\":0", "\"byteSize\":0,\"recordCount\":0") },
            { it.replace("\"createdAtEpochMs\":0", "\"createdAtEpochMs\":-1") }
        )) {
            val files = Fixture.entries(Fixture.validBytes())
            files["manifest.json"] = mutation(files.getValue("manifest.json").toString(Charsets.UTF_8)).toByteArray()
            assertEquals("FAIL",verify(Fixture.zip(files)).verdict)
        }
    }

    private fun territory(id: String) = "{\"id\":\"$id\",\"code\":\"$id\",\"name\":\"N\",\"region\":\"R\",\"district\":\"D\",\"createdAt\":0,\"updatedAt\":0}\n"
}
