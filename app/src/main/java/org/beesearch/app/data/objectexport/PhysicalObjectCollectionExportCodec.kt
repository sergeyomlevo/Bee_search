package org.beesearch.app.data.objectexport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.beesearch.app.data.zip.ZipReadGuard
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.data.zip.StagedZipArchive
import org.beesearch.app.data.zip.ZipSafetyException
import org.beesearch.app.data.zip.ZipSafetyFailure
import org.beesearch.app.data.zip.ZipSafetyPolicy
import org.beesearch.app.data.zip.validateZipRelativePath
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType

/** Deterministic and strict `PHYSICAL_OBJECT_COLLECTION` v1 ZIP codec. */
internal object PhysicalObjectCollectionExportCodec {
    private val json = Json { isLenient = false; ignoreUnknownKeys = false }
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val objectOrder = compareBy<PhysicalObjectExportGraph>({ it.sequenceNumber }, { it.id.toString() })
    private val mediaOrder = compareBy<PhysicalObjectMedia>({ it.createdAt }, { it.id.toString() })

    private fun acceptsVersion(version: Int): Boolean = when (version) {
        PhysicalObjectCollectionExportContract.LEGACY_FORMAT_VERSION -> false
        PhysicalObjectCollectionExportContract.FORMAT_VERSION -> true
        else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
    }

    fun encode(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
    ) = encodeVersion(graph, mediaBytes, output, PhysicalObjectCollectionExportContract.FORMAT_VERSION)

    fun encodeV1(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
    ) = encodeVersion(graph, mediaBytes, output, PhysicalObjectCollectionExportContract.LEGACY_FORMAT_VERSION)

