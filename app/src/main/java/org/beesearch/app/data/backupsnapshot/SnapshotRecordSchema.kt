package org.beesearch.app.data.backupsnapshot

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Closed, lexical V1 wire schemas for JSONL records and portable settings. */
internal object SnapshotRecordSchema {
    private enum class Kind { S, U, I, L, T, D, B, E, H, DATE }
    private data class Field(val kind: Kind, val nullable: Boolean = false, val optional: Boolean = false)

    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val sha = Regex("[0-9a-f]{64}")
    private val integer = Regex("-?(0|[1-9][0-9]*)")

    private fun f(kind: Kind, nullable: Boolean = false) = Field(kind, nullable)
    private fun o(kind: Kind) = Field(kind, nullable = true, optional = true)
    private fun schema(vararg fields: Pair<String, Field>) = fields.toMap()

    private val schemas = mapOf(
        "data/territories.jsonl" to schema(
            "id" to f(Kind.U), "code" to f(Kind.S), "name" to f(Kind.S), "region" to f(Kind.S),
            "district" to f(Kind.S), "createdAt" to f(Kind.T), "updatedAt" to f(Kind.T)),
        "data/observers.jsonl" to schema(
            "id" to f(Kind.U), "code" to f(Kind.S), "lastName" to f(Kind.S), "firstName" to f(Kind.S),
            "middleName" to f(Kind.S, true), "contact" to f(Kind.S, true), "createdAt" to f(Kind.T), "updatedAt" to f(Kind.T)),
        "data/physical-objects.jsonl" to schema(
            "id" to f(Kind.U), "territoryId" to f(Kind.U), "objectType" to f(Kind.E),
            "sequenceNumber" to f(Kind.I), "latitude" to f(Kind.D), "longitude" to f(Kind.D),
            "createdAt" to f(Kind.T), "creatorObserverId" to f(Kind.U, true)),
        "data/apiaries.jsonl" to schema("physicalObjectId" to f(Kind.U), "name" to f(Kind.S, true)),
        "data/hollows.jsonl" to schema(
            "physicalObjectId" to f(Kind.U), "tree" to f(Kind.S, true), "entranceHeightCm" to f(Kind.D, true),
            "entranceAzimuthDeg" to f(Kind.I, true), "outerDiameterCm" to f(Kind.D, true),
            "internalDiameterCm" to f(Kind.D, true), "notes" to f(Kind.S, true), "name" to f(Kind.S, true)),
        "data/log-hives.jsonl" to schema(
            "physicalObjectId" to f(Kind.U), "tree" to f(Kind.S, true), "entranceHeightCm" to f(Kind.D, true),
            "entranceAzimuthDeg" to f(Kind.I, true), "outerDiameterCm" to f(Kind.D, true),
            "material" to f(Kind.S, true), "internalDiameterCm" to f(Kind.D, true),
            "internalHeightCm" to f(Kind.D, true), "notes" to f(Kind.S, true), "name" to f(Kind.S, true)),
        "data/physical-object-sequences.jsonl" to schema(
            "territoryId" to f(Kind.U), "objectType" to f(Kind.E), "lastIssued" to f(Kind.I)),
        "data/physical-object-media.jsonl" to schema(
            "id" to f(Kind.U), "physicalObjectId" to f(Kind.U), "type" to f(Kind.E),
            "relativePath" to f(Kind.S), "originalFileName" to f(Kind.S, true), "mimeType" to f(Kind.S, true),
            "byteSize" to o(Kind.L), "sha256" to o(Kind.H), "createdAt" to f(Kind.T)),
        "data/observation-points.jsonl" to schema(
            "id" to f(Kind.U), "territoryId" to f(Kind.U), "observerId" to f(Kind.U),
            "observationYear" to f(Kind.I), "pointNumber" to f(Kind.I), "beePresenceResult" to f(Kind.E, true),
            "code" to f(Kind.S, true), "latitude" to f(Kind.D), "longitude" to f(Kind.D),
            "gpsLatitude" to f(Kind.D, true), "gpsLongitude" to f(Kind.D, true), "gpsAccuracyM" to f(Kind.D, true),
            "createdAt" to f(Kind.T), "initialGroupReleaseAt" to f(Kind.T, true), "completedAt" to f(Kind.T, true),
            "description" to f(Kind.S, true)),
        "data/bees.jsonl" to schema(
            "id" to f(Kind.U), "observationPointId" to f(Kind.U), "markColor" to f(Kind.S),
            "markPosition" to f(Kind.E), "createdAt" to f(Kind.T), "sourceObjectId" to f(Kind.U, true)),
        "data/flight-cycles.jsonl" to schema(
            "id" to f(Kind.U), "beeId" to f(Kind.U), "sequenceNumber" to f(Kind.I),
            "departureTime" to f(Kind.T), "returnTime" to f(Kind.T, true), "azimuthDeg" to f(Kind.D, true),
            "azimuthCaptureConsumed" to f(Kind.B), "initialGroupLaunch" to f(Kind.B),
            "initialGroupLaunchCorrectionEligible" to f(Kind.B), "createdAt" to f(Kind.T), "updatedAt" to f(Kind.T)),
        "data/observation-point-weather.jsonl" to schema(
            "observationPointId" to f(Kind.U), "status" to f(Kind.E), "temperatureC" to f(Kind.D, true),
            "windSpeedMps" to f(Kind.D, true), "windDirectionDeg" to f(Kind.D, true), "sampleAt" to f(Kind.T, true),
            "fetchedAt" to f(Kind.T, true), "source" to f(Kind.S, true)),
        "data/observation-point-attachments.jsonl" to schema(
            "id" to f(Kind.U), "observationPointId" to f(Kind.U), "type" to f(Kind.E),
            "relativePath" to f(Kind.S), "originalFileName" to f(Kind.S, true), "mimeType" to f(Kind.S, true),
            "byteSize" to o(Kind.L), "sha256" to o(Kind.H), "createdAt" to f(Kind.T)),
        "settings/map-coverage.jsonl" to schema("territoryId" to f(Kind.U), "encoded" to f(Kind.S)),
        "references/media-blobs.jsonl" to schema(
            "sha256" to f(Kind.H), "byteSize" to f(Kind.L), "canonicalExtension" to f(Kind.E)),
    )

