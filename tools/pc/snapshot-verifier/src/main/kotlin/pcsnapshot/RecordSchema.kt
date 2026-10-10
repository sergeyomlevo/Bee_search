package pcsnapshot

import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Closed, lexical wire schemas. Relationship and range checks belong to GraphRules. */
internal object RecordSchema {
    private val schemas = mapOf(
        "territories" to listOf("id", "code", "name", "region", "district", "createdAt", "updatedAt"),
        "observers" to listOf("id", "code", "lastName", "firstName", "middleName", "contact", "createdAt", "updatedAt"),
        "physical-objects" to listOf("id", "territoryId", "objectType", "sequenceNumber", "latitude", "longitude", "createdAt", "creatorObserverId"),
        "apiaries" to listOf("physicalObjectId", "name"),
        "hollows" to listOf("physicalObjectId", "tree", "entranceHeightCm", "entranceAzimuthDeg", "outerDiameterCm", "internalDiameterCm", "notes", "name"),
        "log-hives" to listOf("physicalObjectId", "tree", "entranceHeightCm", "entranceAzimuthDeg", "outerDiameterCm", "material", "internalDiameterCm", "internalHeightCm", "notes", "name"),
        "physical-object-sequences" to listOf("territoryId", "objectType", "lastIssued"),
        "physical-object-media" to listOf("id", "physicalObjectId", "type", "relativePath", "originalFileName", "mimeType", "byteSize", "sha256", "createdAt"),
        "observation-points" to listOf("id", "territoryId", "observerId", "observationYear", "pointNumber", "beePresenceResult", "code", "latitude", "longitude", "gpsLatitude", "gpsLongitude", "gpsAccuracyM", "createdAt", "initialGroupReleaseAt", "completedAt", "description"),
        "bees" to listOf("id", "observationPointId", "markColor", "markPosition", "createdAt", "sourceObjectId"),
        "flight-cycles" to listOf("id", "beeId", "sequenceNumber", "departureTime", "returnTime", "azimuthDeg", "azimuthCaptureConsumed", "initialGroupLaunch", "initialGroupLaunchCorrectionEligible", "createdAt", "updatedAt"),
        "observation-point-weather" to listOf("observationPointId", "status", "temperatureC", "windSpeedMps", "windDirectionDeg", "sampleAt", "fetchedAt", "source"),
        "observation-point-attachments" to listOf("id", "observationPointId", "type", "relativePath", "originalFileName", "mimeType", "byteSize", "sha256", "createdAt")
    )

