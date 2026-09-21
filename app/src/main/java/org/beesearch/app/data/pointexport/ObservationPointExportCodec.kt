package org.beesearch.app.data.pointexport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory

/** Pure v1 ZIP encoder/decoder. It does not access Room, files, DataStore, or the network. */
internal object ObservationPointExportCodec {
    private val json = Json { isLenient = false; ignoreUnknownKeys = false }
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun encode(
        graph: ObservationPointExportGraph,
        attachmentBytes: Map<UUID, ByteArray>,
        output: OutputStream,
    ) {
        val canonical = canonicalize(graph)
        ObservationPointExportValidator.validate(canonical, attachmentBytes)
        val pointBytes = pointJson(canonical).toString().toByteArray(StandardCharsets.UTF_8)
        requireEntrySize(pointBytes.size.toLong())
        val attachmentDescriptors = canonical.attachments.map { attachment ->
            val bytes = attachmentBytes.getValue(attachment.id)
            buildJsonObject {
                put("id", attachment.id.toString())
                put("entry", ObservationPointExportContract.attachmentEntry(attachment.id))
                put("byteLength", bytes.size)
                put("sha256", sha256(bytes))
                attachment.mimeType?.let { put("mimeType", it) }
            }
        }
        val manifestBytes = buildJsonObject {
            put("profile", ObservationPointExportContract.PROFILE)
            put("formatVersion", ObservationPointExportContract.FORMAT_VERSION)
            put("observationPointId", canonical.point.id.toString())
            put("pointEntry", ObservationPointExportContract.POINT_ENTRY)
            put("pointByteLength", pointBytes.size)
            put("pointSha256", sha256(pointBytes))
            put("attachments", JsonArray(attachmentDescriptors))
        }.toString().toByteArray(StandardCharsets.UTF_8)
        requireEntrySize(manifestBytes.size.toLong())
        val total = manifestBytes.size.toLong() + pointBytes.size + attachmentBytes.values.sumOf { it.size.toLong() }
        if (total > ObservationPointExportContract.MAX_TOTAL_BYTES) {
            throw InvalidObservationPointExport("package is too large")
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            putEntry(zip, ObservationPointExportContract.MANIFEST_ENTRY, manifestBytes)
            putEntry(zip, ObservationPointExportContract.POINT_ENTRY, pointBytes)
            canonical.attachments.forEach { attachment ->
                putEntry(zip, ObservationPointExportContract.attachmentEntry(attachment.id), attachmentBytes.getValue(attachment.id))
            }
        }
    }

    fun decode(input: InputStream): DecodedObservationPointExport {
        val entries = readArchive(input)
        val manifest = parseObject(entries[ObservationPointExportContract.MANIFEST_ENTRY], "manifest")
        if (manifest.string("profile") != ObservationPointExportContract.PROFILE) {
            throw InvalidObservationPointExport("unsupported profile")
        }
        if (manifest.int("formatVersion") != ObservationPointExportContract.FORMAT_VERSION) {
            throw InvalidObservationPointExport("unsupported formatVersion")
        }
        val pointId = manifest.uuid("observationPointId")
        val pointEntry = manifest.string("pointEntry")
        if (pointEntry != ObservationPointExportContract.POINT_ENTRY) {
            throw InvalidObservationPointExport("unexpected point entry")
        }
        val pointBytes = entries[pointEntry] ?: throw InvalidObservationPointExport("point.json is missing")
        verifyBytes(pointBytes, manifest.long("pointByteLength"), manifest.string("pointSha256"), "point.json")

        val descriptors = manifest.array("attachments")
        val describedIds = hashSetOf<UUID>()
        val describedEntries = hashSetOf<String>()
        val bytesById = linkedMapOf<UUID, ByteArray>()
        descriptors.forEach { element ->
            val descriptor = element.asObject("attachment descriptor")
            val id = descriptor.uuid("id")
            if (!describedIds.add(id)) throw InvalidObservationPointExport("duplicate attachment id")
            val entry = descriptor.string("entry")
            validateEntryName(entry)
            if (entry != ObservationPointExportContract.attachmentEntry(id) || !describedEntries.add(entry)) {
                throw InvalidObservationPointExport("invalid attachment entry")
            }
            val bytes = entries[entry] ?: throw InvalidObservationPointExport("attachment blob is missing")
            verifyBytes(bytes, descriptor.long("byteLength"), descriptor.string("sha256"), entry)
            bytesById[id] = bytes
        }
        val expectedEntries = buildSet {
            add(ObservationPointExportContract.MANIFEST_ENTRY)
            add(ObservationPointExportContract.POINT_ENTRY)
            addAll(describedEntries)
        }
        if (entries.keys != expectedEntries) throw InvalidObservationPointExport("unexpected ZIP entry")

        val graph = parseGraph(parseObject(pointBytes, "point.json"))
        if (graph.point.id != pointId) throw InvalidObservationPointExport("manifest point id mismatch")
        ObservationPointExportValidator.validate(graph, bytesById)
        if (graph.attachments.map { it.id }.toSet() != describedIds) {
            throw InvalidObservationPointExport("attachment manifest mismatch")
        }
        return DecodedObservationPointExport(graph, bytesById)
    }

