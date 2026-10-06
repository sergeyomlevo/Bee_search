package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.snapshotPortableJson
import org.beesearch.app.data.backup.snapshotRows
import org.beesearch.app.data.backuprepository.CanonicalExtension
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonNull
import org.beesearch.app.data.backup.validateGraph
import org.beesearch.app.data.backup.validateSettings
import org.beesearch.app.data.backup.snapshotGraphFromRows
import org.beesearch.app.data.backup.snapshotSettingsFromRows
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException

internal data class SnapshotMediaReference(
    val sha256: String,
    val byteSize: Long,
    val canonicalExtension: String,
)

internal data class SnapshotDomainEntries(
    val records: Map<String, List<String>>,
    val portable: ByteArray,
    val references: List<SnapshotMediaReference>,
)

/** Pure, metadata-only domain bridge. It never opens or reads a media file. */
internal object SnapshotDomainCodec {
    private val expected = setOf(
        "data/territories.jsonl", "data/observers.jsonl", "data/physical-objects.jsonl",
        "data/apiaries.jsonl", "data/hollows.jsonl", "data/log-hives.jsonl",
        "data/physical-object-sequences.jsonl", "data/physical-object-media.jsonl",
        "data/observation-points.jsonl", "data/bees.jsonl", "data/flight-cycles.jsonl",
        "data/observation-point-weather.jsonl", "data/observation-point-attachments.jsonl",
        "settings/map-coverage.jsonl",
    )
    private val allExpected = expected + "settings/portable.json" + "references/media-blobs.jsonl"

    fun encode(graph: Graph, settings: PortableSettingsSnapshot): SnapshotDomainEntries {
        validateGraphForSnapshot(graph, settings)
        val records = snapshotRows(graph).toMutableMap()
        records["settings/map-coverage.jsonl"] = settings.coverage.entries.sortedBy { it.key.toString() }
            .map { "{\"territoryId\":${quote(it.key.toString())},\"encoded\":${quote(it.value)}}" }
        val references = references(graph)
        return SnapshotDomainEntries(records, snapshotPortableJson(settings).toByteArray(StandardCharsets.UTF_8), references)
    }

    /** Only fixed, integrity-checked files supplied by the archive reader. */
    fun validate(entries: Map<String, File>, check: () -> Unit = {}, limits: SnapshotLimits = SnapshotLimits()) {
        if (entries.keys != allExpected) logical("entry set mismatch")
        try {
            val parsed = expected.filter { it != "settings/map-coverage.jsonl" }.associateWith { path ->
                readRows(entries.getValue(path), limits, check).also { rows -> rows.forEach { validateNumericTypes(it) } }
            }
            val coverage = readRows(entries.getValue("settings/map-coverage.jsonl"), limits, check)
            val portable = SnapshotJson.parse(entries.getValue("settings/portable.json").readBytes(), false, limits).jsonObject
            if (portable.keys != setOf("currentTerritoryId", "currentObserverId")) logical("portable fields")
            val graph = snapshotGraphFromRows(parsed)
            val settings = snapshotSettingsFromRows(portable, coverage)
            validateGraphForSnapshot(graph, settings)
            val actual = readReferences(entries.getValue("references/media-blobs.jsonl"), limits, check)
            if (actual != references(graph)) logical("media reference set mismatch")
            check()
        } catch (e: CancellationException) { throw e }
        catch (e: SnapshotException) { throw e }
        catch (e: Exception) { logical(e.message ?: "logical state inconsistent", e) }
    }

    private fun validateGraphForSnapshot(graph: Graph, settings: PortableSettingsSnapshot) {
        try {
            validateGraph(graph)
            validateSettings(settings, graph)
            val territories = graph.territories.mapTo(hashSetOf()) { it.id }
            val observers = graph.observers.mapTo(hashSetOf()) { it.id }
            if (settings.currentTerritoryId != null && settings.currentTerritoryId !in territories) logical("current territory missing")
            if (settings.currentObserverId != null && settings.currentObserverId !in observers) logical("current observer missing")
            val points = graph.points.mapTo(hashSetOf()) { it.id }
            val objects = graph.physicalObjects.mapTo(hashSetOf()) { it.id }
            if (graph.hollows.map { it.physicalObjectId }.distinct().size != graph.hollows.size ||
                graph.logHives.map { it.physicalObjectId }.distinct().size != graph.logHives.size) logical("duplicate subtype identity")
            if (graph.weather.map { it.observationPointId }.distinct().size != graph.weather.size ||
                graph.weather.any { it.observationPointId !in points }) logical("weather identity/owner")
            if (graph.objectMedia.map { it.id }.distinct().size != graph.objectMedia.size ||
                graph.objectMedia.any { it.physicalObjectId !in objects }) logical("object media identity/owner")
            if (graph.attachments.map { it.id }.distinct().size != graph.attachments.size ||
                graph.attachments.any { it.observationPointId !in points }) logical("attachment identity/owner")
            (graph.objectMedia.map { it.sha256 to it.byteSize } + graph.attachments.map { it.sha256 to it.byteSize }).forEach {
                if (!it.first.matches(Regex("[0-9a-f]{64}")) || it.second <= 0) logical("media identity")
            }
            graph.weather.forEach {
                if (listOfNotNull(it.temperatureC, it.windSpeedMps, it.windDirectionDeg).any { value -> !value.isFinite() }) logical("weather number")
            }
        } catch (e: Exception) { logical(e.message ?: "logical state inconsistent", e) }
    }