    fun validate(row: JsonObject, path: String, version: Long = 1L) {
        val kind = path.substringAfterLast('/').removeSuffix(".jsonl")
        val fields = schemas[kind]?.let { base ->
            if (version >= 2L && kind == "observation-points") base + "observationDate"
            else if (version == 2L && kind == "physical-objects") base + "fixationDate"
            else if (version >= 3L && kind == "physical-objects") base + "fixationDate" + "fixationAt" + "updatedAt"
            else base
        } ?: reject("WIRE_SCHEMA_INVALID", path)
        val optional = if (kind == "physical-object-media" || kind == "observation-point-attachments") setOf("sha256", "byteSize") else emptySet()
        if (row.keySet().any { it !in fields } || fields.filter { it !in optional }.any { !row.has(it) }) {
            reject("WIRE_SCHEMA_INVALID", path)
        }
        fun u(key: String) = uuid(string(row, key, path), path)
        fun s(key: String, nullable: Boolean = false) { if (nullable) nullableString(row, key) else string(row, key) }
        fun l(key: String, nullable: Boolean = false) { if (nullable) nullableLong(row, key) else long(row, key) }
        fun d(key: String, nullable: Boolean = false) { if (nullable) nullableDouble(row, key) else double(row, key) }
        when (kind) {
            "territories" -> { u("id"); s("code"); s("name"); s("region"); s("district"); l("createdAt"); l("updatedAt") }
            "observers" -> { u("id"); s("code"); s("lastName"); s("firstName"); s("middleName", true); s("contact", true); l("createdAt"); l("updatedAt") }
            "physical-objects" -> { u("id"); u("territoryId"); enum(row, "objectType", setOf("APIARY", "HOLLOW", "LOG_HIVE"), path); intField(row, "sequenceNumber", path); d("latitude"); d("longitude"); l("createdAt"); uuidNullable(row, "creatorObserverId", path); if (version >= 2L) dateNullable(row, "fixationDate", path); if (version >= 3L) { nullableLong(row, "fixationAt"); nullableLong(row, "updatedAt") } }
            "apiaries" -> { u("physicalObjectId"); s("name", true) }
            "hollows" -> { u("physicalObjectId"); s("tree", true); d("entranceHeightCm", true); l("entranceAzimuthDeg", true); d("outerDiameterCm", true); d("internalDiameterCm", true); s("notes", true); s("name", true) }
            "log-hives" -> { u("physicalObjectId"); s("tree", true); d("entranceHeightCm", true); l("entranceAzimuthDeg", true); d("outerDiameterCm", true); s("material", true); d("internalDiameterCm", true); d("internalHeightCm", true); s("notes", true); s("name", true) }
            "physical-object-sequences" -> { u("territoryId"); enum(row, "objectType", setOf("APIARY", "HOLLOW", "LOG_HIVE"), path); intField(row, "lastIssued", path) }
            "physical-object-media" -> media(row, path, "physicalObjectId")
            "observation-points" -> { u("id"); u("territoryId"); u("observerId"); intField(row, "observationYear", path); intField(row, "pointNumber", path); enumNullable(row, "beePresenceResult", setOf("BEES_FOUND", "NO_BEES_FOUND"), path); s("code", true); d("latitude"); d("longitude"); d("gpsLatitude", true); d("gpsLongitude", true); d("gpsAccuracyM", true); l("createdAt"); l("initialGroupReleaseAt", true); l("completedAt", true); s("description", true); if (version >= 2L) date(row, "observationDate", path) }
            "bees" -> { u("id"); u("observationPointId"); s("markColor"); enum(row, "markPosition", setOf("THORAX", "ABDOMEN", "LEFT_WING", "NONE", "RIGHT_WING"), path); l("createdAt"); uuidNullable(row, "sourceObjectId", path) }
            "flight-cycles" -> { u("id"); u("beeId"); intField(row, "sequenceNumber", path); l("departureTime"); l("returnTime", true); d("azimuthDeg", true); bool(row, "azimuthCaptureConsumed"); bool(row, "initialGroupLaunch"); bool(row, "initialGroupLaunchCorrectionEligible"); l("createdAt"); l("updatedAt") }
            "observation-point-weather" -> { u("observationPointId"); enum(row, "status", setOf("PENDING", "LOADED", "UNAVAILABLE"), path); d("temperatureC", true); d("windSpeedMps", true); d("windDirectionDeg", true); l("sampleAt", true); l("fetchedAt", true); s("source", true) }
            "observation-point-attachments" -> media(row, path, "observationPointId")
        }
    }

    private fun media(row: JsonObject, path: String, owner: String) {
        uuid(string(row, "id"), path); uuid(string(row, owner), path)
        enum(row, "type", if (owner == "physicalObjectId") setOf("IMAGE", "VIDEO") else setOf("PHOTO"), path)
        string(row, "relativePath"); nullableString(row, "originalFileName"); nullableString(row, "mimeType")
        if (row.has("byteSize")) nullableLong(row, "byteSize")
        if (row.has("sha256")) nullableString(row, "sha256")?.let { sha(it, path) }
        long(row, "createdAt")
    }

