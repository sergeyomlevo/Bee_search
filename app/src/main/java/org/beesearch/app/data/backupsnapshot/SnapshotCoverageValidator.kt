package org.beesearch.app.data.backupsnapshot

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Validates the opaque map-coverage value at the snapshot wire boundary. */
internal object SnapshotCoverageValidator {
    private val number = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    private val areaFields = setOf("areaId", "name", "bounds")
    private val boundFields = setOf("north", "east", "south", "west")

    fun validate(encoded: String, limits: SnapshotLimits = SnapshotLimits()) {
        if (encoded.length > limits.stringUnits) {
            throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, "MAP_COVERAGE", encoded.length.toLong(), limits.stringUnits.toLong())
        }
        if (encoded.any { it.isHighSurrogate() || it.isLowSurrogate() }) {
            // An unpaired surrogate cannot be a valid wire string. Paired surrogates are fine.
            var i = 0
            while (i < encoded.length) {
                val c = encoded[i]
                if (c.isHighSurrogate()) {
                    if (i + 1 == encoded.length || !encoded[i + 1].isLowSurrogate()) invalid()
                    i += 2
                } else {
                    if (c.isLowSurrogate()) invalid()
                    i++
                }
            }
        }
        try {
            when {
                encoded == "v1" -> Unit
                encoded.startsWith("v1|") -> validateV1(encoded)
                encoded.startsWith("v2|") -> validateV2(encoded.substring(3), limits)
                else -> invalid()
            }
        } catch (error: SnapshotException) {
            if (error.error == SnapshotError.SNAPSHOT_LIMIT_EXCEEDED) throw error
            invalid(error)
        } catch (error: Exception) {
            invalid(error)
        }
    }

    private fun validateV1(encoded: String) {
        val fragments = encoded.substring(3).split('|')
        if (fragments.isEmpty() || fragments.any { it.isEmpty() }) invalid()
        fragments.forEach { fragment ->
            val values = fragment.split(',')
            if (values.size != 4 || values.any { !number.matches(it) }) invalid()
            val coordinates = values.map { it.toDouble().also { value -> if (!value.isFinite()) invalid() } }
            validateBounds(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
        }
    }

    private fun validateV2(payload: String, limits: SnapshotLimits) {
        val root = SnapshotJson.parse(payload.toByteArray(Charsets.UTF_8), integerOnly = false, limits = limits) as? JsonObject ?: invalid()
        if (root.keys != areaFields) invalid()
        val areaId = (root["areaId"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
        try {
            if (UUID.fromString(areaId).toString() != areaId) invalid()
        } catch (_: IllegalArgumentException) { invalid() }
        val name = (root["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
        if (name.isEmpty() || isEdgeWhitespace(name.first()) || isEdgeWhitespace(name.last())) invalid()
        val bounds = root["bounds"] as? JsonArray ?: invalid()
        if (bounds.isEmpty()) invalid()
        bounds.forEach { element ->
            val bound = element as? JsonObject ?: invalid()
            if (bound.keys != boundFields) invalid()
            val north = decimal(bound["north"])
            val east = decimal(bound["east"])
            val south = decimal(bound["south"])
            val west = decimal(bound["west"])
            validateBounds(north, east, south, west)
        }
    }

    private fun decimal(element: kotlinx.serialization.json.JsonElement?): Double =
        (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
            ?.takeIf { it.isFinite() } ?: invalid()

    private fun validateBounds(north: Double, east: Double, south: Double, west: Double) {
        if (north !in -90.0..90.0 || south !in -90.0..90.0 || east !in -180.0..180.0 || west !in -180.0..180.0 || north < south) invalid()
    }

    private fun isEdgeWhitespace(c: Char): Boolean = when (c.code) {
        in 0x0009..0x000D, in 0x001C..0x0020, 0x00A0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }

    private fun invalid(cause: Throwable? = null): Nothing =
        throw SnapshotException(SnapshotError.LOGICAL_STATE_INCONSISTENT, "MAP_COVERAGE", cause = cause)
}