    private fun references(graph: Graph): List<SnapshotMediaReference> {
        val pairs = graph.objectMedia.map { Triple(it.sha256, it.byteSize, it.mimeType) } +
            graph.attachments.map { Triple(it.sha256, it.byteSize, it.mimeType) }
        val grouped = pairs.groupBy { it.first }
        if (grouped.values.any { it.map { pair -> pair.second }.distinct().size > 1 }) logical("conflicting media size")
        return grouped.map { (sha, values) ->
            val ext = try { CanonicalExtension.resolve(values.map { it.third }) }
                catch (e: Exception) { logical("conflicting media type", e) }
            SnapshotMediaReference(sha, values.first().second, ext)
        }.sortedBy { it.sha256 }
    }

    private fun extension(mime: String?): String = try { CanonicalExtension.resolve(listOf(mime)) }
    catch (e: Exception) { logical("conflicting media extension hints", e) }

    private fun readRows(file: File, limits: SnapshotLimits, check: () -> Unit, canonical: Boolean = false): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        BufferedInputStream(file.inputStream()).use { input ->
            val line = ByteArrayOutputStream()
            while (true) {
                check(); val n = input.read(); if (n < 0) break
                if (line.size() + 1L > limits.recordBytes) limit(file.name + ":record", line.size() + 1L, limits.recordBytes)
                if (n == 10) {
                    if (line.size() == 0) logical("empty JSONL record")
                    val bytes = line.toByteArray()
                    val value = SnapshotJson.parse(bytes, canonical, limits).jsonObject
                    if (canonical && !SnapshotJson.encode(value).contentEquals(bytes)) logical("noncanonical reference")
                    result += value
                    line.reset()
                } else line.write(n)
            }
            if (line.size() != 0) logical("missing final LF")
        }
        return result
    }
    private fun readReferences(file: File, limits: SnapshotLimits, check: () -> Unit): List<SnapshotMediaReference> =
        readRows(file, limits, check, canonical = true).map {
            if (it.keys != setOf("sha256", "byteSize", "canonicalExtension")) logical("reference fields")
            SnapshotMediaReference(it.string("sha256"), it.number("byteSize"), it.string("canonicalExtension"))
        }.also { if (it.map { ref -> ref.sha256 }.distinct().size != it.size) logical("duplicate reference") }

    private fun validateNumericTypes(row: JsonObject) {
        val numbers = setOf("latitude", "longitude", "gpsLatitude", "gpsLongitude", "gpsAccuracyM",
            "entranceHeightCm", "entranceAzimuthDeg", "outerDiameterCm", "internalDiameterCm",
            "internalHeightCm", "temperatureC", "windSpeedMps", "windDirectionDeg", "azimuthDeg",
            "sequenceNumber", "lastIssued", "byteSize", "observationYear", "pointNumber", "createdAt",
            "updatedAt", "departureTime", "returnTime", "initialGroupReleaseAt", "completedAt", "sampleAt", "fetchedAt")
        row.filterKeys { it in numbers }.values.forEach {
            if (it != JsonNull) {
                val number = it as? JsonPrimitive ?: logical("numeric wire type")
                if (number.isString || number.content.toDoubleOrNull()?.isFinite() != true) logical("numeric wire type")
            }
        }
    }

    private fun quote(value: String) = SnapshotJson.encode(JsonPrimitive(value)).toString(StandardCharsets.UTF_8)
    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: logical("missing string $name")
    private fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull() ?: logical("invalid number $name")
    private fun logical(message: String, cause: Throwable? = null): Nothing = throw SnapshotException(SnapshotError.LOGICAL_STATE_INCONSISTENT, message, cause = cause)
    private fun limit(category: String, observed: Long, configured: Long): Nothing = throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, category, observed, configured)
}