    private val enums = mapOf(
        "objectType" to setOf("APIARY", "HOLLOW", "LOG_HIVE"),
        "type" to setOf("IMAGE", "VIDEO", "PHOTO"),
        "beePresenceResult" to setOf("BEES_FOUND", "NO_BEES_FOUND"),
        "markPosition" to setOf("THORAX", "ABDOMEN", "LEFT_WING", "NONE", "RIGHT_WING"),
        "status" to setOf("PENDING", "LOADED", "UNAVAILABLE"),
        "canonicalExtension" to setOf("jpg", "mp4", "bin"),
    )

    private val orderKeys = mapOf(
        "data/territories.jsonl" to listOf("id"), "data/observers.jsonl" to listOf("id"),
        "data/physical-objects.jsonl" to listOf("id"), "data/apiaries.jsonl" to listOf("physicalObjectId"),
        "data/hollows.jsonl" to listOf("physicalObjectId"), "data/log-hives.jsonl" to listOf("physicalObjectId"),
        "data/physical-object-sequences.jsonl" to listOf("territoryId", "objectType"),
        "data/physical-object-media.jsonl" to listOf("id"), "data/observation-points.jsonl" to listOf("id"),
        "data/bees.jsonl" to listOf("id"), "data/flight-cycles.jsonl" to listOf("id"),
        "data/observation-point-weather.jsonl" to listOf("observationPointId"),
        "data/observation-point-attachments.jsonl" to listOf("id"),
        "settings/map-coverage.jsonl" to listOf("territoryId"), "references/media-blobs.jsonl" to listOf("sha256"),
    )