    private fun canonicalize(graph: ObservationPointExportGraph) = graph.copy(
        beeHistories = graph.beeHistories
            .sortedWith(compareBy<BeeObservationHistory>({ it.bee.createdAt }, { it.bee.id.toString() }))
            .map { history ->
                history.copy(
                    flightCycles = history.flightCycles.sortedWith(
                        compareBy<FlightCycle>({ it.sequenceNumber }, { it.id.toString() }),
                    ),
                )
            },
        attachments = graph.attachments.sortedWith(
            compareBy<ObservationPointAttachment>({ it.createdAt }, { it.id.toString() }),
        ),
    )

    private fun pointJson(graph: ObservationPointExportGraph): JsonObject = buildJsonObject {
        put("point", pointJson(graph.point))
        put("territory", territoryJson(graph.territory))
        put("observer", observerJson(graph.observer))
        put("weather", graph.weather?.let(::weatherJson) ?: JsonNull)
        put("bees", buildJsonArray {
            graph.beeHistories.forEach { history ->
                add(buildJsonObject {
                    put("bee", beeJson(history.bee))
                    put("flightCycles", buildJsonArray { history.flightCycles.forEach { add(cycleJson(it)) } })
                })
            }
        })
        put("attachments", buildJsonArray { graph.attachments.forEach { add(attachmentJson(it)) } })
    }

    private fun pointJson(value: ObservationPoint) = buildJsonObject {
        put("id", value.id.toString()); put("territoryId", value.territoryId.toString()); put("observerId", value.observerId.toString())
        put("observationYear", value.observationYear); put("pointNumber", value.pointNumber); putNullable("beePresenceResult", value.beePresenceResult?.name)
        putNullable("code", value.code); put("latitude", value.latitude); put("longitude", value.longitude)
        putNullable("gpsLatitude", value.gpsLatitude); putNullable("gpsLongitude", value.gpsLongitude); putNullable("gpsAccuracyM", value.gpsAccuracyM)
        put("createdAt", value.createdAt.toString()); putNullable("initialGroupReleaseAt", value.initialGroupReleaseAt?.toString())
        putNullable("completedAt", value.completedAt?.toString()); putNullable("description", value.description)
    }

    private fun territoryJson(value: Territory) = buildJsonObject {
        put("id", value.id.toString()); put("code", value.code); put("name", value.name); put("region", value.region); put("district", value.district)
        put("createdAt", value.createdAt.toString()); put("updatedAt", value.updatedAt.toString())
    }

    private fun observerJson(value: Observer) = buildJsonObject {
        put("id", value.id.toString()); put("code", value.code); put("lastName", value.lastName); put("firstName", value.firstName)
        putNullable("middleName", value.middleName); putNullable("contact", value.contact)
        put("createdAt", value.createdAt.toString()); put("updatedAt", value.updatedAt.toString())
    }

    private fun weatherJson(value: ObservationPointWeather) = buildJsonObject {
        put("observationPointId", value.observationPointId.toString()); put("status", value.status.name)
        putNullable("temperatureC", value.temperatureC); putNullable("windSpeedMps", value.windSpeedMps); putNullable("windDirectionDeg", value.windDirectionDeg)
        putNullable("sampleAt", value.sampleAt?.toString()); putNullable("fetchedAt", value.fetchedAt?.toString()); putNullable("source", value.source)
    }

    private fun beeJson(value: Bee) = buildJsonObject {
        put("id", value.id.toString()); put("observationPointId", value.observationPointId.toString()); put("markColor", value.markColor)
        put("markPosition", value.markPosition.name); put("createdAt", value.createdAt.toString())
    }

