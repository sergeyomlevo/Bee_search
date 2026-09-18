package org.beesearch.app.ui.map

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Persisted representation of the Ареал of one Territory.
 *
 * The value keeps living in the existing `map_coverage_<territoryId>` DataStore entry, so the
 * existing backup settings section transports it unchanged:
 *
 * ```text
 * v1|north,east,south,west|...                 legacy unnamed участки
 * v2|{"areaId":…,"name":…,"bounds":[…]}
 * ```
 *
 * The codec is pure: it never generates an id, never resolves a name and performs no I/O, so both
 * migration and validation stay testable without a device.
 */
internal object MapAreaCodec {
    const val LEGACY_VERSION = "v1"
    const val AREA_VERSION = "v2"

    /** The legacy encoding of an empty selection, written by earlier versions. */
    const val EMPTY_LEGACY_VALUE = LEGACY_VERSION

    private const val AREA_ID = "areaId"
    private const val NAME = "name"
    private const val BOUNDS = "bounds"
    private const val NORTH = "north"
    private const val EAST = "east"
    private const val SOUTH = "south"
    private const val WEST = "west"

    private val areaFields = setOf(AREA_ID, NAME, BOUNDS)
    private val boundFields = setOf(NORTH, EAST, SOUTH, WEST)

    private val json = Json
    private val elementSerializer = JsonElement.serializer()

    fun encode(area: MapArea): String {
        val payload = buildJsonObject {
            put(AREA_ID, JsonPrimitive(area.id.toString()))
            put(NAME, JsonPrimitive(area.name))
            put(
                BOUNDS,
                buildJsonArray {
                    area.bounds.forEach { bound ->
                        add(
                            buildJsonObject {
                                put(NORTH, JsonPrimitive(bound.north))
                                put(EAST, JsonPrimitive(bound.east))
                                put(SOUTH, JsonPrimitive(bound.south))
                                put(WEST, JsonPrimitive(bound.west))
                            },
                        )
                    }
                },
            )
        }
        return "$AREA_VERSION|" + json.encodeToString(elementSerializer, payload)
    }

    /**
     * Legacy encoding of участки without an Ареал.
     *
     * PHASE B: kept only so first creation can stay unchanged until the name dialog exists.
     */
    fun encodeLegacy(bounds: List<MapGeoBounds>): String = buildString {
        append(LEGACY_VERSION)
        bounds.forEach { bound ->
            append('|').append(bound.north).append(',').append(bound.east)
                .append(',').append(bound.south).append(',').append(bound.west)
        }
    }

    /**
     * Reads a persisted value. A damaged value is reported as [MapAreaReadResult.Corrupt] and is
     * never reported as [MapAreaReadResult.Absent].
     */
    fun decode(value: String?): MapAreaReadResult {
        if (value == null) return MapAreaReadResult.Absent
        return when {
            value == EMPTY_LEGACY_VALUE -> MapAreaReadResult.Absent
            value.startsWith("$LEGACY_VERSION|") -> decodeLegacy(value)
            value.startsWith("$AREA_VERSION|") -> decodeArea(value.removePrefix("$AREA_VERSION|"))
            else -> corrupt("неизвестный формат ареала")
        }
    }

    private fun decodeLegacy(value: String): MapAreaReadResult = guard {
        val bounds = value.split('|').drop(1).map { fragment ->
            val numbers = fragment.split(',').map(String::toDouble)
            if (numbers.size != 4) throw IllegalArgumentException("участок должен содержать 4 координаты")
            MapGeoBounds(
                north = numbers[0],
                east = numbers[1],
                south = numbers[2],
                west = numbers[3],
            )
        }
        if (bounds.isEmpty()) MapAreaReadResult.Absent else MapAreaReadResult.Legacy(bounds)
    }

    private fun decodeArea(payload: String): MapAreaReadResult = guard {
        val root = json.parseToJsonElement(payload).jsonObject
        if (root.keys != areaFields) throw IllegalArgumentException("неожиданный набор полей ареала")
        val id = UUID.fromString(root.getValue(AREA_ID).jsonPrimitive.content)
        val name = root.getValue(NAME).jsonPrimitive.content.trim()
        val bounds = root.getValue(BOUNDS).jsonArray.map { element ->
            val bound = element.jsonObject
            if (bound.keys != boundFields) throw IllegalArgumentException("неожиданный набор полей участка")
            MapGeoBounds(
                north = bound.getValue(NORTH).jsonPrimitive.double,
                east = bound.getValue(EAST).jsonPrimitive.double,
                south = bound.getValue(SOUTH).jsonPrimitive.double,
                west = bound.getValue(WEST).jsonPrimitive.double,
            )
        }
        MapAreaReadResult.Present(MapArea(id = id, name = name, bounds = bounds))
    }

    /** Turns any parse, structure or invariant failure into an explicit corrupt outcome. */
    private fun guard(block: () -> MapAreaReadResult): MapAreaReadResult = try {
        block()
    } catch (error: Exception) {
        corrupt(error.message ?: error::class.java.simpleName)
    }

    private fun corrupt(reason: String): MapAreaReadResult = MapAreaReadResult.Corrupt(reason)
}