    fun validateRows(path: String, rows: List<JsonObject>, version: Int = 1) {
        if (version !in SnapshotContract.supportedVersions) invalid("VERSION")
        val legacy = schemas[path] ?: invalid("UNKNOWN_RECORD_PATH")
        val expected = if (version >= 2) when (path) {
            "data/observation-points.jsonl" -> legacy + ("observationDate" to f(Kind.DATE))
            "data/physical-objects.jsonl" -> legacy + ("fixationDate" to f(Kind.DATE, true)) +
                if (version >= 3) listOf("fixationAt" to f(Kind.T, true), "updatedAt" to f(Kind.T, true)) else emptyList()
            else -> legacy
        } else legacy
        var previous: List<String>? = null
        rows.forEach { row ->
            validateObject(path, row, expected)
            val key = orderKeys.getValue(path).map { (row[it] as JsonPrimitive).content }
            if (previous != null && compareKey(previous!!, key) >= 0) logical("RECORD_ORDER:$path")
            previous = key
        }
    }

    fun validatePortable(row: JsonObject) {
        validateObject("settings/portable.json", row, schema("currentTerritoryId" to f(Kind.U, true), "currentObserverId" to f(Kind.U, true)))
    }

    private fun validateObject(path: String, row: JsonObject, expected: Map<String, Field>) {
        if (!row.keys.all { it in expected.keys }) invalid("FIELDS")
        expected.forEach { (name, field) ->
            val value = row[name]
            if (value == null) { if (!field.optional) invalid("MISSING_$name"); return@forEach }
            if (value === JsonNull) { if (!field.nullable) invalid("NULL_$name"); return@forEach }
            validateValue(path, name, value, field.kind)
        }
    }

    private fun validateValue(path: String, name: String, value: JsonElement, kind: Kind) {
        val primitive = value as? JsonPrimitive ?: invalid("TYPE_$name")
        when (kind) {
            Kind.DATE -> {
                if (!primitive.isString) invalid("TYPE_$name")
                try { org.beesearch.app.data.backup.parseCanonicalResearchDate(primitive.content) }
                catch (_: org.beesearch.app.domain.backup.MalformedBackup) { invalid("DATE_$name") }
            }
            Kind.S -> if (!primitive.isString) invalid("TYPE_$name")
            Kind.U -> if (!primitive.isString || !uuid.matches(primitive.content)) invalid("UUID_$name")
            Kind.H -> if (!primitive.isString || !sha.matches(primitive.content)) invalid("SHA_$name")
            Kind.I -> integer(primitive, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong(), name)
            Kind.L, Kind.T -> integer(primitive, Long.MIN_VALUE, Long.MAX_VALUE, name)
            Kind.D -> if (primitive.isString || primitive.content.toDoubleOrNull()?.isFinite() != true) invalid("DOUBLE_$name")
            Kind.B -> if (primitive.isString || primitive.content !in setOf("true", "false")) invalid("BOOLEAN_$name")
            Kind.E -> if (!primitive.isString || primitive.content !in enumValues(path, name)) invalid("ENUM_$name")
        }
    }

    private fun integer(value: JsonPrimitive, min: Long, max: Long, name: String) {
        if (value.isString || !integer.matches(value.content)) invalid("INTEGER_$name")
        val parsed = value.content.toLongOrNull() ?: invalid("INTEGER_$name")
        if (parsed !in min..max) invalid("RANGE_$name")
    }

    private fun enumValues(path: String, name: String): Set<String> = when {
        name == "type" && path.endsWith("physical-object-media.jsonl") -> setOf("IMAGE", "VIDEO")
        name == "type" && path.endsWith("observation-point-attachments.jsonl") -> setOf("PHOTO")
        else -> enums[name] ?: emptySet()
    }

    private fun compareKey(a: List<String>, b: List<String>): Int {
        for (i in a.indices) { val result = a[i].compareTo(b[i]); if (result != 0) return result }
        return 0
    }

    private fun invalid(category: String): Nothing = throw SnapshotException(SnapshotError.INVALID_FORMAT, category)
    private fun logical(category: String): Nothing = throw SnapshotException(SnapshotError.LOGICAL_STATE_INCONSISTENT, category)
}
