package org.beesearch.app.data.exchange

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapGeoBounds

/**
 * The portable Area file: the user-facing representation of one whole Ареал.
 *
 * It is deliberately a *separate* contract from the device-local `MapAreaCodec` value in DataStore.
 * The DataStore value is private storage that may change its encoding whenever that is useful; this
 * file is what leaves the device (today through the Android share sheet, later to a server), so its
 * shape is a compatibility promise:
 *
 * ```json
 * {
 *   "formatVersion": 1,
 *   "areaId": "7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d",
 *   "name": "Лух",
 *   "bounds": [
 *     { "north": 57.111673, "east": 39.026918, "south": 56.562186, "west": 38.470994 }
 *   ]
 * }
 * ```
 *
 * The file carries the whole Ареал: its stable id, its human-readable name and every участок in the
 * stored order. Nothing device-local enters it - no territory id, no build variant, no storage path,
 * no active map package, no UI state, no timestamps and no derived value. The map generator can
 * compute its own outer rectangle from the участки, so no outer bbox is written either.
 */
internal object AreaExchangeCodec {
    const val FORMAT_VERSION = 1

    private const val FORMAT_VERSION_FIELD = "formatVersion"
    private const val AREA_ID = "areaId"
    private const val NAME = "name"
    private const val BOUNDS = "bounds"
    private const val NORTH = "north"
    private const val EAST = "east"
    private const val SOUTH = "south"
    private const val WEST = "west"

    private val areaFields = setOf(FORMAT_VERSION_FIELD, AREA_ID, NAME, BOUNDS)
    private val boundFields = setOf(NORTH, EAST, SOUTH, WEST)

    private val json = Json {
        // Readable on purpose: the file is meant to be opened, mailed and inspected by a human.
        // Field order and the exact indentation are not part of the contract - the fields and their
        // values are.
        prettyPrint = true
    }
    private val elementSerializer = JsonElement.serializer()

    /** The exact text of the Area file, including the trailing newline. */
    fun encode(area: MapArea): String {
        val payload = buildJsonObject {
            put(FORMAT_VERSION_FIELD, JsonPrimitive(FORMAT_VERSION))
            put(AREA_ID, JsonPrimitive(area.id.toString()))
            put(NAME, JsonPrimitive(area.name))
            put(
                BOUNDS,
                buildJsonArray {
                    area.bounds.forEach { bound ->
                        add(
                            buildJsonObject {
                                // The stored field order, so an Ареал on disk looks the same as the
                                // documented example and a diff between two files stays readable.
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
        return json.encodeToString(elementSerializer, payload) + "\n"
    }

    /**
     * Reads an Area file. Every structural, type, range or invariant failure is reported as
     * [AreaExchangeReadResult.Invalid] with a reason, never as a half-built Ареал.
     */
    fun decode(text: String): AreaExchangeReadResult = try {
        val root = json.parseToJsonElement(text).jsonObject
        if (root.keys != areaFields) throw IllegalArgumentException("неожиданный набор полей файла ареала")
        val version = root.getValue(FORMAT_VERSION_FIELD).jsonPrimitive
        // A quoted "1" is a different type, not a compatible version: the field is a number.
        if (version.isString) throw IllegalArgumentException("версия файла ареала должна быть числом")
        if (version.int != FORMAT_VERSION) {
            throw IllegalArgumentException("неподдерживаемая версия файла ареала: ${version.content}")
        }
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
        // MapArea and MapGeoBounds are the same invariants the canonical value uses, so a file can
        // never describe an Ареал the app could not store.
        AreaExchangeReadResult.Present(MapArea(id = id, name = name, bounds = bounds))
    } catch (error: Exception) {
        AreaExchangeReadResult.Invalid(error.message ?: error::class.java.simpleName)
    }
}

/** Outcome of reading an Area file. */
internal sealed interface AreaExchangeReadResult {
    data class Present(val area: MapArea) : AreaExchangeReadResult

    /** The file is not a readable Area file. It is never treated as an Ареал. */
    data class Invalid(val reason: String) : AreaExchangeReadResult
}

/**
 * The human-readable managed filename of an Ареал: `<name>--<short-id>.json`, e.g.
 * `Лух--7e82a310.json`.
 *
 * The name is what the user recognises in a file manager and in the share sheet; the short id keeps
 * two Ареалы with the same name apart. The filename is **not** an identity: the full `areaId` inside
 * the file is. Eight hex characters are enough for the mirror because there is at most one Ареал per
 * Territory in a variant-specific folder, and a file whose `areaId` does not match is never treated
 * as this Ареал's copy anyway.
 */
internal object AreaExchangeFileName {
    const val EXTENSION = ".json"
    const val SEPARATOR = "--"

    /**
     * Letters kept per name, counted in Unicode code points. A Cyrillic name costs two UTF-8 bytes
     * per code point, so this stays far below any filesystem component limit.
     */
    const val MAX_NAME_CODE_POINTS = 60

    /** Filesystem-unsafe characters, replaced by a hyphen. */
    private val UNSAFE_CHARACTERS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    fun of(area: MapArea): String = of(id = area.id, name = area.name)

    fun of(id: UUID, name: String): String = externalAreaStem(id = id, name = name) + EXTENSION

    /**
     * The shared external stem of an Ареал: `<sanitised-name>--<short-id>`, without an extension.
     *
     * This one stem connects the exported Ареал to everything built for it outside Bee Search: the
     * Area JSON is `<stem>.json`, and a generated offline map package for that Ареал is
     * `<stem>--map-v<N>.pmtiles`. Both names therefore start with the same human-readable name and
     * carry the same short id, so a person sees which area a file belongs to while two Ареалы with
     * the same name stay distinguishable.
     */
    fun externalAreaStem(area: MapArea): String = externalAreaStem(id = area.id, name = area.name)

    fun externalAreaStem(id: UUID, name: String): String =
        sanitizedAreaName(name) + SEPARATOR + shortId(id)

    /** First eight hex characters of the UUID: stable for a given Ареал, never random. */
    fun shortId(id: UUID): String = id.toString().replace("-", "").take(8)

    /**
     * A deterministic, filesystem-safe, human-readable form of an Ареал name.
     *
     * Cyrillic and any other letter are preserved; only characters a file name cannot carry are
     * replaced. The result is never empty and never starts or ends with a dot, a space or the
     * separator, so appending the short id always produces an unambiguous name.
     */
    fun sanitizedAreaName(name: String): String {
        val replaced = buildString {
            name.trim().forEach { character ->
                when {
                    character in UNSAFE_CHARACTERS -> append('-')
                    // Control characters would make the name invisible or unopenable.
                    character.isISOControl() -> Unit
                    else -> append(character)
                }
            }
        }
        val collapsed = replaced
            .replace(Regex("-{2,}"), "-")
            .replace(Regex(" {2,}"), " ")
            .trim(' ', '.', '-')
        val truncated = collapsed.takeCodePoints(MAX_NAME_CODE_POINTS).trim(' ', '.', '-')
        return truncated.ifEmpty { DEFAULT_EXCHANGE_AREA_NAME }
    }

    /** `Ареал`, the same fallback the Ареал name itself uses when a Territory has no good name. */
    private const val DEFAULT_EXCHANGE_AREA_NAME = "Ареал"
}

/** Takes at most [max] Unicode code points, so a surrogate pair is never split. */
private fun String.takeCodePoints(max: Int): String {
    if (max <= 0) return ""
    var codePoints = 0
    var index = 0
    while (index < length && codePoints < max) {
        index += Character.charCount(codePointAt(index))
        codePoints++
    }
    return substring(0, index)
}