    private fun cycleJson(value: FlightCycle) = buildJsonObject {
        put("id", value.id.toString()); put("beeId", value.beeId.toString()); put("sequenceNumber", value.sequenceNumber)
        put("departureTime", value.departureTime.toString()); putNullable("returnTime", value.returnTime?.toString()); putNullable("azimuthDeg", value.azimuthDeg)
        put("azimuthCaptureConsumed", value.azimuthCaptureConsumed); put("isInitialGroupLaunch", value.isInitialGroupLaunch)
        put("isFirstDepartureCancellationEligible", value.isFirstDepartureCancellationEligible)
        put("createdAt", value.createdAt.toString()); put("updatedAt", value.updatedAt.toString())
    }

    private fun attachmentJson(value: ObservationPointAttachment) = buildJsonObject {
        put("id", value.id.toString()); put("observationPointId", value.observationPointId.toString()); put("type", value.type.name)
        put("relativePath", value.relativePath); putNullable("originalFileName", value.originalFileName); putNullable("mimeType", value.mimeType)
        put("byteSize", value.byteSize); put("sha256", value.sha256); put("createdAt", value.createdAt.toString())
        put("packageEntry", ObservationPointExportContract.attachmentEntry(value.id))
    }

    private fun parseGraph(root: JsonObject): ObservationPointExportGraph {
        val point = root.obj("point").toPoint()
        return ObservationPointExportGraph(
            point = point,
            territory = root.obj("territory").toTerritory(),
            observer = root.obj("observer").toObserver(),
            weather = root.nullableObject("weather")?.toWeather(),
            beeHistories = root.array("bees").map { row ->
                val item = row.asObject("bee history")
                BeeObservationHistory(item.obj("bee").toBee(), item.array("flightCycles").map { it.asObject("flight cycle").toCycle() })
            },
            attachments = root.array("attachments").map { it.asObject("attachment").toAttachment() },
        )
    }

    private fun JsonObject.toPoint() = ObservationPoint(
        uuid("id"), uuid("territoryId"), uuid("observerId"), int("observationYear"), int("pointNumber"),
        nullableString("beePresenceResult")?.let { enum<BeePresenceResult>(it, "beePresenceResult") }, nullableString("code"),
        double("latitude"), double("longitude"), nullableDouble("gpsLatitude"), nullableDouble("gpsLongitude"), nullableDouble("gpsAccuracyM"),
        instant("createdAt"), nullableInstant("initialGroupReleaseAt"), nullableInstant("completedAt"), nullableString("description"),
    )

    private fun JsonObject.toTerritory() = Territory(
        uuid("id"), string("code"), string("name"), string("region"), string("district"), instant("createdAt"), instant("updatedAt"),
    )

    private fun JsonObject.toObserver() = Observer(
        uuid("id"), string("code"), string("lastName"), string("firstName"), nullableString("middleName"), nullableString("contact"),
        instant("createdAt"), instant("updatedAt"),
    )

    private fun JsonObject.toWeather() = ObservationPointWeather(
        uuid("observationPointId"), enum(string("status"), "weather status"), nullableDouble("temperatureC"), nullableDouble("windSpeedMps"),
        nullableDouble("windDirectionDeg"), nullableInstant("sampleAt"), nullableInstant("fetchedAt"), nullableString("source"),
    )

    private fun JsonObject.toBee() = Bee(
        uuid("id"), uuid("observationPointId"), string("markColor"), enum(string("markPosition"), "markPosition"), instant("createdAt"),
    )

    private fun JsonObject.toCycle() = FlightCycle(
        uuid("id"), uuid("beeId"), int("sequenceNumber"), instant("departureTime"), nullableInstant("returnTime"), nullableDouble("azimuthDeg"),
        bool("azimuthCaptureConsumed"), bool("isInitialGroupLaunch"), bool("isFirstDepartureCancellationEligible"), instant("createdAt"), instant("updatedAt"),
    )

    private fun JsonObject.toAttachment(): ObservationPointAttachment {
        val id = uuid("id")
        if (string("packageEntry") != ObservationPointExportContract.attachmentEntry(id)) {
            throw InvalidObservationPointExport("attachment package entry mismatch")
        }
        return ObservationPointAttachment(
            id, uuid("observationPointId"), enum(string("type"), "attachment type"), string("relativePath"), nullableString("originalFileName"),
            nullableString("mimeType"), long("byteSize"), string("sha256"), instant("createdAt"),
        )
    }

