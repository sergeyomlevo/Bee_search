package org.beesearch.app.data.objectexport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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
import org.beesearch.app.data.zip.ZipReadGuard
import org.beesearch.app.data.zip.ZipSafetyException
import org.beesearch.app.data.zip.ZipSafetyFailure
import org.beesearch.app.data.zip.ZipSafetyPolicy
import org.beesearch.app.data.zip.validateZipRelativePath
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType

/**
 * Pure `SINGLE_PHYSICAL_OBJECT` v1 ZIP encoder/decoder.
 *
 * It does not access Room, files, DataStore, or the network. Schema, graph and hash validation stay
 * local to this profile; only mechanical ZIP reader safety is shared with the other portable codecs.
 *
 * The writer is canonical (fixed entry order, `time = 0`, media ordered by creation time and id) and
 * the reader is strict: it accepts exactly the declared entries and exactly the declared JSON fields,
 * so a package that claims this profile cannot smuggle in foreign data or hide a damaged entry.
 */
internal object PhysicalObjectExportCodec {
    private val json = Json { isLenient = false; ignoreUnknownKeys = false }
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun encode(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ByteArray>,
        output: OutputStream,
    ) {
        val canonical = canonicalize(graph)
        PhysicalObjectExportValidator.validate(canonical, mediaBytes)
        val objectBytes = objectJson(canonical).toString().toByteArray(StandardCharsets.UTF_8)
        requireEntrySize(objectBytes.size.toLong())
        val mediaDescriptors = canonical.media.map { media ->
            val bytes = mediaBytes.getValue(media.id)
            buildJsonObject {
                put("id", media.id.toString())
                put("entry", PhysicalObjectExportContract.mediaEntry(media.id))
                put("mediaType", media.type.name)
                put("byteLength", bytes.size)
                put("sha256", sha256(bytes))
                put("mimeType", media.mimeType?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
        val manifestBytes = buildJsonObject {
            put("profile", PhysicalObjectExportContract.PROFILE)
            put("formatVersion", PhysicalObjectExportContract.FORMAT_VERSION)
            put("physicalObjectId", canonical.id.toString())
            put("physicalObjectType", canonical.type.name)
            put("objectEntry", PhysicalObjectExportContract.OBJECT_ENTRY)
            put("objectByteLength", objectBytes.size)
            put("objectSha256", sha256(objectBytes))
            put("media", JsonArray(mediaDescriptors))
        }.toString().toByteArray(StandardCharsets.UTF_8)
        requireEntrySize(manifestBytes.size.toLong())
        val total = manifestBytes.size.toLong() + objectBytes.size +
            mediaBytes.values.sumOf { it.size.toLong() }
        if (total > PhysicalObjectExportContract.MAX_TOTAL_BYTES) {
            throw InvalidPhysicalObjectExport("package is too large")
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            putEntry(zip, PhysicalObjectExportContract.MANIFEST_ENTRY, manifestBytes)
            putEntry(zip, PhysicalObjectExportContract.OBJECT_ENTRY, objectBytes)
            canonical.media.forEach { media ->
                putEntry(zip, PhysicalObjectExportContract.mediaEntry(media.id), mediaBytes.getValue(media.id))
            }
        }
    }

    fun decode(input: InputStream): DecodedPhysicalObjectExport {
        val entries = readArchive(input)
        val manifest = parseObject(entries[PhysicalObjectExportContract.MANIFEST_ENTRY], "manifest")
        manifest.requireKeys(
            "profile", "formatVersion", "physicalObjectId", "physicalObjectType",
            "objectEntry", "objectByteLength", "objectSha256", "media",
        )
        if (manifest.string("profile") != PhysicalObjectExportContract.PROFILE) {
            throw InvalidPhysicalObjectExport("unsupported profile")
        }
        if (manifest.int("formatVersion") != PhysicalObjectExportContract.FORMAT_VERSION) {
            throw InvalidPhysicalObjectExport("unsupported formatVersion")
        }
        val objectId = manifest.uuid("physicalObjectId")
        val objectType = enum<PhysicalObjectType>(manifest.string("physicalObjectType"), "physicalObjectType")
        PhysicalObjectExportValidator.validateSupportedType(objectType)
        val objectEntry = manifest.string("objectEntry")
        if (objectEntry != PhysicalObjectExportContract.OBJECT_ENTRY) {
            throw InvalidPhysicalObjectExport("unexpected object entry")
        }
        val objectBytes = entries[objectEntry] ?: throw InvalidPhysicalObjectExport("object.json is missing")
        verifyBytes(objectBytes, manifest.long("objectByteLength"), manifest.string("objectSha256"), "object.json")

        val descriptors = manifest.array("media")
        val describedIds = hashSetOf<UUID>()
        val describedEntries = hashSetOf<String>()
        val mediaBytes = linkedMapOf<UUID, ByteArray>()
        descriptors.forEach { element ->
            val descriptor = element.asObject("media descriptor")
            descriptor.requireKeys("id", "entry", "mediaType", "byteLength", "sha256", "mimeType")
            val id = descriptor.uuid("id")
            if (!describedIds.add(id)) throw InvalidPhysicalObjectExport("duplicate media id")
            val entry = descriptor.string("entry")
            validateEntryName(entry)
            if (entry != PhysicalObjectExportContract.mediaEntry(id) || !describedEntries.add(entry)) {
                throw InvalidPhysicalObjectExport("invalid media entry")
            }
            enum<PhysicalObjectMediaType>(descriptor.string("mediaType"), "mediaType")
            val bytes = entries[entry] ?: throw InvalidPhysicalObjectExport("media blob is missing")
            verifyBytes(bytes, descriptor.long("byteLength"), descriptor.string("sha256"), entry)
            mediaBytes[id] = bytes
        }
        val expectedEntries = buildSet {
            add(PhysicalObjectExportContract.MANIFEST_ENTRY)
            add(PhysicalObjectExportContract.OBJECT_ENTRY)
            addAll(describedEntries)
        }
        if (entries.keys != expectedEntries) throw InvalidPhysicalObjectExport("unexpected ZIP entry")

        val graph = parseGraph(parseObject(objectBytes, "object.json"))
        if (graph.id != objectId) throw InvalidPhysicalObjectExport("manifest object id mismatch")
        if (graph.type != objectType) throw InvalidPhysicalObjectExport("manifest object type mismatch")
        PhysicalObjectExportValidator.validate(graph, mediaBytes)
        if (graph.media.map { it.id }.toSet() != describedIds) {
            throw InvalidPhysicalObjectExport("media manifest mismatch")
        }
        return DecodedPhysicalObjectExport(graph, mediaBytes)
    }

    private fun canonicalize(graph: PhysicalObjectExportGraph) = graph.copy(
        media = graph.media.sortedWith(compareBy<PhysicalObjectMedia>({ it.createdAt }, { it.id.toString() })),
    )

    private fun objectJson(graph: PhysicalObjectExportGraph): JsonObject = buildJsonObject {
        put("object", buildJsonObject {
            put("id", graph.id.toString())
            put("territoryId", graph.territoryId.toString())
            put("type", graph.type.name)
            put("sequenceNumber", graph.sequenceNumber)
            put("latitude", graph.latitude)
            put("longitude", graph.longitude)
            put("createdAt", graph.createdAt.toString())
            putNullable("creatorObserverId", graph.creatorObserverId?.toString())
            putNullable("name", graph.name)
        })
        put("properties", graph.properties?.let(::propertiesJson) ?: JsonNull)
        put("media", buildJsonArray { graph.media.forEach { add(mediaJson(it)) } })
        put("territory", territoryJson(graph.territory))
        put("observer", graph.observer?.let(::observerJson) ?: JsonNull)
    }

    /** Canonical object-owned payload reused by the collection profile without repeating context. */
    internal fun collectionObjectBytes(graph: PhysicalObjectExportGraph): ByteArray = buildJsonObject {
        put("object", buildJsonObject {
            put("id", graph.id.toString())
            put("territoryId", graph.territoryId.toString())
            put("type", graph.type.name)
            put("sequenceNumber", graph.sequenceNumber)
            put("latitude", graph.latitude)
            put("longitude", graph.longitude)
            put("createdAt", graph.createdAt.toString())
            putNullable("creatorObserverId", graph.creatorObserverId?.toString())
            putNullable("name", graph.name)
        })
        put("properties", graph.properties?.let(::propertiesJson) ?: JsonNull)
        put("media", buildJsonArray {
            graph.media.sortedWith(compareBy<PhysicalObjectMedia>({ it.createdAt }, { it.id.toString() }))
                .forEach { media ->
                    add(mediaJson(media, PhysicalObjectCollectionExportContract.mediaEntry(graph.id, media.id)))
                }
        })
    }.toString().toByteArray(StandardCharsets.UTF_8)

    private fun propertiesJson(value: PhysicalObjectExportProperties): JsonObject = when (value) {
        is PhysicalObjectExportProperties.Hollow -> buildJsonObject {
            put("tree", value.value.tree)
            put("entranceHeightCm", value.value.entranceHeightCm)
            put("entranceAzimuthDeg", value.value.entranceAzimuthDeg)
            put("outerDiameterCm", value.value.outerDiameterCm)
            putNullable("internalDiameterCm", value.value.internalDiameterCm)
            putNullable("notes", value.value.notes)
        }

        is PhysicalObjectExportProperties.LogHive -> buildJsonObject {
            put("tree", value.value.tree)
            put("entranceHeightCm", value.value.entranceHeightCm)
            put("entranceAzimuthDeg", value.value.entranceAzimuthDeg)
            put("outerDiameterCm", value.value.outerDiameterCm)
            put("material", value.value.material)
            put("internalDiameterCm", value.value.internalDiameterCm)
            put("internalHeightCm", value.value.internalHeightCm)
            putNullable("notes", value.value.notes)
        }
    }

    private fun mediaJson(
        value: PhysicalObjectMedia,
        packageEntry: String = PhysicalObjectExportContract.mediaEntry(value.id),
    ) = buildJsonObject {
        put("id", value.id.toString())
        put("physicalObjectId", value.physicalObjectId.toString())
        put("type", value.type.name)
        put("relativePath", value.relativePath)
        putNullable("originalFileName", value.originalFileName)
        putNullable("mimeType", value.mimeType)
        put("byteSize", value.byteSize)
        put("sha256", value.sha256)
        put("createdAt", value.createdAt.toString())
        put("packageEntry", packageEntry)
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

    private fun parseGraph(root: JsonObject): PhysicalObjectExportGraph {
        root.requireKeys("object", "properties", "media", "territory", "observer")
        val identity = root.obj("object")
        identity.requireKeys(
            "id", "territoryId", "type", "sequenceNumber", "latitude", "longitude",
            "createdAt", "creatorObserverId", "name",
        )
        val type = enum<PhysicalObjectType>(identity.string("type"), "type")
        return PhysicalObjectExportGraph(
            id = identity.uuid("id"),
            type = type,
            territoryId = identity.uuid("territoryId"),
            sequenceNumber = identity.int("sequenceNumber"),
            latitude = identity.double("latitude"),
            longitude = identity.double("longitude"),
            createdAt = identity.instant("createdAt"),
            creatorObserverId = identity.nullableString("creatorObserverId")?.let(::uuid),
            name = identity.nullableString("name"),
            properties = root.nullableObject("properties")?.toProperties(type),
            media = root.array("media").map { it.asObject("media item").toMedia() },
            territory = root.obj("territory").toTerritory(),
            observer = root.nullableObject("observer")?.toObserver(),
        )
    }

    /** Strict decoder for one context-free object entry in a collection package. */
    internal fun parseCollectionObject(
        bytes: ByteArray,
        territory: TerritoryExportSnapshot,
        observers: Map<UUID, ObserverExportSnapshot>,
    ): PhysicalObjectExportGraph {
        val root = parseObject(bytes, "collection object")
        root.requireKeys("object", "properties", "media")
        val identity = root.obj("object")
        identity.requireKeys(
            "id", "territoryId", "type", "sequenceNumber", "latitude", "longitude",
            "createdAt", "creatorObserverId", "name",
        )
        val id = identity.uuid("id")
        val type = enum<PhysicalObjectType>(identity.string("type"), "type")
        val creatorId = identity.nullableString("creatorObserverId")?.let(::uuid)
        val graph = PhysicalObjectExportGraph(
            id = id,
            type = type,
            territoryId = identity.uuid("territoryId"),
            sequenceNumber = identity.int("sequenceNumber"),
            latitude = identity.double("latitude"),
            longitude = identity.double("longitude"),
            createdAt = identity.instant("createdAt"),
            creatorObserverId = creatorId,
            name = identity.nullableString("name"),
            properties = root.nullableObject("properties")?.toProperties(type),
            media = root.array("media").map { element ->
                val media = element.asObject("media item").toMedia(
                    PhysicalObjectCollectionExportContract.mediaEntry(id, element.asObject("media item").uuid("id")),
                )
                media
            },
            territory = territory,
            observer = creatorId?.let(observers::get),
        )
        if (creatorId != null && graph.observer == null) {
            throw InvalidPhysicalObjectExport("missing observer snapshot")
        }
        return graph
    }

    private fun JsonObject.toProperties(type: PhysicalObjectType): PhysicalObjectExportProperties = try {
        when (type) {
            PhysicalObjectType.HOLLOW -> {
                requireKeys(
                    "tree", "entranceHeightCm", "entranceAzimuthDeg", "outerDiameterCm",
                    "internalDiameterCm", "notes",
                )
                PhysicalObjectExportProperties.Hollow(
                    HollowProperties(
                        tree = string("tree"),
                        entranceHeightCm = double("entranceHeightCm"),
                        entranceAzimuthDeg = int("entranceAzimuthDeg"),
                        outerDiameterCm = double("outerDiameterCm"),
                        internalDiameterCm = nullableDouble("internalDiameterCm"),
                        notes = nullableString("notes"),
                    ),
                )
            }

            PhysicalObjectType.LOG_HIVE -> {
                requireKeys(
                    "tree", "entranceHeightCm", "entranceAzimuthDeg", "outerDiameterCm", "material",
                    "internalDiameterCm", "internalHeightCm", "notes",
                )
                PhysicalObjectExportProperties.LogHive(
                    LogHiveProperties(
                        tree = string("tree"),
                        entranceHeightCm = double("entranceHeightCm"),
                        entranceAzimuthDeg = int("entranceAzimuthDeg"),
                        outerDiameterCm = double("outerDiameterCm"),
                        material = string("material"),
                        internalDiameterCm = double("internalDiameterCm"),
                        internalHeightCm = double("internalHeightCm"),
                        notes = nullableString("notes"),
                    ),
                )
            }

            PhysicalObjectType.APIARY -> throw UnsupportedPhysicalObjectExportType(
                "unsupported physical object type",
            )
        }
    } catch (error: PhysicalObjectExportException) {
        throw error
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid object properties", error)
    }

    private fun JsonObject.toMedia(
        expectedPackageEntry: String = PhysicalObjectExportContract.mediaEntry(uuid("id")),
    ): PhysicalObjectMedia {
        requireKeys(
            "id", "physicalObjectId", "type", "relativePath", "originalFileName", "mimeType",
            "byteSize", "sha256", "createdAt", "packageEntry",
        )
        val id = uuid("id")
        if (string("packageEntry") != expectedPackageEntry) {
            throw InvalidPhysicalObjectExport("media package entry mismatch")
        }
        return PhysicalObjectMedia(
            id = id,
            physicalObjectId = uuid("physicalObjectId"),
            type = enum(string("type"), "media type"),
            relativePath = string("relativePath"),
            originalFileName = nullableString("originalFileName"),
            mimeType = nullableString("mimeType"),
            byteSize = long("byteSize"),
            sha256 = string("sha256"),
            createdAt = instant("createdAt"),
        )
    }

    private fun JsonObject.toTerritory(): TerritoryExportSnapshot {
        requireKeys("id", "code", "name")
        return TerritoryExportSnapshot(
            id = uuid("id"),
            code = string("code"),
            name = string("name"),
        )
    }

    private fun JsonObject.toObserver(): ObserverExportSnapshot {
        requireKeys("id", "code", "lastName", "firstName", "middleName")
        return ObserverExportSnapshot(
            id = uuid("id"),
            code = string("code"),
            lastName = string("lastName"),
            firstName = string("firstName"),
            middleName = nullableString("middleName"),
        )
    }

    private fun readArchive(input: InputStream): Map<String, ByteArray> = try {
        val result = linkedMapOf<String, ByteArray>()
        val guard = ZipReadGuard(ZipSafetyPolicy(
            PhysicalObjectExportContract.MAX_ENTRIES,
            PhysicalObjectExportContract.MAX_ENTRY_BYTES,
            PhysicalObjectExportContract.MAX_TOTAL_BYTES,
        ))
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                zipMechanics(entry.isDirectory) { guard.acceptEntry(entry.name, entry.isDirectory) }
                val bytes = zipMechanics { guard.readEntry(zip) }
                result[entry.name] = bytes
                zip.closeEntry()
            }
        }
        if (result.isEmpty()) throw InvalidPhysicalObjectExport("empty archive")
        result
    } catch (error: PhysicalObjectExportException) {
        throw error
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("malformed archive", error)
    }

    private fun validateEntryName(name: String) = zipMechanics { validateZipRelativePath(name) }

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

    private fun requireEntrySize(size: Long) {
        if (size > PhysicalObjectExportContract.MAX_ENTRY_BYTES) {
            throw InvalidPhysicalObjectExport("entry is too large")
        }
    }

    private fun verifyBytes(bytes: ByteArray, expectedSize: Long, expectedSha: String, label: String) {
        requireEntrySize(bytes.size.toLong())
        if (expectedSize != bytes.size.toLong()) {
            throw PhysicalObjectExportIntegrityError("$label size mismatch")
        }
        if (!expectedSha.matches(hashPattern) || expectedSha != sha256(bytes)) {
            throw PhysicalObjectExportIntegrityError("$label SHA-256 mismatch")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseObject(bytes: ByteArray?, label: String): JsonObject {
        if (bytes == null) throw InvalidPhysicalObjectExport("$label is missing")
        return try {
            json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            throw InvalidPhysicalObjectExport("malformed $label", error)
        }
    }

    private fun JsonElement.asObject(label: String) = try {
        jsonObject
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("$label must be an object", error)
    }

    /** The declared schema is the whole schema: an undeclared field makes the package invalid. */
    private fun JsonObject.requireKeys(vararg expected: String) {
        if (keys != expected.toSet()) throw InvalidPhysicalObjectExport("unexpected JSON fields")
    }

    private fun JsonObject.element(name: String) = this[name] ?: throw InvalidPhysicalObjectExport("missing $name")

    private fun JsonObject.string(name: String) = try {
        element(name).jsonPrimitive.content
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }

    private fun JsonObject.nullableString(name: String): String? = element(name).let {
        if (it is JsonNull) null else it.jsonPrimitive.contentOrNull ?: throw InvalidPhysicalObjectExport("invalid $name")
    }

    private fun JsonObject.int(name: String) = element(name).jsonPrimitive.intOrNull
        ?: throw InvalidPhysicalObjectExport("invalid $name")

    private fun JsonObject.long(name: String) = element(name).jsonPrimitive.longOrNull
        ?: throw InvalidPhysicalObjectExport("invalid $name")

    private fun JsonObject.double(name: String) = element(name).jsonPrimitive.doubleOrNull
        ?: throw InvalidPhysicalObjectExport("invalid $name")

    private fun JsonObject.nullableDouble(name: String): Double? = element(name).let {
        if (it is JsonNull) null else it.jsonPrimitive.doubleOrNull ?: throw InvalidPhysicalObjectExport("invalid $name")
    }

    private fun JsonObject.uuid(name: String) = try {
        UUID.fromString(string(name))
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }

    private fun JsonObject.instant(name: String) = try {
        Instant.parse(string(name))
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }

    private fun JsonObject.array(name: String) = try {
        element(name).jsonArray
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $name", error)
    }

    private fun JsonObject.obj(name: String) = element(name).asObject(name)

    private fun JsonObject.nullableObject(name: String) = element(name).let {
        if (it is JsonNull) null else it.asObject(name)
    }

    private inline fun <reified T : Enum<T>> enum(value: String, label: String): T = try {
        enumValueOf<T>(value)
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid $label", error)
    }

    private fun uuid(value: String): UUID = try {
        UUID.fromString(value)
    } catch (error: Exception) {
        throw InvalidPhysicalObjectExport("invalid uuid", error)
    }

    private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonObjectBuilder.putNullable(name: String, value: Double?) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }
}