    fun string(row: JsonObject, key: String): String = value(row, key, false, "").asString
    private fun string(row: JsonObject, key: String, scope: String): String = value(row, key, false, scope).asString
    fun long(row: JsonObject, key: String): Long = integer(row, key, false, "", Long.MIN_VALUE..Long.MAX_VALUE)
    fun nullableLong(row: JsonObject, key: String): Long? = if (isNull(row, key)) null else integer(row, key, true, "", Long.MIN_VALUE..Long.MAX_VALUE)
    fun double(row: JsonObject, key: String): Double = number(row, key, false, "").asDouble
    fun nullableDouble(row: JsonObject, key: String): Double? = if (isNull(row, key)) null else number(row, key, true, "").asDouble
    fun nullableString(row: JsonObject, key: String): String? = if (isNull(row, key)) null else value(row, key, true, "").asString
    fun bool(row: JsonObject, key: String): Boolean {
        val value = row.get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) reject("WIRE_SCHEMA_INVALID")
        return value.asBoolean
    }

    fun uuid(value: String, scope: String) { if (!Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(value)) reject("UUID_INVALID", scope) }
    fun sha(value: String, scope: String) { if (!Regex("[0-9a-f]{64}").matches(value)) reject("SHA_INVALID", scope) }
    fun nonblank(value: String): Boolean = value.any { !isWireWhitespace(it) }
    fun noEdgeWhitespace(value: String): Boolean = value.isEmpty() || (!isWireWhitespace(value.first()) && !isWireWhitespace(value.last()))

    private fun uuidNullable(row: JsonObject, key: String, scope: String) { nullableString(row, key)?.let { uuid(it, scope) } }
    private fun intField(row: JsonObject, key: String, scope: String) { integer(row, key, false, scope, Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }
    private fun enum(row: JsonObject, key: String, allowed: Set<String>, scope: String) { if (string(row, key) !in allowed) reject("WIRE_SCHEMA_INVALID", scope) }
    private fun enumNullable(row: JsonObject, key: String, allowed: Set<String>, scope: String) { nullableString(row, key)?.let { if (it !in allowed) reject("WIRE_SCHEMA_INVALID", scope) } }
    private fun date(row: JsonObject, key: String, scope: String) { validateDate(string(row, key), scope) }
    private fun dateNullable(row: JsonObject, key: String, scope: String) { nullableString(row, key)?.let { validateDate(it, scope) } }
    private fun validateDate(value: String, scope: String) {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) reject("WIRE_SCHEMA_INVALID", scope)
        try { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }
        catch (_: DateTimeParseException) { reject("WIRE_SCHEMA_INVALID", scope) }
    }
    private fun isNull(row: JsonObject, key: String): Boolean = row.get(key)?.isJsonNull == true
    private fun value(row: JsonObject, key: String, nullable: Boolean, scope: String) = row.get(key)?.let { if (it.isJsonNull && nullable) it else if (!it.isJsonNull && it.isJsonPrimitive && it.asJsonPrimitive.isString) it else reject("WIRE_SCHEMA_INVALID", scope) } ?: reject("WIRE_SCHEMA_INVALID", scope)
    private fun integer(row: JsonObject, key: String, nullable: Boolean, scope: String, range: LongRange): Long {
        val element = row.get(key) ?: reject("WIRE_SCHEMA_INVALID", scope)
        if (element.isJsonNull && nullable) return 0L
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) reject("WIRE_SCHEMA_INVALID", scope)
        val raw = element.asString
        if (!Regex("-?(0|[1-9][0-9]*)").matches(raw)) reject("WIRE_SCHEMA_INVALID", scope)
        return raw.toLongOrNull()?.takeIf { it in range } ?: reject("WIRE_SCHEMA_INVALID", scope)
    }
    private fun number(row: JsonObject, key: String, nullable: Boolean, scope: String) = row.get(key)?.let { if (it.isJsonNull && nullable) return@let com.google.gson.JsonPrimitive(0.0); if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber || !it.asDouble.isFinite()) reject("WIRE_SCHEMA_INVALID", scope); it } ?: reject("WIRE_SCHEMA_INVALID", scope)
    private fun isWireWhitespace(c: Char): Boolean = c.code in 0x09..0x0D || c.code in 0x1C..0x20 || c.code == 0x00A0 || c.code == 0x1680 || c.code in 0x2000..0x200A || c.code == 0x2028 || c.code == 0x2029 || c.code == 0x202F || c.code == 0x205F || c.code == 0x3000
}