    private fun readArchive(input: InputStream): Map<String, ByteArray> = try {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (result.size >= ObservationPointExportContract.MAX_ENTRIES) throw InvalidObservationPointExport("too many ZIP entries")
                if (entry.isDirectory) throw InvalidObservationPointExport("directory ZIP entry is not allowed")
                validateEntryName(entry.name)
                if (result.containsKey(entry.name)) throw InvalidObservationPointExport("duplicate ZIP entry")
                val bytes = readLimited(zip, ObservationPointExportContract.MAX_ENTRY_BYTES)
                total += bytes.size
                if (total > ObservationPointExportContract.MAX_TOTAL_BYTES) throw InvalidObservationPointExport("archive is too large")
                result[entry.name] = bytes
                zip.closeEntry()
            }
        }
        if (result.isEmpty()) throw InvalidObservationPointExport("empty archive")
        result
    } catch (error: ObservationPointExportException) {
        throw error
    } catch (error: Exception) {
        throw InvalidObservationPointExport("malformed archive", error)
    }

    private fun validateEntryName(name: String) {
        val parts = name.split('/')
        if (name.isBlank() || name.startsWith('/') || name.contains('\\') || name.contains(':') ||
            parts.any { it.isBlank() || it == "." || it == ".." }
        ) throw InvalidObservationPointExport("unsafe ZIP entry")
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw InvalidObservationPointExport("ZIP entry is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun requireEntrySize(size: Long) {
        if (size > ObservationPointExportContract.MAX_ENTRY_BYTES) throw InvalidObservationPointExport("entry is too large")
    }

    private fun verifyBytes(bytes: ByteArray, expectedSize: Long, expectedSha: String, label: String) {
        requireEntrySize(bytes.size.toLong())
        if (expectedSize != bytes.size.toLong()) throw ObservationPointExportIntegrityError("$label size mismatch")
        if (!expectedSha.matches(hashPattern) || expectedSha != sha256(bytes)) throw ObservationPointExportIntegrityError("$label SHA-256 mismatch")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseObject(bytes: ByteArray?, label: String): JsonObject {
        if (bytes == null) throw InvalidObservationPointExport("$label is missing")
        return try {
            json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            throw InvalidObservationPointExport("malformed $label", error)
        }
    }

    private fun JsonElement.asObject(label: String) = try { jsonObject } catch (error: Exception) {
        throw InvalidObservationPointExport("$label must be an object", error)
    }
    private fun JsonObject.element(name: String) = this[name] ?: throw InvalidObservationPointExport("missing $name")
    private fun JsonObject.string(name: String) = try { element(name).jsonPrimitive.content } catch (error: Exception) { throw InvalidObservationPointExport("invalid $name", error) }
    private fun JsonObject.nullableString(name: String): String? = element(name).let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull ?: throw InvalidObservationPointExport("invalid $name") }
    private fun JsonObject.int(name: String) = element(name).jsonPrimitive.intOrNull ?: throw InvalidObservationPointExport("invalid $name")
    private fun JsonObject.long(name: String) = element(name).jsonPrimitive.longOrNull ?: throw InvalidObservationPointExport("invalid $name")
    private fun JsonObject.double(name: String) = element(name).jsonPrimitive.doubleOrNull ?: throw InvalidObservationPointExport("invalid $name")
    private fun JsonObject.nullableDouble(name: String): Double? = element(name).let { if (it is JsonNull) null else it.jsonPrimitive.doubleOrNull ?: throw InvalidObservationPointExport("invalid $name") }
    private fun JsonObject.bool(name: String) = element(name).jsonPrimitive.booleanOrNull ?: throw InvalidObservationPointExport("invalid $name")
    private fun JsonObject.uuid(name: String) = try { UUID.fromString(string(name)) } catch (error: Exception) { throw InvalidObservationPointExport("invalid $name", error) }
    private fun JsonObject.instant(name: String) = try { Instant.parse(string(name)) } catch (error: Exception) { throw InvalidObservationPointExport("invalid $name", error) }
    private fun JsonObject.nullableInstant(name: String) = nullableString(name)?.let { try { Instant.parse(it) } catch (error: Exception) { throw InvalidObservationPointExport("invalid $name", error) } }
    private fun JsonObject.array(name: String) = try { element(name).jsonArray } catch (error: Exception) { throw InvalidObservationPointExport("invalid $name", error) }
    private fun JsonObject.obj(name: String) = element(name).asObject(name)
    private fun JsonObject.nullableObject(name: String) = element(name).let { if (it is JsonNull) null else it.asObject(name) }

    private inline fun <reified T : Enum<T>> enum(value: String, label: String): T = try {
        enumValueOf<T>(value)
    } catch (error: Exception) {
        throw InvalidObservationPointExport("invalid $label", error)
    }

    private fun JsonObjectBuilder.putNullable(name: String, value: String?) { put(name, value?.let(::JsonPrimitive) ?: JsonNull) }
    private fun JsonObjectBuilder.putNullable(name: String, value: Double?) { put(name, value?.let(::JsonPrimitive) ?: JsonNull) }
}
