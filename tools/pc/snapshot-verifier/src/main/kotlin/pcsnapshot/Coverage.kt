package pcsnapshot

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Exact validation of the persisted v1/v2 area-selection string. */
internal object Coverage {
    private val decimal = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun validate(encoded: String, scope: String) {
        if (encoded.length > Contract.STRING) reject("MAP_COVERAGE_INVALID", scope)
        if (encoded == "v1") return
        when {
            encoded.startsWith("v1|") -> validateV1(encoded.substring(3), scope)
            encoded.startsWith("v2|") -> validateV2(encoded.substring(3), scope)
            else -> reject("MAP_COVERAGE_INVALID", scope)
        }
    }

    private fun validateV1(body: String, scope: String) {
        if (body.isEmpty()) reject("MAP_COVERAGE_INVALID", scope)
        body.split('|').forEach { fragment ->
            val values = fragment.split(',')
            if (values.size != 4 || values.any { !decimal.matches(it) }) reject("MAP_COVERAGE_INVALID", scope)
            val numbers = values.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: reject("MAP_COVERAGE_INVALID", scope) }
            rectangle(numbers[0], numbers[1], numbers[2], numbers[3], scope)
        }
    }

    private fun validateV2(body: String, scope: String) {
        val root = try { SafeJson.parse(body.toByteArray(Charsets.UTF_8)) } catch (e: CheckFailure) {
            throw CheckFailure(e.issue.copy(code = "MAP_COVERAGE_INVALID", scope = scope))
        }
        if (!root.isJsonObject) reject("MAP_COVERAGE_INVALID", scope)
        val obj = root.asJsonObject
        if (obj.keySet() != setOf("areaId", "name", "bounds")) reject("MAP_COVERAGE_INVALID", scope)
        val area = obj.get("areaId")
        if (area == null || !area.isJsonPrimitive || !area.asJsonPrimitive.isString) reject("MAP_COVERAGE_INVALID", scope)
        RecordSchema.uuid(area.asString, scope)
        val name = obj.get("name")
        if (name == null || !name.isJsonPrimitive || !name.asJsonPrimitive.isString || !RecordSchema.nonblank(name.asString) || !RecordSchema.noEdgeWhitespace(name.asString)) reject("MAP_COVERAGE_INVALID", scope)
        val bounds = obj.get("bounds")
        if (bounds == null || !bounds.isJsonArray || bounds.asJsonArray.size() == 0) reject("MAP_COVERAGE_INVALID", scope)
        bounds.asJsonArray.forEach { item ->
            if (!item.isJsonObject) reject("MAP_COVERAGE_INVALID", scope)
            val box = item.asJsonObject
            if (box.keySet() != setOf("north", "east", "south", "west")) reject("MAP_COVERAGE_INVALID", scope)
            val n = coordinate(box, "north", scope); val e = coordinate(box, "east", scope)
            val s = coordinate(box, "south", scope); val w = coordinate(box, "west", scope)
            rectangle(n, e, s, w, scope)
        }
    }

    private fun coordinate(obj: JsonObject, key: String, scope: String): Double {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber || !value.asDouble.isFinite()) reject("MAP_COVERAGE_INVALID", scope)
        return value.asDouble
    }

    private fun rectangle(north: Double, east: Double, south: Double, west: Double, scope: String) {
        if (north !in -90.0..90.0 || south !in -90.0..90.0 || east !in -180.0..180.0 || west !in -180.0..180.0 || north < south)
            reject("MAP_COVERAGE_INVALID", scope)
    }
}
