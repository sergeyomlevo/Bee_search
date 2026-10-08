package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.snapshotPortableJson
import org.beesearch.app.data.backup.snapshotRows
import org.beesearch.app.data.backuprepository.CanonicalExtension
import org.beesearch.app.data.backuprepository.MediaIdentityCandidate
import org.beesearch.app.data.backuprepository.RequiredMediaSet
import org.beesearch.app.data.backuprepository.RequiredMediaSetException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonNull
import org.beesearch.app.data.backup.validateGraph
import org.beesearch.app.data.backup.validateLegacyObservationDates
import org.beesearch.app.domain.backup.BackupDomainInvariantViolation
import org.beesearch.app.data.backup.snapshotGraphFromRows
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import org.beesearch.app.domain.model.WeatherStatus
import java.util.UUID

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
        try { validateLegacyObservationDates(graph) }
        catch (e: BackupDomainInvariantViolation) { logical(e.message ?: "legacy date not representable", e) }
        val records = snapshotRows(graph).toMutableMap()
        records["settings/map-coverage.jsonl"] = settings.coverage.entries.sortedBy { it.key.toString() }
            .map { "{\"territoryId\":${quote(it.key.toString())},\"encoded\":${quote(it.value)}}" }
        val portable = snapshotPortableJson(settings).toByteArray(StandardCharsets.UTF_8)
        val parsed = records.mapValues { (_, rows) -> rows.map {
            SnapshotJson.parse(it.toByteArray(StandardCharsets.UTF_8), false).jsonObject
        } }
        val references = validateWireState(parsed, SnapshotJson.parse(portable, false).jsonObject, SnapshotLimits())
        return SnapshotDomainEntries(records, portable, references)
    }

    /** Only fixed, integrity-checked files supplied by the archive reader. Returns the wire references. */
    fun validate(
        entries: Map<String, File>,
        check: () -> Unit = {},
        limits: SnapshotLimits = SnapshotLimits(),
    ): List<SnapshotMediaReference> {
        if (entries.keys != allExpected) logical("entry set mismatch")
        try {
            val parsed = expected.associateWith { path ->
                readRows(entries.getValue(path), limits, check)
            }
            val portable = SnapshotJson.parse(entries.getValue("settings/portable.json").readBytes(), false, limits).jsonObject
            val expectedReferences = validateWireState(parsed, portable, limits)
            val actual = readReferences(entries.getValue("references/media-blobs.jsonl"), limits, check)
            if (actual != expectedReferences) logical("media reference set mismatch")
            check()
            return actual
        } catch (e: CancellationException) { throw e }
        catch (e: SnapshotException) { throw e }
        catch (e: Exception) { logical(e.message ?: "logical state inconsistent", e) }
    }

    private fun validateGraphForSnapshot(graph: Graph, settings: PortableSettingsSnapshot, limits: SnapshotLimits = SnapshotLimits()) {
        try {
            validateGraph(graph, globalIdentityUniqueness = false)
            val territories = graph.territories.mapTo(hashSetOf()) { it.id }
            val observers = graph.observers.mapTo(hashSetOf()) { it.id }
            if (settings.currentTerritoryId != null && settings.currentTerritoryId !in territories) logical("current territory missing")
            if (settings.currentObserverId != null && settings.currentObserverId !in observers) logical("current observer missing")
            if (!territories.containsAll(settings.coverage.keys)) logical("coverage territory missing")
            settings.coverage.values.forEach { SnapshotCoverageValidator.validate(it, limits) }
            val points = graph.points.mapTo(hashSetOf()) { it.id }
            val objects = graph.physicalObjects.mapTo(hashSetOf()) { it.id }
            if (graph.hollows.map { it.physicalObjectId }.distinct().size != graph.hollows.size ||
                graph.logHives.map { it.physicalObjectId }.distinct().size != graph.logHives.size) logical("duplicate subtype identity")
            if (graph.weather.map { it.observationPointId }.distinct().size != graph.weather.size ||
                graph.weather.any { it.observationPointId !in points }) logical("weather identity/owner")
            if (graph.weather.mapTo(hashSetOf()) { it.observationPointId } != points) logical("weather cardinality")
            if (graph.objectMedia.map { it.id }.distinct().size != graph.objectMedia.size ||
                graph.objectMedia.any { it.physicalObjectId !in objects }) logical("object media identity/owner")
            if (graph.attachments.map { it.id }.distinct().size != graph.attachments.size ||
                graph.attachments.any { it.observationPointId !in points }) logical("attachment identity/owner")
            if (graph.bees.groupingBy { it.observationPointId }.eachCount().values.any { it > 10 }) logical("bee cardinality exceeds 10")
            graph.weather.forEach {
                when (it.status) {
                    WeatherStatus.PENDING, WeatherStatus.UNAVAILABLE -> {
                        if (it.temperatureC != null || it.windSpeedMps != null || it.windDirectionDeg != null ||
                            it.sampleAt != null || it.fetchedAt != null || it.source != null) logical("weather unloaded payload")
                    }
                    WeatherStatus.LOADED -> {
                        if (it.temperatureC?.isFinite() != true ||
                            it.windSpeedMps?.let { speed -> speed.isFinite() && speed >= 0.0 } != true ||
                            it.windDirectionDeg?.let { direction -> direction.isFinite() && direction >= 0.0 && direction < 360.0 } != true)
                            logical("weather loaded numbers")
                        if (it.sampleAt == null || it.fetchedAt == null) logical("weather loaded timestamps")
                        if (it.source.isNullOrBlank()) logical("weather loaded source")
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: SnapshotException) { throw e }
        catch (e: Exception) { logical(e.message ?: "logical state inconsistent", e) }
    }

    private val mediaPaths = setOf("data/physical-object-media.jsonl", "data/observation-point-attachments.jsonl")

    /** Shared raw wire boundary: unknown media identity never needs fabricated Room fields. */
    private fun validateWireState(rows: Map<String, List<JsonObject>>, portable: JsonObject, limits: SnapshotLimits): List<SnapshotMediaReference> {
        rows.forEach { (path, records) -> SnapshotRecordSchema.validateRows(path, records) }
        SnapshotRecordSchema.validatePortable(portable)
        // The legacy entity bridge cannot represent absent/null media SHA/size.
        // Validate those raw records below instead; their original bytes stay untouched.
        val graph = snapshotGraphFromRows(rows.filterKeys { it !in mediaPaths && it != "settings/map-coverage.jsonl" })
        val coverage = rows.getValue("settings/map-coverage.jsonl")
        val settings = PortableSettingsSnapshot(
            portable.nullableString("currentTerritoryId")?.let(UUID::fromString),
            portable.nullableString("currentObserverId")?.let(UUID::fromString),
            coverage.associate { UUID.fromString(it.string("territoryId")) to it.string("encoded") },
        )
        validateGraphForSnapshot(graph, settings, limits)
        val objects = graph.physicalObjects.mapTo(hashSetOf()) { it.id.toString() }
        val points = graph.points.mapTo(hashSetOf()) { it.id.toString() }
        rows.getValue("data/physical-object-media.jsonl").forEach {
            if (it.string("physicalObjectId") !in objects) logical("object media owner missing")
        }
        rows.getValue("data/observation-point-attachments.jsonl").forEach {
            if (it.string("observationPointId") !in points) logical("attachment owner missing")
        }
        return references(mediaPaths.flatMap { rows.getValue(it) })
    }

    /**
     * The required media set, resolved through the one shared definition.
     *
     * [org.beesearch.app.data.backuprepository.RequiredMediaSet] owns eligibility, deduplication,
     * MIME aggregation and the canonical extension; media protection resolves its work through the
     * same object, so snapshot references and protected blobs cannot disagree. Internal rather than
     * private so the parity test can compare both paths on identical input.
     */
    internal fun references(rows: List<JsonObject>): List<SnapshotMediaReference> {
        val candidates = rows.map {
            MediaIdentityCandidate(
                sha256 = it.nullableString("sha256"),
                byteSize = (it["byteSize"] as? JsonPrimitive)?.takeIf { value -> value != JsonNull }?.content?.toLongOrNull(),
                mimeType = it.nullableString("mimeType"),
            )
        }
        return try {
            RequiredMediaSet.resolve(candidates).map {
                SnapshotMediaReference(it.sha256, it.byteSize, it.canonicalExtension)
            }
        } catch (e: RequiredMediaSetException) {
            logical(e.message ?: RequiredMediaSet.CONFLICTING_SIZE, e)
        }
    }

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
        readRows(file, limits, check, canonical = true).also {
            SnapshotRecordSchema.validateRows("references/media-blobs.jsonl", it)
        }.map {
            SnapshotMediaReference(it.string("sha256"), it.number("byteSize"), it.string("canonicalExtension"))
        }.also { if (it.map { ref -> ref.sha256 }.distinct().size != it.size) logical("duplicate reference") }

    private fun quote(value: String) = SnapshotJson.encode(JsonPrimitive(value)).toString(StandardCharsets.UTF_8)
    private fun JsonObject.nullableString(name: String) = if (this[name] == null || this[name] == JsonNull) null else string(name)
    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: logical("missing string $name")
    private fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull() ?: logical("invalid number $name")
    private fun logical(message: String, cause: Throwable? = null): Nothing = throw SnapshotException(SnapshotError.LOGICAL_STATE_INCONSISTENT, message, cause = cause)
    private fun limit(category: String, observed: Long, configured: Long): Nothing = throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, category, observed, configured)
}
