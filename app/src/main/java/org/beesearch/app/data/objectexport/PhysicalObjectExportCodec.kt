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
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.data.zip.MediaExpectation
import org.beesearch.app.data.zip.StagedZipArchive
import org.beesearch.app.data.zip.ZipSafetyException
import org.beesearch.app.data.zip.ZipSafetyFailure
import org.beesearch.app.data.zip.ZipSafetyPolicy
import org.beesearch.app.data.zip.validateZipRelativePath
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.parseResearchDate

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

    private fun acceptsVersion(version: Int): Boolean = when (version) {
        PhysicalObjectExportContract.LEGACY_FORMAT_VERSION -> false
        PhysicalObjectExportContract.TEMPORAL_FORMAT_VERSION -> true
        PhysicalObjectExportContract.FORMAT_VERSION -> true
        else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
    }

    private fun acceptsExtendedTemporal(version: Int): Boolean = version >= PhysicalObjectExportContract.FORMAT_VERSION

    fun encode(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
    ) = encodeVersion(graph, mediaBytes, output, PhysicalObjectExportContract.FORMAT_VERSION)

    fun encodeV2(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
    ) = encodeVersion(graph, mediaBytes, output, PhysicalObjectExportContract.TEMPORAL_FORMAT_VERSION)

    fun encodeV1(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
    ) = encodeVersion(graph, mediaBytes, output, PhysicalObjectExportContract.LEGACY_FORMAT_VERSION)

    private fun encodeVersion(
        graph: PhysicalObjectExportGraph,
        mediaBytes: Map<UUID, ArchivePayload>,
        output: OutputStream,
        version: Int,
    ) {
        val canonical = canonicalize(graph)
        if (canonical.media.size > PhysicalObjectExportContract.MAX_ENTRIES - 2) {
            throw InvalidPhysicalObjectExport("too many ZIP entries")
        }
        val allowFixationDate = acceptsVersion(version)
        PhysicalObjectExportValidator.validate(canonical, mediaBytes, allowFixationDate, acceptsExtendedTemporal(version))
        val objectBytes = objectJson(canonical, version).toString().toByteArray(StandardCharsets.UTF_8)
        requireMetadataSize(objectBytes.size.toLong())
        val mediaDescriptors = canonical.media.map { media ->
            val payload = mediaBytes.getValue(media.id)
            buildJsonObject {
                put("id", media.id.toString())
                put("entry", PhysicalObjectExportContract.mediaEntry(media.id))
                put("mediaType", media.type.name)
                put("byteLength", payload.size)
                put("sha256", payload.sha256)
                put("mimeType", media.mimeType?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
        val manifestBytes = buildJsonObject {
            put("profile", PhysicalObjectExportContract.PROFILE)
            put("formatVersion", version)
            put("physicalObjectId", canonical.id.toString())
            put("physicalObjectType", canonical.type.name)
            put("objectEntry", PhysicalObjectExportContract.OBJECT_ENTRY)
            put("objectByteLength", objectBytes.size)
            put("objectSha256", sha256Bytes(objectBytes))
            put("media", JsonArray(mediaDescriptors))
        }.toString().toByteArray(StandardCharsets.UTF_8)
        requireMetadataSize(manifestBytes.size.toLong())
        val metadataTotal = checkedAdd(objectBytes.size.toLong(), manifestBytes.size.toLong())
        if (metadataTotal > PhysicalObjectExportContract.MAX_TOTAL_METADATA_BYTES) {
            throw InvalidPhysicalObjectExport("metadata is too large")
        }
        try {
            org.beesearch.app.data.zip.checkedMediaTotal(
                mediaBytes.values.map { MediaExpectation(it.size, it.sha256) },
            )
        } catch (error: Exception) {
            throw InvalidPhysicalObjectExport("media size overflow", error)
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            putEntry(zip, PhysicalObjectExportContract.MANIFEST_ENTRY, manifestBytes)
            putEntry(zip, PhysicalObjectExportContract.OBJECT_ENTRY, objectBytes)
            canonical.media.forEach { media ->
                putPayloadEntry(zip, PhysicalObjectExportContract.mediaEntry(media.id), mediaBytes.getValue(media.id))
            }
        }
    }

    fun decode(input: InputStream): DecodedPhysicalObjectExport {
        val archive = readArchive(input)
        try {
            return decodeEntries(archive.entries, archive)
        } catch (error: Throwable) {
            try { archive.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private fun decodeEntries(
        entries: Map<String, ArchivePayload>,
        owner: java.io.Closeable,
    ): DecodedPhysicalObjectExport {
        val manifest = parseObject(entries[PhysicalObjectExportContract.MANIFEST_ENTRY], "manifest")
        manifest.requireKeys(
            "profile", "formatVersion", "physicalObjectId", "physicalObjectType",
            "objectEntry", "objectByteLength", "objectSha256", "media",
        )
        if (manifest.string("profile") != PhysicalObjectExportContract.PROFILE) {
            throw InvalidPhysicalObjectExport("unsupported profile")
        }
        val version = manifest.int("formatVersion")
        val allowFixationDate = acceptsVersion(version)
        val allowExtendedTemporal = acceptsExtendedTemporal(version)
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
        val mediaBytes = linkedMapOf<UUID, ArchivePayload>()
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

        val graph = parseGraph(parseObject(objectBytes, "object.json"), version)
        if (graph.id != objectId) throw InvalidPhysicalObjectExport("manifest object id mismatch")
        if (graph.type != objectType) throw InvalidPhysicalObjectExport("manifest object type mismatch")
        PhysicalObjectExportValidator.validate(graph, mediaBytes, allowFixationDate, allowExtendedTemporal)
        if (graph.media.map { it.id }.toSet() != describedIds) {
            throw InvalidPhysicalObjectExport("media manifest mismatch")
        }
        return DecodedPhysicalObjectExport(graph, mediaBytes, owner)
    }

    private fun canonicalize(graph: PhysicalObjectExportGraph) = graph.copy(
        media = graph.media.sortedWith(compareBy<PhysicalObjectMedia>({ it.createdAt }, { it.id.toString() })),
    )

    private fun objectJson(graph: PhysicalObjectExportGraph, version: Int): JsonObject = buildJsonObject {
        put("object", buildJsonObject {
            put("id", graph.id.toString())
            put("territoryId", graph.territoryId.toString())
            put("type", graph.type.name)
            put("sequenceNumber", graph.sequenceNumber)
            put("latitude", graph.latitude)
            put("longitude", graph.longitude)
            put("createdAt", graph.createdAt.toString())
            when (version) {
                PhysicalObjectExportContract.LEGACY_FORMAT_VERSION -> Unit
                PhysicalObjectExportContract.TEMPORAL_FORMAT_VERSION -> putNullable("fixationDate", canonicalFixationDate(graph.fixationDate))
                PhysicalObjectExportContract.FORMAT_VERSION -> {
                    putNullable("fixationDate", canonicalFixationDate(graph.fixationDate))
                    putNullableEpochMs("fixationAt", graph.fixationAt)
                    putNullableEpochMs("updatedAt", graph.updatedAt)
                }
                else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
            }
            putNullable("creatorObserverId", graph.creatorObserverId?.toString())
            putNullable("name", graph.name)
        })
        put("properties", graph.properties?.let(::propertiesJson) ?: JsonNull)
        put("media", buildJsonArray { graph.media.forEach { add(mediaJson(it)) } })
        put("territory", territoryJson(graph.territory))
        put("observer", graph.observer?.let(::observerJson) ?: JsonNull)
    }

    /** Canonical object-owned payload reused by the collection profile without repeating context. */
    internal fun collectionObjectBytes(
        graph: PhysicalObjectExportGraph,
        version: Int = PhysicalObjectExportContract.FORMAT_VERSION,
    ): ByteArray = buildJsonObject {
        put("object", buildJsonObject {
            put("id", graph.id.toString())
            put("territoryId", graph.territoryId.toString())
            put("type", graph.type.name)
            put("sequenceNumber", graph.sequenceNumber)
            put("latitude", graph.latitude)
            put("longitude", graph.longitude)
            put("createdAt", graph.createdAt.toString())
            when (version) {
                PhysicalObjectExportContract.LEGACY_FORMAT_VERSION -> Unit
                PhysicalObjectExportContract.TEMPORAL_FORMAT_VERSION -> putNullable("fixationDate", canonicalFixationDate(graph.fixationDate))
                PhysicalObjectExportContract.FORMAT_VERSION -> {
                    putNullable("fixationDate", canonicalFixationDate(graph.fixationDate))
                    putNullableEpochMs("fixationAt", graph.fixationAt)
                    putNullableEpochMs("updatedAt", graph.updatedAt)
                }
                else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
            }
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

    private fun parseGraph(root: JsonObject, version: Int): PhysicalObjectExportGraph {
        val hasFixationDate = when (version) {
            PhysicalObjectExportContract.LEGACY_FORMAT_VERSION -> false
            PhysicalObjectExportContract.TEMPORAL_FORMAT_VERSION,
            PhysicalObjectExportContract.FORMAT_VERSION -> true
            else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
        }
        root.requireKeys("object", "properties", "media", "territory", "observer")
        val identity = root.obj("object")
        identity.requireKeys(
            "id", "territoryId", "type", "sequenceNumber", "latitude", "longitude",
            "createdAt", "creatorObserverId", "name",
            *if (hasFixationDate) arrayOf("fixationDate") else emptyArray(),
            *if (version >= PhysicalObjectExportContract.FORMAT_VERSION) arrayOf("fixationAt", "updatedAt") else emptyArray(),
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
            fixationDate = if (hasFixationDate) identity.researchDate("fixationDate") else null,
            fixationAt = if (version >= PhysicalObjectExportContract.FORMAT_VERSION) identity.nullableEpochMs("fixationAt") else null,
            updatedAt = if (version >= PhysicalObjectExportContract.FORMAT_VERSION) identity.nullableEpochMs("updatedAt") else null,
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
        bytes: ArchivePayload,
        territory: TerritoryExportSnapshot,
        observers: Map<UUID, ObserverExportSnapshot>,
        version: Int = PhysicalObjectCollectionExportContract.FORMAT_VERSION,
    ): PhysicalObjectExportGraph {
        val hasFixationDate = when (version) {
            PhysicalObjectCollectionExportContract.LEGACY_FORMAT_VERSION -> false
            PhysicalObjectCollectionExportContract.TEMPORAL_FORMAT_VERSION,
            PhysicalObjectCollectionExportContract.FORMAT_VERSION -> true
            else -> throw InvalidPhysicalObjectExport("unsupported formatVersion")
        }
        val root = parseObject(bytes, "collection object")
        root.requireKeys("object", "properties", "media")
        val identity = root.obj("object")
        identity.requireKeys(
            "id", "territoryId", "type", "sequenceNumber", "latitude", "longitude",
            "createdAt", "creatorObserverId", "name",
            *if (hasFixationDate) arrayOf("fixationDate") else emptyArray(),
            *if (version >= PhysicalObjectCollectionExportContract.FORMAT_VERSION) arrayOf("fixationAt", "updatedAt") else emptyArray(),
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
            fixationDate = if (hasFixationDate) identity.researchDate("fixationDate") else null,
            fixationAt = if (version >= PhysicalObjectCollectionExportContract.FORMAT_VERSION) identity.nullableEpochMs("fixationAt") else null,
            updatedAt = if (version >= PhysicalObjectCollectionExportContract.FORMAT_VERSION) identity.nullableEpochMs("updatedAt") else null,
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

    private fun authorizeMedia(
        entries: Map<String, ArchivePayload>,
        allPaths: Set<String>,
    ): Map<String, MediaExpectation> {
        val mediaPaths = allPaths.filterTo(linkedSetOf()) { it.startsWith(PhysicalObjectExportContract.MEDIA_PREFIX) }
        val manifest = parseObject(entries[PhysicalObjectExportContract.MANIFEST_ENTRY], "manifest")
        manifest.requireKeys(
            "profile", "formatVersion", "physicalObjectId", "physicalObjectType",
            "objectEntry", "objectByteLength", "objectSha256", "media",
        )
        if (manifest.string("profile") != PhysicalObjectExportContract.PROFILE) {
            throw InvalidPhysicalObjectExport("unsupported profile")
        }
        val expected = linkedMapOf<String, MediaExpectation>()
        manifest.array("media").forEach { element ->
            val descriptor = element.asObject("media descriptor")
            descriptor.requireKeys("id", "entry", "mediaType", "byteLength", "sha256", "mimeType")
            val id = descriptor.uuid("id")
            val entry = descriptor.string("entry")
            validateEntryName(entry)
            if (entry != PhysicalObjectExportContract.mediaEntry(id) || expected.containsKey(entry)) {
                throw InvalidPhysicalObjectExport("invalid media entry")
            }
            enum<PhysicalObjectMediaType>(descriptor.string("mediaType"), "mediaType")
            val size = descriptor.long("byteLength")
            val sha = descriptor.string("sha256")
            if (size <= 0L || !sha.matches(hashPattern)) {
                throw InvalidPhysicalObjectExport("invalid media descriptor")
            }
            expected[entry] = MediaExpectation(size, sha)
        }
        if (expected.keys != mediaPaths) throw InvalidPhysicalObjectExport("unexpected ZIP entry")
        val declaredEntries = LinkedHashMap(entries)
        expected.forEach { (name, expectation) -> declaredEntries[name] = ArchivePayload.declared(expectation) }
        decodeEntries(declaredEntries, NoopCloseable).close()
        return expected
    }

    private fun checkedAdd(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (error: ArithmeticException) {
        throw InvalidPhysicalObjectExport("metadata size overflow", error)
    }

    private object NoopCloseable : java.io.Closeable {
        override fun close() = Unit
    }

    private fun readArchive(input: InputStream): StagedZipArchive = try {
        StagedZipArchive.readAuthorized(
            input,
            ZipSafetyPolicy(
                PhysicalObjectExportContract.MAX_ENTRIES,
                PhysicalObjectExportContract.MAX_METADATA_ENTRY_BYTES,
                PhysicalObjectExportContract.MAX_TOTAL_METADATA_BYTES,
            ),
            isMediaPath = { it.startsWith(PhysicalObjectExportContract.MEDIA_PREFIX) },
            authorize = { entries, mediaPaths -> authorizeMedia(entries, mediaPaths) },
        ).also { if (it.entries.isEmpty()) { it.close(); throw InvalidPhysicalObjectExport("empty archive") } }
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

    private fun putPayloadEntry(zip: ZipOutputStream, name: String, payload: ArchivePayload) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        payload.copyTo(zip)
        zip.closeEntry()
    }

    private fun requireMetadataSize(size: Long) {
        if (size > PhysicalObjectExportContract.MAX_METADATA_ENTRY_BYTES) {
            throw InvalidPhysicalObjectExport("entry is too large")
        }
    }

    private fun verifyBytes(bytes: ArchivePayload, expectedSize: Long, expectedSha: String, label: String) {
        if (expectedSize != bytes.size) {
            throw PhysicalObjectExportIntegrityError("$label size mismatch")
        }
        if (!expectedSha.matches(hashPattern) || expectedSha != bytes.sha256) {
            throw PhysicalObjectExportIntegrityError("$label SHA-256 mismatch")
        }
    }

    private fun parseObject(payload: ArchivePayload?, label: String): JsonObject {
        if (payload == null) throw InvalidPhysicalObjectExport("$label is missing")
        return try { json.parseToJsonElement(payload.readMetadata(PhysicalObjectExportContract.MAX_METADATA_ENTRY_BYTES).toString(StandardCharsets.UTF_8)).jsonObject }
        catch (error: Exception) { throw InvalidPhysicalObjectExport("malformed $label", error) }
    }

    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

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

    private fun JsonObject.researchDate(name: String) = element(name).let { element ->
        if (element is JsonNull) return@let null
        if (element !is JsonPrimitive || !element.isString) throw InvalidPhysicalObjectExport("invalid $name")
        try {
            parseResearchDate(element.content)
        } catch (error: IllegalArgumentException) {
            throw InvalidPhysicalObjectExport("invalid $name", error)
        }
    }

    private fun canonicalFixationDate(value: java.time.LocalDate?): String? = value?.toString()?.also {
        try {
            parseResearchDate(it)
        } catch (error: IllegalArgumentException) {
            throw InvalidPhysicalObjectExport("invalid fixationDate", error)
        }
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

    private fun JsonObject.nullableEpochMs(name: String): Instant? = element(name).let {
        if (it is JsonNull) null else try {
            if (it !is JsonPrimitive || it.isString) throw IllegalArgumentException()
            Instant.ofEpochMilli(it.longOrNull ?: throw IllegalArgumentException())
        } catch (error: Exception) {
            throw InvalidPhysicalObjectExport("invalid $name", error)
        }
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

    private fun JsonObjectBuilder.putNullableEpochMs(name: String, value: Instant?) {
        put(name, value?.toEpochMilli()?.let(::JsonPrimitive) ?: JsonNull)
    }
}
