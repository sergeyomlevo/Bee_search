package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** No production writer/manifest builder: hand-authored domain bytes + standard ZIP/SHA. */
class SnapshotIndependentFixtureTest {
    @get:Rule val temp = TemporaryFolder()
    private val repository = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val snapshot = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val paths = listOf("data/territories.jsonl", "data/observers.jsonl", "data/physical-objects.jsonl",
        "data/apiaries.jsonl", "data/hollows.jsonl", "data/log-hives.jsonl", "data/physical-object-sequences.jsonl",
        "data/physical-object-media.jsonl", "data/observation-points.jsonl", "data/bees.jsonl", "data/flight-cycles.jsonl",
        "data/observation-point-weather.jsonl", "data/observation-point-attachments.jsonl", "settings/portable.json",
        "settings/map-coverage.jsonl", "references/media-blobs.jsonl")
    private val row = "{\"id\":\"33333333-3333-4333-8333-333333333333\",\"code\":\"T\",\"name\":\"Test\",\"region\":\"R\",\"district\":\"D\",\"createdAt\":0,\"updatedAt\":0}\n"
    private fun fixture(name: String, mutateRow: Boolean = false,
        portable: String = "{\"currentTerritoryId\":null,\"currentObserverId\":null}"): File {
        val entries = paths.associateWith { when(it) {
            "settings/portable.json" -> portable.toByteArray()
            "data/territories.jsonl" -> row.toByteArray()
            else -> byteArrayOf()
        } }
        val descriptors = paths.sorted().joinToString(",", "[", "]") { path ->
            val bytes = entries.getValue(path)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "{\"byteSize\":${bytes.size},\"path\":\"$path\",\"sha256\":\"$hash\"}"
        }
        val fields = sortedMapOf("snapshotFormat" to "\"beesearch-snapshot\"", "snapshotFormatVersion" to "1",
            "snapshotId" to "\"$snapshot\"", "repositoryId" to "\"$repository\"", "variant" to "\"Dev\"",
            "createdAtEpochMs" to "0", "snapshotProfile" to "\"METADATA_ONLY\"", "creationResult" to "\"COMPLETE\"",
            "evidencePolicy" to "\"NO_MEDIA_EVIDENCE\"", "creationIssues" to "[]", "entries" to descriptors,
            "mediaReferences" to "{\"path\":\"references/media-blobs.jsonl\",\"recordCount\":0}")
        val manifest = fields.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }.toByteArray()
        val out = File(temp.root, name)
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            entries.forEach { (path, original) ->
                val bytes = if (mutateRow && path == "data/territories.jsonl") (" " + row).toByteArray() else original
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out
    }
    @Test fun manualNonemptyFixtureAcceptedWithoutProductionGenerator() {
        val result = SnapshotArchive().validate(fixture("valid.zip"), repository, "Dev")
        assertEquals(1L, result.metrics.recordCounts["data/territories.jsonl"])
        assertEquals(17, result.metrics.entryBytes.size)
    }
    @Test fun sameSemanticsDifferentRawBytesRejected() {
        val error = assertThrows(SnapshotException::class.java) { SnapshotArchive().validate(fixture("changed.zip", true), repository, "Dev") }
        assertEquals(SnapshotError.ENTRY_DIGEST_MISMATCH, error.error)
    }
    @Test fun recordAndReferenceLimitEqualityAndFirstExcess() {
        val file = fixture("boundary.zip")
        SnapshotArchive(SnapshotLimits(recordBytes = row.toByteArray().size.toLong(), referencesBytes = 0)).validate(file, repository, "Dev")
        val error = assertThrows(SnapshotException::class.java) {
            SnapshotArchive(SnapshotLimits(recordBytes = row.toByteArray().size - 1L)).validate(file, repository, "Dev")
        }
        assertEquals(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, error.error)
    }

    @Test fun validEntryDigestsDoNotBypassSemanticAndJsonValidation() {
        val badReference = fixture("bad-reference.zip", portable =
            "{\"currentTerritoryId\":\"44444444-4444-4444-8444-444444444444\",\"currentObserverId\":null}")
        val semantic = assertThrows(SnapshotException::class.java) {
            SnapshotArchive().validate(badReference, repository, "Dev")
        }
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, semantic.error)
        val badJson = fixture("bad-json.zip", portable = "not json")
        val syntax = assertThrows(SnapshotException::class.java) {
            SnapshotArchive().validate(badJson, repository, "Dev")
        }
        assertEquals(SnapshotError.INVALID_FORMAT, syntax.error)
    }
}
