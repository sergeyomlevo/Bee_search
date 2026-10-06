package pcsnapshot

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/** Additional document-driven conformance matrices; no Android model dependencies. */
class WireAlignmentTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private fun verify(edit: (LinkedHashMap<String,ByteArray>)->Unit = {}): VerificationReport {
        val files = NonEmptyFixture.entries(); edit(files)
        files["manifest.json"] = Fixture.manifest(files)
        return Verifier().verify(Fixture.canonicalCopy(temp.root.toPath(),Fixture.zip(files)))
    }
    private fun edit(files: MutableMap<String,ByteArray>,path: String, change: (JsonObject)->Unit) {
        files[path] = files.getValue(path).toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.map {
            val row = JsonParser.parseString(it).asJsonObject; change(row); row.toString()+"\n"
        }.joinToString("").toByteArray()
    }
    private fun code(report: VerificationReport, expected: String) {
        assertEquals("FAIL",report.verdict)
        assertTrue("expected $expected: ${report.issues}",report.issues.any { it.code == expected })
    }

    @Test fun everyCollectionRejectsUnknownMissingAndWrongTypedFields() {
        for ((path,bytes) in NonEmptyFixture.entries().filterKeys { it.startsWith("data/") }) {
            val base = JsonParser.parseString(bytes.toString(Charsets.UTF_8).lineSequence().first()).asJsonObject
            RecordSchema.validate(base,path)
            val unknown = base.deepCopy().also { it.addProperty("extra",1) }
            val missing = base.deepCopy().also { it.remove(if (it.has("id")) "id" else if (it.has("physicalObjectId")) "physicalObjectId" else "territoryId".takeIf { k -> it.has(k) } ?: "observationPointId") }
            for (bad in listOf(unknown,missing)) assertEquals("WIRE_SCHEMA_INVALID",assertThrows(CheckFailure::class.java) { RecordSchema.validate(bad,path) }.issue.code)
            val key = base.entrySet().first { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString }.key
            val wrongType = base.deepCopy().also { it.addProperty(key,true) }
            assertEquals("WIRE_SCHEMA_INVALID",assertThrows(CheckFailure::class.java) { RecordSchema.validate(wrongType,path) }.issue.code)
        }
    }

    @Test fun optionalAndNullableMediaIdentityIsPreservedWithoutReferences() {
        val report = verify { files ->
            for (path in listOf("data/physical-object-media.jsonl","data/observation-point-attachments.jsonl"))
                edit(files,path) { it.remove("sha256"); it.remove("byteSize") }
            files["references/media-blobs.jsonl"] = ByteArray(0)
        }
        assertEquals("PASS",report.verdict)
    }
    @Test fun negativeAndUnsafeCanonicalSizesRemainMetadataOnly() {
        for (size in listOf(-1L,0L,9007199254740992L,Long.MAX_VALUE)) {
            assertEquals("PASS",verify { files ->
                for (path in listOf("data/physical-object-media.jsonl","data/observation-point-attachments.jsonl")) edit(files,path) { it.addProperty("byteSize",size) }
                files["references/media-blobs.jsonl"] = ByteArray(0)
            }.verdict)
        }
    }
    @Test fun malformedNonnullShaNeverSilentlyBecomesUnknown() {
        code(verify { edit(it,"data/physical-object-media.jsonl") { row -> row.addProperty("sha256","invalid") } },"SHA_INVALID")
    }
    @Test fun ineligibleMimeHintsDoNotCauseEligibleConflict() {
        assertEquals("PASS",verify { edit(it,"data/physical-object-media.jsonl") { row ->
            if (row.get("byteSize").asLong == 0L) row.addProperty("mimeType","video/mp4")
        } }.verdict)
    }
    @Test fun genericHintsDeduplicateUnderRecognizedMime() {
        assertEquals("PASS",verify { files ->
            edit(files,"data/observation-point-attachments.jsonl") { row ->
                if (row.has("sha256")) { row.addProperty("sha256",NonEmptyFixture.shaImage); row.addProperty("byteSize",10); row.addProperty("mimeType","application/octet-stream") }
            }
            files["references/media-blobs.jsonl"] = "{\"byteSize\":10,\"canonicalExtension\":\"jpg\",\"sha256\":\"${NonEmptyFixture.shaImage}\"}\n".toByteArray()
        }.verdict)
    }
    @Test fun mimeHintIsTrimmedByWireWhitespaceAndLowercasedInvariantly() {
        assertEquals("PASS",verify { files ->
            for (path in listOf("data/physical-object-media.jsonl","data/observation-point-attachments.jsonl")) edit(files,path) { it.addProperty("mimeType"," IMAGE/JPEG ") }
            files["references/media-blobs.jsonl"] = files.getValue("references/media-blobs.jsonl").toString(Charsets.UTF_8).toByteArray()
        }.verdict)
    }

    @Test fun sharedMimeVectorsExerciseNonEmptyMetadataAndReferenceDerivation() {
        val vectorPath = listOf(
            Path.of("../../../docs/test-vectors/repository-v1-mime-extensions.json"),
            Path.of("docs/test-vectors/repository-v1-mime-extensions.json")
        ).firstOrNull { Files.exists(it) } ?: error("shared MIME vector file not found")
        val vectors = JsonParser.parseString(Files.readString(vectorPath)).asJsonObject
        vectors.getAsJsonArray("singleHints").forEach { value ->
            val vector = value.asJsonObject
            val hint = vector.get("hint")?.let { if (it.isJsonNull) null else it.asString }
            val expected = vector.get("extension").asString
            val report = verify { files ->
                setMediaHints(files, listOf(hint, hint))
                files["references/media-blobs.jsonl"] = references(
                    NonEmptyFixture.shaImage to expected,
                    NonEmptyFixture.shaAttachment to expected
                )
            }
            assertEquals("hint=$hint", "PASS", report.verdict)
        }
        vectors.getAsJsonArray("merges").forEach { value ->
            val vector = value.asJsonObject
            val hints = vector.getAsJsonArray("hints").map { if (it.isJsonNull) null else it.asString }
            val report = verify { files ->
                setMergeMediaHints(files, hints)
                val expected = vector.get("extension")
                files["references/media-blobs.jsonl"] = if (expected != null && hints.isNotEmpty()) {
                    references(NonEmptyFixture.shaImage to expected.asString)
                } else ByteArray(0)
            }
            val expectedError = vector.get("error")
            if (expectedError == null) assertEquals("hints=$hints", "PASS", report.verdict)
            else {
                assertEquals("METADATA_INCONSISTENCY", expectedError.asString)
                code(report, "MEDIA_IDENTITY_CONFLICT")
            }
        }
    }

    private fun setMediaHints(files: MutableMap<String, ByteArray>, hints: List<String?>) {
        listOf("data/physical-object-media.jsonl", "data/observation-point-attachments.jsonl").forEachIndexed { index, path ->
            edit(files, path) { row ->
                val hint = hints.getOrNull(index)
                row.add("mimeType", hint?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            }
        }
    }

    private fun setMergeMediaHints(files: MutableMap<String, ByteArray>, hints: List<String?>) {
        val rows = listOf(
            "data/physical-object-media.jsonl" to NonEmptyFixture.media,
            "data/observation-point-attachments.jsonl" to NonEmptyFixture.attachment,
            "data/physical-object-media.jsonl" to NonEmptyFixture.zeroMedia,
            "data/observation-point-attachments.jsonl" to NonEmptyFixture.incompleteAttachment
        )
        rows.forEachIndexed { index, (path, id) -> edit(files, path) { row ->
            val rowId = row.get("id").asString
            val hint = hints.getOrNull(index)
            if (rowId == id) {
                row.add("mimeType", hint?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                if (index < hints.size) {
                    row.addProperty("byteSize", 10)
                    row.addProperty("sha256", NonEmptyFixture.shaImage)
                } else {
                    row.remove("byteSize")
                    row.remove("sha256")
                }
            }
        } }
    }

    private fun references(vararg rows: Pair<String, String>): ByteArray = rows
        .sortedBy { it.first }
        .joinToString("") { (sha, extension) ->
            "{\"byteSize\":${if (sha == NonEmptyFixture.shaImage) 10 else 20},\"canonicalExtension\":\"$extension\",\"sha256\":\"$sha\"}\n"
        }.toByteArray()
    @Test fun loadedWeatherMatrixAndPendingPayloadAreEnforced() {
        assertEquals("PASS",verify { edit(it,"data/observation-point-weather.jsonl") { row ->
            row.addProperty("status","LOADED"); row.addProperty("temperatureC",-12.5); row.addProperty("windSpeedMps",0); row.addProperty("windDirectionDeg",0); row.addProperty("sampleAt",-1); row.addProperty("fetchedAt",1); row.addProperty("source","provider")
        } }.verdict)
        code(verify { edit(it,"data/observation-point-weather.jsonl") { row -> row.addProperty("temperatureC",1) } },"LOGICAL_STATE_INCONSISTENT")
    }
    @Test fun validV2GeometryAntimeridianAndExactStrings() {
        val encoded = "v2|{\"areaId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"name\":\"Area\",\"bounds\":[{\"north\":55,\"east\":-179,\"south\":55,\"west\":179}]}"
        assertEquals("PASS",verify { edit(it,"settings/map-coverage.jsonl") { row -> row.addProperty("encoded",encoded) } }.verdict)
        code(verify { edit(it,"settings/map-coverage.jsonl") { row -> row.addProperty("encoded",encoded.replace("\"Area\"","\" Area\"")) } },"MAP_COVERAGE_INVALID")
        code(verify { edit(it,"settings/map-coverage.jsonl") { row -> row.addProperty("encoded","v1| 55,37,54,36") } },"MAP_COVERAGE_INVALID")
    }
    @Test fun domainIntegerPrecisionAndLexicalTypesAreDistinctFromDouble() {
        assertEquals("PASS",verify { edit(it,"data/physical-object-media.jsonl") { row -> row.addProperty("createdAt",Long.MAX_VALUE) } }.verdict)
        code(verify { files -> files["data/physical-object-media.jsonl"] = files.getValue("data/physical-object-media.jsonl").toString(Charsets.UTF_8).replace("\"createdAt\":1000","\"createdAt\":1e3").toByteArray() },"WIRE_SCHEMA_INVALID")
        code(verify { files -> files["data/physical-objects.jsonl"] = files.getValue("data/physical-objects.jsonl").toString(Charsets.UTF_8).replace("\"sequenceNumber\":1","\"sequenceNumber\":1.0").toByteArray() },"WIRE_SCHEMA_INVALID")
    }
    @Test fun missingSubtypeSequenceRegressionAndLifecycleRejectInvalidState() {
        code(verify { it["data/hollows.jsonl"] = ByteArray(0) },"LOGICAL_STATE_INCONSISTENT")
        code(verify { edit(it,"data/physical-object-sequences.jsonl") { row -> row.addProperty("lastIssued",0) } },"LOGICAL_STATE_INCONSISTENT")
        code(verify { edit(it,"data/flight-cycles.jsonl") { row -> row.addProperty("sequenceNumber",2) } },"LOGICAL_STATE_INCONSISTENT")
        code(verify { edit(it,"data/observation-points.jsonl") { row -> row.addProperty("beePresenceResult","NO_BEES_FOUND") } },"LOGICAL_STATE_INCONSISTENT")
    }

    @Test fun allThirteenCollectionsEnforceTheirDocumentedOrderKeys() {
        for (path in Contract.paths.filter { it.startsWith("data/") }) {
            code(verify { files ->
                val original = JsonParser.parseString(files.getValue(path).toString(Charsets.UTF_8).lineSequence().first()).asJsonObject
                val higher = original.deepCopy()
                val keys = Contract.keys[path] ?: listOf("id")
                higher.addProperty(keys.first(),"ffffffff-ffff-ffff-ffff-ffffffffffff")
                files[path] = (higher.toString()+"\n"+original.toString()+"\n").toByteArray()
            },"RECORD_ORDER_INVALID")
        }
    }
    @Test fun missingNullableFieldIsNotNullAndOptionalIdentityIsTheOnlyException() {
        for ((path,bytes) in NonEmptyFixture.entries().filterKeys { it.startsWith("data/") }) {
            for (line in bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }) {
                val base = JsonParser.parseString(line).asJsonObject
                for (field in base.entrySet().filter { it.value.isJsonNull }.map { it.key }) {
                    val changed = base.deepCopy().also { it.remove(field) }
                    if (field in setOf("sha256","byteSize")) RecordSchema.validate(changed,path)
                    else assertEquals("WIRE_SCHEMA_INVALID",assertThrows(CheckFailure::class.java) { RecordSchema.validate(changed,path) }.issue.code)
                }
            }
        }
    }
    @Test fun mediaSizeCannotBeFractionalExponentStringOrOverflowLong() {
        for (raw in listOf("10.0","1e1","\"10\"","9223372036854775808")) {
            code(verify { files -> files["data/physical-object-media.jsonl"] = files.getValue("data/physical-object-media.jsonl").toString(Charsets.UTF_8).replace("\"byteSize\":10","\"byteSize\":$raw").toByteArray() },"WIRE_SCHEMA_INVALID")
        }
    }
    @Test fun coverageAndReferenceOrderingAlsoFailClosed() {
        code(verify { files ->
            val row = JsonParser.parseString(files.getValue("data/territories.jsonl").toString(Charsets.UTF_8)).asJsonObject
            val second = row.deepCopy().also { it.addProperty("id","ffffffff-ffff-ffff-ffff-ffffffffffff"); it.addProperty("code","Second") }
            files["data/territories.jsonl"] = (row.toString()+"\n"+second.toString()+"\n").toByteArray()
            files["settings/map-coverage.jsonl"] = "{\"territoryId\":\"ffffffff-ffff-ffff-ffff-ffffffffffff\",\"encoded\":\"v1\"}\n{\"territoryId\":\"${NonEmptyFixture.territory}\",\"encoded\":\"v1\"}\n".toByteArray()
        },"RECORD_ORDER_INVALID")
        code(verify { files -> files["references/media-blobs.jsonl"] = files.getValue("references/media-blobs.jsonl").toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }.reversed().joinToString("\n",postfix="\n").toByteArray() },"RECORD_ORDER_INVALID")
    }
}