    private fun encodeVersion(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
        version: Int,
    ) {
        val canonical = graph.copy(objects = graph.objects.sortedWith(objectOrder).map { value ->
            value.copy(media = value.media.sortedWith(mediaOrder))
        })
        validate(canonical, mediaBytes, allowFixationDate = acceptsVersion(version))

        val territoryBytes = territoryJson(canonical.territory).bytes()
        val observers = canonical.objects.mapNotNull { it.observer }.distinctBy { it.id }.sortedBy { it.id.toString() }
        val observersBytes = buildJsonArray { observers.forEach { add(observerJson(it)) } }.bytes()
        val objectPayloads = canonical.objects.associate {
            it.id to PhysicalObjectExportCodec.collectionObjectBytes(it, version)
        }
        objectPayloads.values.forEach(::requireEntrySize)

        val objectDescriptors = canonical.objects.map { value ->
            val bytes = objectPayloads.getValue(value.id)
            buildJsonObject {
                put("id", value.id.toString())
                put("entry", PhysicalObjectCollectionExportContract.objectEntry(value.id))
                put("byteLength", bytes.size)
                put("sha256", sha256Bytes(bytes))
            }
        }
        val mediaDescriptors = canonical.objects.flatMap { value ->
            value.media.map { media ->
                val bytes = mediaBytes.getValue(media.id)
                buildJsonObject {
                    put("id", media.id.toString())
                    put("physicalObjectId", value.id.toString())
                    put("entry", PhysicalObjectCollectionExportContract.mediaEntry(value.id, media.id))
                    put("mediaType", media.type.name)
                    put("byteLength", bytes.size)
                    put("sha256", bytes.sha256)
                    putNullable("mimeType", media.mimeType)
                }
            }
        }
        val manifestBytes = buildJsonObject {
            put("profile", PhysicalObjectCollectionExportContract.PROFILE)
            put("formatVersion", version)
            put("territoryId", canonical.territory.id.toString())
            put("physicalObjectType", canonical.type.name)
            put("objectCount", canonical.objects.size)
            put("territory", descriptor(PhysicalObjectCollectionExportContract.TERRITORY_ENTRY, territoryBytes))
            put("observers", descriptor(PhysicalObjectCollectionExportContract.OBSERVERS_ENTRY, observersBytes))
            put("objects", JsonArray(objectDescriptors))
            put("media", JsonArray(mediaDescriptors))
        }.bytes()
        requireEntrySize(manifestBytes)

        val total = manifestBytes.size.toLong() + territoryBytes.size + observersBytes.size +
            objectPayloads.values.sumOf { it.size.toLong() } + mediaBytes.values.sumOf { it.size }
        if (total > PhysicalObjectCollectionExportContract.MAX_TOTAL_BYTES) invalid("package is too large")

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            putEntry(zip, PhysicalObjectCollectionExportContract.MANIFEST_ENTRY, manifestBytes)
            putEntry(zip, PhysicalObjectCollectionExportContract.TERRITORY_ENTRY, territoryBytes)
            putEntry(zip, PhysicalObjectCollectionExportContract.OBSERVERS_ENTRY, observersBytes)
            canonical.objects.forEach { value ->
                putEntry(zip, PhysicalObjectCollectionExportContract.objectEntry(value.id), objectPayloads.getValue(value.id))
                value.media.forEach { media ->
                    putEntry(
                        zip,
                        PhysicalObjectCollectionExportContract.mediaEntry(value.id, media.id),
                        mediaBytes.getValue(media.id),
                    )
                }
            }
        }
    }

    fun decode(input: InputStream): DecodedPhysicalObjectCollectionExport {
        val archive = readArchive(input)
        try {
        val entries = archive.entries
        val manifest = parseObject(entries[PhysicalObjectCollectionExportContract.MANIFEST_ENTRY], "manifest")
        manifest.requireKeys(
            "profile", "formatVersion", "territoryId", "physicalObjectType", "objectCount",
            "territory", "observers", "objects", "media",
        )
        if (manifest.string("profile") != PhysicalObjectCollectionExportContract.PROFILE) invalid("unsupported profile")
        val version = manifest.int("formatVersion")
        val allowFixationDate = acceptsVersion(version)
        val territoryId = manifest.uuid("territoryId")
        val type = enum<PhysicalObjectType>(manifest.string("physicalObjectType"), "physicalObjectType")
        PhysicalObjectExportValidator.validateSupportedType(type)

        val territoryDescriptor = manifest.obj("territory")
        val territoryBytes = verifyDescriptor(
            territoryDescriptor,
            PhysicalObjectCollectionExportContract.TERRITORY_ENTRY,
            entries,
        )
        val territory = parseTerritory(territoryBytes)
        if (territory.id != territoryId) invalid("territory snapshot mismatch")

        val observerDescriptor = manifest.obj("observers")
        val observerBytes = verifyDescriptor(
            observerDescriptor,
            PhysicalObjectCollectionExportContract.OBSERVERS_ENTRY,
            entries,
        )
        val observers = parseObservers(observerBytes)

        val describedObjectIds = hashSetOf<UUID>()
        val describedObjectEntries = hashSetOf<String>()
        val objects = manifest.array("objects").map { element ->
            val descriptor = element.asObject("object descriptor")
            descriptor.requireKeys("id", "entry", "byteLength", "sha256")
            val id = descriptor.uuid("id")
            if (!describedObjectIds.add(id)) invalid("duplicate object id")
            val entry = descriptor.string("entry")
            validateEntryName(entry)
            if (entry != PhysicalObjectCollectionExportContract.objectEntry(id) || !describedObjectEntries.add(entry)) {
                invalid("invalid object entry")
            }
            val bytes = entries[entry] ?: invalid("object entry is missing")
            verifyBytes(bytes, descriptor.long("byteLength"), descriptor.string("sha256"), entry)
            PhysicalObjectExportCodec.parseCollectionObject(bytes, territory, observers, version).also { graph ->
                if (graph.id != id) invalid("object descriptor id mismatch")
                if (graph.territoryId != territoryId) invalid("object from another territory")
                if (graph.type != type) invalid("mixed physical object types")
            }
        }
        if (manifest.int("objectCount") != objects.size || objects.isEmpty()) invalid("object count mismatch")
        val usedObserverIds = objects.mapNotNull { it.creatorObserverId }.toSet()
        if (observers.keys != usedObserverIds) invalid("observer snapshot set mismatch")

        val describedMediaIds = hashSetOf<UUID>()
        val describedMediaEntries = hashSetOf<String>()
        val mediaOwners = hashMapOf<UUID, UUID>()
        val mediaTypes = hashMapOf<UUID, PhysicalObjectMediaType>()
        val mediaMimeTypes = hashMapOf<UUID, String?>()
        val mediaBytes = linkedMapOf<UUID, ArchivePayload>()
        manifest.array("media").forEach { element ->
            val descriptor = element.asObject("media descriptor")
            descriptor.requireKeys(
                "id", "physicalObjectId", "entry", "mediaType", "byteLength", "sha256", "mimeType",
            )
            val id = descriptor.uuid("id")
            val ownerId = descriptor.uuid("physicalObjectId")
            if (!describedMediaIds.add(id)) invalid("duplicate media id")
            if (ownerId !in describedObjectIds) invalid("media owner is not in collection")
            val entry = descriptor.string("entry")
            validateEntryName(entry)
            if (entry != PhysicalObjectCollectionExportContract.mediaEntry(ownerId, id) ||
                !describedMediaEntries.add(entry)
            ) invalid("invalid media entry")
            val bytes = entries[entry] ?: invalid("media entry is missing")
            verifyBytes(bytes, descriptor.long("byteLength"), descriptor.string("sha256"), entry)
            mediaOwners[id] = ownerId
            mediaTypes[id] = enum(descriptor.string("mediaType"), "mediaType")
            mediaMimeTypes[id] = descriptor.nullableString("mimeType")
            mediaBytes[id] = bytes
        }

        val expectedEntries = buildSet {
            add(PhysicalObjectCollectionExportContract.MANIFEST_ENTRY)
            add(PhysicalObjectCollectionExportContract.TERRITORY_ENTRY)
            add(PhysicalObjectCollectionExportContract.OBSERVERS_ENTRY)
            addAll(describedObjectEntries)
            addAll(describedMediaEntries)
        }
        if (entries.keys != expectedEntries) invalid("unexpected ZIP entry")

        val objectMedia = objects.flatMap { graph -> graph.media.map { graph.id to it } }
        if (objectMedia.map { it.second.id }.toSet() != describedMediaIds) invalid("media manifest mismatch")
        objectMedia.forEach { (ownerId, media) ->
            if (mediaOwners[media.id] != ownerId || mediaTypes[media.id] != media.type ||
                mediaMimeTypes[media.id] != media.mimeType
            ) invalid("media descriptor mismatch")
        }
        val collection = PhysicalObjectCollectionExportGraph(territory, type, objects)
        validate(collection, mediaBytes, allowFixationDate)
        return DecodedPhysicalObjectCollectionExport(collection, mediaBytes, archive)
        } catch (error: Throwable) {
            try { archive.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private fun validate(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        allowFixationDate: Boolean,
    ) {
        PhysicalObjectExportValidator.validateSupportedType(graph.type)
        if (graph.objects.isEmpty()) invalid("empty collection")
        if (graph.objects.size > PhysicalObjectCollectionExportContract.MAX_OBJECTS) invalid("too many objects")
        val objectIds = hashSetOf<UUID>()
        val mediaIds = hashSetOf<UUID>()
        val observerSnapshots = linkedMapOf<UUID, ObserverExportSnapshot>()
        graph.objects.forEach { value ->
            if (!objectIds.add(value.id)) invalid("duplicate object id")
            if (value.type != graph.type) invalid("mixed physical object types")
            if (value.territoryId != graph.territory.id || value.territory != graph.territory) {
                invalid("object from another territory")
            }
            value.observer?.let { snapshot ->
                val previous = observerSnapshots.putIfAbsent(snapshot.id, snapshot)
                if (previous != null && previous != snapshot) invalid("observer snapshot mismatch")
            }
            value.media.forEach { if (!mediaIds.add(it.id)) invalid("duplicate media id") }
            val ownBytes = value.media.associate { media ->
                media.id to (mediaBytes[media.id] ?: invalid("media blob set mismatch"))
            }
            PhysicalObjectExportValidator.validate(value, ownBytes, allowFixationDate)
        }
        if (mediaBytes.keys != mediaIds) invalid("media blob set mismatch")
        val entryCount = 3 + graph.objects.size + mediaIds.size
        if (entryCount > PhysicalObjectCollectionExportContract.MAX_ENTRIES) invalid("too many ZIP entries")
    }

    private fun descriptor(entry: String, bytes: ByteArray) = buildJsonObject {
        put("entry", entry)
        put("byteLength", bytes.size)
        put("sha256", sha256(bytes))
    }

    private fun territoryJson(value: TerritoryExportSnapshot) = buildJsonObject {
        put("id", value.id.toString())
        put("code", value.code)
        put("name", value.name)
    }

    private fun observerJson(value: ObserverExportSnapshot) = buildJsonObject {
        put("id", value.id.toString())
        put("code", value.code)
        put("lastName", value.lastName)
        put("firstName", value.firstName)
        putNullable("middleName", value.middleName)
    }

    private fun parseTerritory(bytes: ByteArray): TerritoryExportSnapshot {
        val value = parseObject(bytes, "territory")
        value.requireKeys("id", "code", "name")
        return TerritoryExportSnapshot(value.uuid("id"), value.string("code"), value.string("name"))
    }

    private fun parseObservers(bytes: ByteArray): Map<UUID, ObserverExportSnapshot> {
        val array = parseArray(bytes, "observers")
        val result = linkedMapOf<UUID, ObserverExportSnapshot>()
        array.forEach { element ->
            val value = element.asObject("observer")
            value.requireKeys("id", "code", "lastName", "firstName", "middleName")
            val snapshot = ObserverExportSnapshot(
                value.uuid("id"), value.string("code"), value.string("lastName"),
                value.string("firstName"), value.nullableString("middleName"),
            )
            if (result.put(snapshot.id, snapshot) != null) invalid("duplicate observer snapshot")
        }
        return result
    }

    private fun verifyDescriptor(
        descriptor: JsonObject,
        expectedEntry: String,
        entries: Map<String, ArchivePayload>,
    ): ByteArray {
        descriptor.requireKeys("entry", "byteLength", "sha256")
        if (descriptor.string("entry") != expectedEntry) invalid("unexpected descriptor entry")
        val bytes = entries[expectedEntry] ?: invalid("$expectedEntry is missing")
        verifyBytes(bytes, descriptor.long("byteLength"), descriptor.string("sha256"), expectedEntry)
        return try { bytes.readMetadata(PhysicalObjectCollectionExportContract.MAX_ENTRY_BYTES) }
        catch (error: Exception) { throw InvalidPhysicalObjectExport("malformed $expectedEntry", error) }
    }

    private fun readArchive(input: InputStream): StagedZipArchive = try {
        StagedZipArchive.read(input, ZipSafetyPolicy(
            PhysicalObjectCollectionExportContract.MAX_ENTRIES,
            PhysicalObjectCollectionExportContract.MAX_ENTRY_BYTES,
            PhysicalObjectCollectionExportContract.MAX_TOTAL_BYTES,
        )).also { if (it.entries.isEmpty()) { it.close(); invalid("empty archive") } }
    } catch (error: ZipSafetyException) {
        throw InvalidPhysicalObjectExport(if (error.isDirectory) "directory ZIP entry is not allowed" else zipFailureMessage(error.failure), error)
    } catch (error: PhysicalObjectExportException) {
        throw error
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("malformed archive", error)
    }

    private fun validateEntryName(name: String) = zipMechanics { validateZipRelativePath(name) }

    private fun zipFailureMessage(failure: ZipSafetyFailure) = when (failure) {
        ZipSafetyFailure.ENTRY_COUNT -> "too many ZIP entries"
        ZipSafetyFailure.UNSAFE_PATH -> "unsafe ZIP entry"
        ZipSafetyFailure.DUPLICATE -> "duplicate ZIP entry"
        ZipSafetyFailure.ENTRY_BYTES -> "ZIP entry is too large"
        ZipSafetyFailure.TOTAL_BYTES -> "archive is too large"
        else -> "malformed archive"
    }

    private inline fun <T> zipMechanics(isDirectory: Boolean = false, block: () -> T): T =
        try { block() } catch (error: ZipSafetyException) {
            throw InvalidPhysicalObjectExport(when (error.failure) {
                ZipSafetyFailure.ENTRY_COUNT -> "too many ZIP entries"
                ZipSafetyFailure.UNSAFE_PATH -> if (isDirectory) "directory ZIP entry is not allowed" else "unsafe ZIP entry"
                ZipSafetyFailure.DUPLICATE -> "duplicate ZIP entry"
                ZipSafetyFailure.ENTRY_BYTES -> "ZIP entry is too large"
                ZipSafetyFailure.TOTAL_BYTES -> "archive is too large"
                else -> throw InvalidPhysicalObjectExport("malformed archive", error)
            })
        }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun putEntry(zip: ZipOutputStream, name: String, payload: ArchivePayload) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        payload.copyTo(zip)
        zip.closeEntry()
    }

    private fun requireEntrySize(bytes: ByteArray) {
        if (bytes.size.toLong() > PhysicalObjectCollectionExportContract.MAX_ENTRY_BYTES) invalid("entry is too large")
    }

    private fun sha256Bytes(bytes: ByteArray): String = ArchivePayload.metadata(bytes).sha256

    private fun verifyBytes(bytes: ArchivePayload, size: Long, hash: String, label: String) {
        if (bytes.size > PhysicalObjectCollectionExportContract.MAX_ENTRY_BYTES) invalid("entry is too large")
        if (size != bytes.size) throw PhysicalObjectExportIntegrityError("$label size mismatch")
        if (!hash.matches(hashPattern) || hash != bytes.sha256) {
            throw PhysicalObjectExportIntegrityError("$label SHA-256 mismatch")
        }
    }

    private fun parseObject(bytes: ByteArray?, label: String): JsonObject {
        if (bytes == null) invalid("$label is missing")
        return try {
            json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            throw InvalidPhysicalObjectExport("malformed $label", error)
        }
    }

    private fun parseObject(payload: ArchivePayload?, label: String): JsonObject {
        if (payload == null) invalid("$label is missing")
        return try {
            json.parseToJsonElement(payload.readMetadata(PhysicalObjectCollectionExportContract.MAX_ENTRY_BYTES).toString(StandardCharsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            throw InvalidPhysicalObjectExport("malformed $label", error)
        }
    }

    private fun parseArray(bytes: ByteArray, label: String) = try {
        json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonArray
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("malformed $label", error)
    }

    private fun JsonElement.asObject(label: String) = try {
        jsonObject
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("$label must be an object", error)
    }

    private fun JsonObject.requireKeys(vararg expected: String) {
        if (keys != expected.toSet()) invalid("unexpected JSON fields")
    }

    private fun JsonObject.element(name: String) = this[name] ?: invalid("missing $name")
    private fun JsonObject.string(name: String) = try {
        element(name).jsonPrimitive.content
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }
    private fun JsonObject.nullableString(name: String): String? = element(name).let {
        if (it is JsonNull) null else it.jsonPrimitive.contentOrNull ?: invalid("invalid $name")
    }
    private fun JsonObject.int(name: String) = element(name).jsonPrimitive.intOrNull ?: invalid("invalid $name")
    private fun JsonObject.long(name: String) = element(name).jsonPrimitive.longOrNull ?: invalid("invalid $name")
    private fun JsonObject.uuid(name: String) = try {
        UUID.fromString(string(name))
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }
    private fun JsonObject.array(name: String) = try {
        element(name).jsonArray
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }
    private fun JsonObject.obj(name: String) = element(name).asObject(name)

    private inline fun <reified T : Enum<T>> enum(value: String, label: String): T = try {
        enumValueOf<T>(value)
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $label", error)
    }

    private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonElement.bytes() = toString().toByteArray(StandardCharsets.UTF_8)
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun invalid(message: String): Nothing = throw InvalidPhysicalObjectExport(message)
}
