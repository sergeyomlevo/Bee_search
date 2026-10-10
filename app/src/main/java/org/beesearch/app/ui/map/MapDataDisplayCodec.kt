package org.beesearch.app.ui.map

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.model.*
import org.beesearch.app.domain.model.parseResearchDate

/**
 * Persisted representation of the «Данные на карте» state of one Territory.
 *
 * The value lives in one `map_data_display_<territoryId>` entry of the existing settings DataStore,
 * so every Territory keeps its own state and the two never mix:
 *
 * ```text
 * v1|{"types":{"OBSERVATION_POINT":{"visible":true,"from":"2026-05-01","to":"2026-05-31"}}}
 * ```
 *
 * This is presentation state: a damaged or unreadable value is not research data and is therefore
 * reported as [decode] `null`, which the store reads as the approved default state (everything
 * visible, no period). A type name this build does not know is ignored rather than treated as
 * damage, so a future build that adds a type can write its entry without breaking this one.
 */
internal object MapDataDisplayCodec {
    const val VERSION = "v2"

    private const val TYPES = "types"
    private const val VISIBLE = "visible"
    private const val FROM = "from"
    private const val TO = "to"

    private val legacyTypeFields = setOf(VISIBLE, FROM, TO)
    private val countFields = setOf("beeMin", "beeMax", "cycleMin", "cycleMax")
    private val measurementFields = setOf("heightMin", "heightMax", "diameterMin", "diameterMax")
    private val rootFields = setOf(TYPES)

    private val json = Json
    private val elementSerializer = JsonElement.serializer()

    fun encode(state: MapDataDisplayState): String = "$VERSION|" + json.encodeToString(
        elementSerializer,
        buildJsonObject {
            put(
                TYPES,
                buildJsonObject {
                    MapDataType.entries.forEach { type ->
                        val value = state.display(type)
                        put(
                            type.name,
                            buildJsonObject {
                                put(VISIBLE, JsonPrimitive(value.visible))
                                put(FROM, JsonPrimitive(value.period?.fromDate?.toString()))
                                put(TO, JsonPrimitive(value.period?.toDate?.toString()))
                                when (val filters = value.filters) {
                                    is ObservationPointFilterSet -> {
                                        put("beeMin", JsonPrimitive(filters.beeCount.min))
                                        put("beeMax", JsonPrimitive(filters.beeCount.max))
                                        put("cycleMin", JsonPrimitive(filters.flightCycleCount.min))
                                        put("cycleMax", JsonPrimitive(filters.flightCycleCount.max))
                                    }
                                    is PhysicalObjectFilterSet -> {
                                        put("heightMin", JsonPrimitive(filters.entranceHeightCm.min))
                                        put("heightMax", JsonPrimitive(filters.entranceHeightCm.max))
                                        put("diameterMin", JsonPrimitive(filters.outerDiameterCm.min))
                                        put("diameterMax", JsonPrimitive(filters.outerDiameterCm.max))
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
    )

    /** Reads a persisted value; `null` means absent or unreadable, never a partial state. */
    fun decode(value: String?): MapDataDisplayState? {
        if (value == null) return null
        val version = value.substringBefore("|")
        if (version != "v1" && version != VERSION) return null
        return guard {
            val root = json.parseToJsonElement(value.substringAfter("|")).jsonObject
            if (root.keys != rootFields) throw IllegalArgumentException("неожиданный набор полей состояния")
            val types = root.getValue(TYPES).jsonObject
            val decoded = mutableMapOf<MapDataType, MapTypeDisplay>()
            types.forEach { (name, element) ->
                val type = MapDataType.entries.firstOrNull { it.name == name } ?: return@forEach
                val value = element.jsonObject
                val expected = legacyTypeFields + if (version == "v1") emptySet() else
                    if (type == MapDataType.OBSERVATION_POINT) countFields else measurementFields
                require(value.keys == expected) { "неожиданный набор полей типа" }
                val period = decodePeriod(value.getValue(FROM), value.getValue(TO))
                fun integer(key: String): Int? = value[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toInt()
                fun measurement(key: String): Double? = value[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toDouble()
                val filters = if (type == MapDataType.OBSERVATION_POINT) ObservationPointFilterSet(
                    period, CountRange(integer("beeMin"), integer("beeMax")),
                    CountRange(integer("cycleMin"), integer("cycleMax")),
                ) else PhysicalObjectFilterSet(
                    period, MeasurementRange(measurement("heightMin"), measurement("heightMax")),
                    MeasurementRange(measurement("diameterMin"), measurement("diameterMax")),
                )
                decoded[type] = MapTypeDisplay(
                    visible = value.getValue(VISIBLE).jsonPrimitive.content.toBooleanStrict(),
                    filters = filters,
                )
            }
            MapDataDisplayState(
                types = MapDataType.entries.associateWith { type ->
                    decoded[type] ?: MapTypeDisplay(filters = defaultFilters(type))
                },
            )
        }
    }

    private fun decodePeriod(from: JsonElement, to: JsonElement): ResearchDateInterval? {
        val fromDate = decodeDate(from)
        val toDate = decodeDate(to)
        // A half-written period is damage, not «Всё время».
        if ((fromDate == null) != (toDate == null)) {
            throw IllegalArgumentException("период записан неполностью")
        }
        if (fromDate == null || toDate == null) return null
        return ResearchDateInterval(fromDate, toDate)
    }

    private fun decodeDate(element: JsonElement): LocalDate? {
        if (element is JsonNull) return null
        val text = element.jsonPrimitive.content
        if (text.isEmpty()) return null
        // A non-empty value that is not a canonical research date is damage, not «Всё время».
        return parseResearchDateForDisplay(text)
            ?: throw IllegalArgumentException("некорректная дата периода")
    }

    private fun parseResearchDateForDisplay(value: String): LocalDate? = try {
        parseResearchDate(value)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun guard(block: () -> MapDataDisplayState): MapDataDisplayState? = try {
        block()
    } catch (error: Exception) {
        null
    }
}
