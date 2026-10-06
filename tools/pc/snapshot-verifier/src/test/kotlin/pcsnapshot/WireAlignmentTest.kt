package pcsnapshot

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
    @Test fun mimeHintIsNotTrimmedOrCaseFolded() {
        assertEquals("PASS",verify { files ->
            for (path in listOf("data/physical-object-media.jsonl","data/observation-point-attachments.jsonl")) edit(files,path) { it.addProperty("mimeType"," IMAGE/JPEG ") }
            files["references/media-blobs.jsonl"] = files.getValue("references/media-blobs.jsonl").toString(Charsets.UTF_8).replace("jpg","bin").toByteArray()
        }.verdict)
    }
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
