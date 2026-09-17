package org.beesearch.app.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.beesearch.app.BuildConfig
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.backup.*
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class BackupValidationResult(val archiveId: UUID, val logicalContentSha256: String)
internal fun interface RestoreCheckpoint { fun afterCollection(name: String) }
internal data class PortableSettingsSnapshot(
    val currentTerritoryId: UUID?, val currentObserverId: UUID?, val coverage: Map<UUID, String>,
)
internal interface PortableSettingsStore {
    suspend fun snapshot(): PortableSettingsSnapshot
    suspend fun replace(snapshot: PortableSettingsSnapshot)
}

internal class DataStorePortableSettingsStore(private val store: DataStore<Preferences>) : PortableSettingsStore {
    override suspend fun snapshot(): PortableSettingsSnapshot {
        val prefs = store.data.first()
        val coverage = linkedMapOf<UUID, String>()
        prefs.asMap().entries.filter { it.key.name.startsWith(COVERAGE_PREFIX) }
            .sortedBy { it.key.name }.forEach { (key, raw) ->
                val id = parseUuid(key.name.removePrefix(COVERAGE_PREFIX), "coverage key")
                val value = raw as? String ?: throw BackupDomainInvariantViolation("coverage is not a string")
                MapCoverageValidator.validate(value)
                if (coverage.put(id, value) != null) throw DuplicateBackupIdentity("duplicate coverage $id")
            }
        return PortableSettingsSnapshot(
            prefs[CURRENT_TERRITORY]?.let { parseUuid(it, "current_territory_id") },
            prefs[CURRENT_OBSERVER]?.let { parseUuid(it, "current_observer_id") },
            coverage,
        )
    }

    override suspend fun replace(snapshot: PortableSettingsSnapshot) {
        store.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(COVERAGE_PREFIX) }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
            if (snapshot.currentTerritoryId == null) prefs.remove(CURRENT_TERRITORY)
            else prefs[CURRENT_TERRITORY] = snapshot.currentTerritoryId.toString()
            if (snapshot.currentObserverId == null) prefs.remove(CURRENT_OBSERVER)
            else prefs[CURRENT_OBSERVER] = snapshot.currentObserverId.toString()
            snapshot.coverage.entries.sortedBy { it.key.toString() }.forEach { (id, value) ->
                prefs[stringPreferencesKey("$COVERAGE_PREFIX$id")] = value
            }
        }
    }

    private companion object {
        const val COVERAGE_PREFIX = "map_coverage_"
        val CURRENT_TERRITORY = stringPreferencesKey("current_territory_id")
        val CURRENT_OBSERVER = stringPreferencesKey("current_observer_id")
    }
}

internal class BackupService(
    private val database: BeeSearchDatabase,
    private val settings: PortableSettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    private val sourceAppVersion: String = BuildConfig.VERSION_NAME,
    private val checkpoint: RestoreCheckpoint = RestoreCheckpoint { },
) {
    constructor(
        database: BeeSearchDatabase, settings: DataStore<Preferences>, clock: Clock = Clock.systemUTC(),
        sourceAppVersion: String = BuildConfig.VERSION_NAME, checkpoint: RestoreCheckpoint = RestoreCheckpoint { },
    ) : this(database, DataStorePortableSettingsStore(settings), clock, sourceAppVersion, checkpoint)

    suspend fun export(output: File): UUID {
        val graph = database.withTransaction { database.backupDao().snapshot() }
        validateGraph(graph)
        val portable = settings.snapshot()
        validateSettings(portable, graph)
        val blobs = blobs(graph, portable)
        val id = UUID.randomUUID()
        output.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            put(zip, MANIFEST, manifest(id, clock.instant(), sourceAppVersion, blobs))
            blobs.forEach { put(zip, it.path, it.bytes) }
        }
        return id
    }

    fun validate(input: File): BackupValidationResult {
        val parsed = parse(readArchive(input))
        return BackupValidationResult(parsed.archiveId, logicalDigest(parsed.blobs))
    }

    suspend fun restore(input: File) {
        val parsed = parse(readArchive(input)) // All archive/graph validation precedes Room access.
        try {
            database.withTransaction {
                val dao = database.backupDao()
                if (dao.totalCount() != 0) throw BackupDestinationNotEmpty("research database is not empty")
                dao.insertTerritories(parsed.graph.territories); checkpoint.afterCollection("territories")
                dao.insertObservers(parsed.graph.observers); checkpoint.afterCollection("observers")
                dao.insertObservationPoints(parsed.graph.points); checkpoint.afterCollection("observation-points")
                dao.insertBees(parsed.graph.bees); checkpoint.afterCollection("bees")
                dao.insertFlightCycles(parsed.graph.cycles); checkpoint.afterCollection("flight-cycles")
            }
        } catch (error: BackupDestinationNotEmpty) {
            throw error
        } catch (error: Exception) {
            throw BackupDatabaseRestoreFailure("database restore failed", error)
        }
        applySettings(parsed, "research restored, settings were not applied")
    }

    /** Revalidates the archive and retries only portable state after a post-commit settings failure. */
    suspend fun retrySettings(input: File) = applySettings(parse(readArchive(input)), "settings were not applied")

    private suspend fun applySettings(parsed: Parsed, message: String) {
        try {
            val actual = database.withTransaction { database.backupDao().snapshot() }
            val territoryIds = actual.territories.mapTo(hashSetOf()) { it.id }
            val observerIds = actual.observers.mapTo(hashSetOf()) { it.id }
            if (!territoryIds.containsAll(parsed.settings.coverage.keys)) throw BrokenBackupForeignKey("coverage territory missing")
            settings.replace(parsed.settings.copy(
                currentTerritoryId = parsed.settings.currentTerritoryId?.takeIf(territoryIds::contains),
                currentObserverId = parsed.settings.currentObserverId?.takeIf(observerIds::contains),
            ))
        } catch (error: Exception) {
            throw BackupSettingsRestoreFailure(message, error)
        }
    }
}

private data class Blob(val name: String, val path: String, val bytes: ByteArray, val count: Int)
private data class Graph(
    val territories: List<TerritoryEntity>, val observers: List<ObserverEntity>,
    val points: List<ObservationPointEntity>, val bees: List<BeeEntity>, val cycles: List<FlightCycleEntity>,
)
private data class Parsed(val archiveId: UUID, val graph: Graph, val settings: PortableSettingsSnapshot, val blobs: List<Blob>)

private suspend fun BackupDao.snapshot() = Graph(territories(), observers(), observationPoints(), bees(), flightCycles())
private suspend fun BackupDao.totalCount() = territoryCount() + observerCount() + observationPointCount() + beeCount() + flightCycleCount()

private const val MANIFEST = "manifest.json"
private const val MAX_ENTRIES = 64
private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
private val JSON = Json { isLenient = false; ignoreUnknownKeys = false }

internal class ZipEntryTracker {
    private val names = hashSetOf<String>()
    fun accept(name: String) {
        if (!names.add(name)) throw MalformedBackup("duplicate ZIP entry")
    }
}

private fun blobs(graph: Graph, settings: PortableSettingsSnapshot): List<Blob> {
    fun rows(name: String, values: List<String>): Blob {
        val text = values.joinToString("\n", postfix = if (values.isEmpty()) "" else "\n")
        return Blob(name, BackupContractV1.collections.getValue(name), text.toByteArray(StandardCharsets.UTF_8), values.size)
    }
    return listOf(
        rows("territories", graph.territories.sortedBy { it.id.toString() }.map { it.json() }),
        rows("observers", graph.observers.sortedBy { it.id.toString() }.map { it.json() }),
        rows("observation-points", graph.points.sortedBy { it.id.toString() }.map { it.json() }),
        rows("bees", graph.bees.sortedBy { it.id.toString() }.map { it.json() }),
        rows("flight-cycles", graph.cycles.sortedBy { it.id.toString() }.map { it.json() }),
        rows("portable-settings", listOf(obj("currentTerritoryId" to j(settings.currentTerritoryId), "currentObserverId" to j(settings.currentObserverId)))),
        rows("map-coverage", settings.coverage.entries.sortedBy { it.key.toString() }.map { obj("territoryId" to j(it.key), "encoded" to j(it.value)) }),
    )
}

private fun manifest(id: UUID, created: Instant, appVersion: String, blobs: List<Blob>) = obj(
    "backupFormatVersion" to "1", "archiveSchemaVersion" to "1", "archiveId" to j(id),
    "createdAt" to created.toEpochMilli().toString(), "sourceAppVersion" to j(appVersion),
    "roomSchemaVersion" to "6", "profile" to j(BackupContractV1.PROFILE),
    "collections" to blobs.joinToString(",", "[", "]") { obj(
        "name" to j(it.name), "path" to j(it.path), "collectionSchemaVersion" to "1", "required" to "true",
        "recordCount" to it.count.toString(), "byteLength" to it.bytes.size.toString(), "sha256" to j(sha256(it.bytes)),
    ) },
).toByteArray(StandardCharsets.UTF_8)

private fun parse(entries: Map<String, ByteArray>): Parsed {
    val manifest = objectFrom(entries[MANIFEST] ?: throw MissingBackupCollection(MANIFEST), "manifest")
    if (manifest.int("backupFormatVersion") != 1) throw UnsupportedBackupFormat("unsupported backup format")
    if (manifest.int("archiveSchemaVersion") != 1) throw UnsupportedArchiveSchema("unsupported archive schema")
    val archiveId = manifest.uuid("archiveId"); manifest.long("createdAt")
    if (manifest.string("sourceAppVersion").isBlank()) throw MalformedBackup("blank sourceAppVersion")
    manifest.int("roomSchemaVersion")
    if (manifest.string("profile") != BackupContractV1.PROFILE) throw UnsupportedBackupFormat("unsupported profile")
    val array = try { manifest.field("collections").jsonArray } catch (e: Exception) { throw MalformedBackup("collections must be an array", e) }
    val known = linkedMapOf<String, Blob>()
    val describedNames = hashSetOf<String>()
    val describedPaths = hashSetOf<String>()
    val optionalPaths = hashSetOf<String>()
    array.forEach { element ->
        val item = try { element.jsonObject } catch (e: Exception) { throw MalformedBackup("invalid collection descriptor", e) }
        val name = item.string("name")
        if (!describedNames.add(name)) throw MalformedBackup("duplicate collection $name")
        val required = item.bool("required")
        if (name !in BackupContractV1.collections) {
            if (required) throw UnknownRequiredBackupCollection(name)
            val path = item.string("path")
            validatePathName(path)
            if (!describedPaths.add(path)) throw MalformedBackup("duplicate collection path $path")
            entries[path]?.let { bytes ->
                if (item.long("byteLength") != bytes.size.toLong()) throw BackupIntegrityMismatch("byteLength mismatch for $name")
                val expectedHash = item.string("sha256")
                if (!expectedHash.matches(Regex("[0-9a-f]{64}")) || sha256(bytes) != expectedHash) throw BackupIntegrityMismatch("sha256 mismatch for $name")
                if (item.int("recordCount") < 0) throw BackupIntegrityMismatch("recordCount mismatch for $name")
                optionalPaths += path
            }
            return@forEach
        }
        if (item.int("collectionSchemaVersion") != 1) throw UnsupportedArchiveSchema("unsupported collection schema $name")
        val path = item.string("path")
        if (path != BackupContractV1.collections.getValue(name)) throw MalformedBackup("unexpected path for $name")
        if (!describedPaths.add(path)) throw MalformedBackup("duplicate collection path $path")
        val bytes = entries[path] ?: throw MissingBackupCollection(name)
        if (item.long("byteLength") != bytes.size.toLong()) throw BackupIntegrityMismatch("byteLength mismatch for $name")
        val expectedHash = item.string("sha256")
        if (!expectedHash.matches(Regex("[0-9a-f]{64}")) || sha256(bytes) != expectedHash) throw BackupIntegrityMismatch("sha256 mismatch for $name")
        val count = item.int("recordCount")
        if (count < 0 || rows(bytes, name).size != count) throw BackupIntegrityMismatch("recordCount mismatch for $name")
        known[name] = Blob(name, path, bytes, count)
    }
    BackupContractV1.collections.keys.forEach { if (it !in known) throw MissingBackupCollection(it) }
    val listedPaths = known.values.mapTo(mutableSetOf(MANIFEST)) { it.path }.apply { addAll(optionalPaths) }
    if (entries.keys != listedPaths) throw MalformedBackup("unlisted ZIP entry")
    fun objects(name: String) = rows(known.getValue(name).bytes, name).map { objectFrom(it.toByteArray(StandardCharsets.UTF_8), name) }
    val graph = Graph(
        objects("territories").map(::territory), objects("observers").map(::observer),
        objects("observation-points").map(::point), objects("bees").map(::bee), objects("flight-cycles").map(::cycle),
    )
    val portableRows = objects("portable-settings")
    if (portableRows.size != 1) throw BackupIntegrityMismatch("portable-settings must contain one record")
    val coverage = linkedMapOf<UUID, String>()
    objects("map-coverage").forEach {
        val id = it.uuid("territoryId"); val encoded = it.string("encoded"); MapCoverageValidator.validate(encoded)
        if (coverage.put(id, encoded) != null) throw DuplicateBackupIdentity("duplicate coverage $id")
    }
    val portable = portableRows.single()
    val settings = PortableSettingsSnapshot(portable.optionalUuid("currentTerritoryId"), portable.optionalUuid("currentObserverId"), coverage)
    validateGraph(graph); validateSettings(settings, graph)
    return Parsed(archiveId, graph, settings, known.values.toList())
}

private fun readArchive(file: File): Map<String, ByteArray> = try {
    val result = linkedMapOf<String, ByteArray>(); val tracker = ZipEntryTracker(); var total = 0L
    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (result.size >= MAX_ENTRIES) throw MalformedBackup("too many ZIP entries")
            validatePath(entry)
            tracker.accept(entry.name)
            val bytes = readLimited(zip, MAX_ENTRY_BYTES); total += bytes.size
            if (total > MAX_TOTAL_BYTES) throw MalformedBackup("archive is too large")
            result[entry.name] = bytes
            zip.closeEntry()
        }
    }
    if (result.isEmpty()) throw MalformedBackup("empty archive")
    result
} catch (e: BackupException) { throw e } catch (e: Exception) { throw MalformedBackup("malformed archive", e) }

private fun validatePath(entry: ZipEntry) {
    if (entry.isDirectory) throw MalformedBackup("unsafe ZIP path")
    validatePathName(entry.name)
}
private fun validatePathName(name: String) { val parts = name.split('/'); if (name.isBlank() || name.startsWith('/') || name.contains('\\') || name.contains(':') || parts.any { it.isBlank() || it == "." || it == ".." }) throw MalformedBackup("unsafe ZIP path") }
private fun readLimited(input: InputStream, limit: Long): ByteArray {
    val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
    while (true) { val n = input.read(buffer); if (n < 0) break; total += n; if (total > limit) throw MalformedBackup("ZIP entry too large"); out.write(buffer, 0, n) }
    return out.toByteArray()
}
private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name).apply { time = 0L }); zip.write(bytes); zip.closeEntry() }
private fun logicalDigest(blobs: List<Blob>): String { val d = MessageDigest.getInstance("SHA-256"); blobs.sortedBy { it.name }.forEach { d.update(it.name.toByteArray()); d.update(0); d.update(it.bytes) }; return d.digest().hex() }

private fun validateSettings(settings: PortableSettingsSnapshot, graph: Graph) {
    val territories = graph.territories.mapTo(hashSetOf()) { it.id }
    if (!territories.containsAll(settings.coverage.keys)) throw BrokenBackupForeignKey("coverage territory missing")
    settings.coverage.values.forEach(MapCoverageValidator::validate)
}

private fun validateGraph(g: Graph) {
    val ids = hashSetOf<UUID>(); fun ids(label: String, values: List<UUID>) = values.forEach { if (!ids.add(it)) throw DuplicateBackupIdentity("duplicate $label id $it") }
    ids("territory", g.territories.map { it.id }); ids("observer", g.observers.map { it.id }); ids("point", g.points.map { it.id }); ids("bee", g.bees.map { it.id }); ids("cycle", g.cycles.map { it.id })
    unique(g.territories, "territory code") { it.code }; unique(g.observers, "observer code") { it.code }
    g.territories.forEach { domain(it.code.isNotBlank() && it.name.isNotBlank() && it.region.isNotBlank() && it.district.isNotBlank(), "blank territory field"); ordered(it.createdAt, it.updatedAt, "territory") }
    g.observers.forEach { domain(it.code.isNotBlank() && it.lastName.isNotBlank() && it.firstName.isNotBlank(), "blank observer field"); ordered(it.createdAt, it.updatedAt, "observer") }
    val territories = g.territories.mapTo(hashSetOf()) { it.id }; val observers = g.observers.mapTo(hashSetOf()) { it.id }
    unique(g.points, "point number") { listOf(it.territoryId, it.observationYear, it.observerId, it.pointNumber) }
    g.points.forEach { p ->
        if (p.territoryId !in territories) throw BrokenBackupForeignKey("point ${p.id} territory missing")
        if (p.observerId !in observers) throw BrokenBackupForeignKey("point ${p.id} observer missing")
        domain(p.observationYear > 0 && p.pointNumber > 0, "invalid point number"); coordinate(p.latitude, -90.0, 90.0); coordinate(p.longitude, -180.0, 180.0)
        p.gpsLatitude?.let { coordinate(it, -90.0, 90.0) }; p.gpsLongitude?.let { coordinate(it, -180.0, 180.0) }; p.gpsAccuracyM?.let { domain(it.isFinite() && it >= 0, "invalid GPS accuracy") }
        p.initialGroupReleaseAt?.let { ordered(p.createdAt, it, "initial release") }; p.completedAt?.let { ordered(p.createdAt, it, "point completion") }
        domain(p.completedAt == null || p.beePresenceResult != null, "completed point has no presence result")
    }
    val points = g.points.mapTo(hashSetOf()) { it.id }; unique(g.bees, "bee mark") { listOf(it.observationPointId, it.markColor, it.markPosition) }
    g.bees.forEach { if (it.observationPointId !in points) throw BrokenBackupForeignKey("bee ${it.id} point missing"); domain(it.markColor.isNotBlank(), "blank mark color") }
    val beesByPoint = g.bees.groupBy { it.observationPointId }
    g.points.forEach { p -> val n = beesByPoint[p.id].orEmpty().size; domain(p.beePresenceResult != BeePresenceResult.NO_BEES_FOUND || (n == 0 && p.completedAt != null), "invalid NO_BEES_FOUND point"); domain(p.beePresenceResult != BeePresenceResult.BEES_FOUND || n > 0, "BEES_FOUND point has no bees"); domain(n == 0 || p.beePresenceResult == BeePresenceResult.BEES_FOUND, "point with bees lacks result") }
    val bees = g.bees.mapTo(hashSetOf()) { it.id }; unique(g.cycles, "flight sequence") { it.beeId to it.sequenceNumber }
    g.cycles.forEach { c ->
        if (c.beeId !in bees) throw BrokenBackupForeignKey("cycle ${c.id} bee missing")
        domain(c.sequenceNumber > 0, "invalid flight sequence"); c.returnTime?.let { ordered(c.departureTime, it, "flight return") }; c.azimuthDeg?.let { domain(it.isFinite() && it >= 0 && it < 360, "invalid azimuth") }; ordered(c.createdAt, c.updatedAt, "cycle")
        domain(!c.isInitialGroupLaunch || c.sequenceNumber == 1, "initial launch must be sequence 1"); domain(!c.isFirstDepartureCancellationEligible || (c.sequenceNumber == 1 && c.returnTime == null), "invalid first-departure correction provenance")
    }
    g.cycles.groupBy { it.beeId }.values.forEach { cycles -> val ordered = cycles.sortedBy { it.sequenceNumber }; domain(ordered.map { it.sequenceNumber } == (1..ordered.size).toList(), "non-contiguous flight sequence"); domain(ordered.count { it.returnTime == null } <= 1 && ordered.dropLast(1).none { it.returnTime == null }, "invalid open cycles") }
    val pointById = g.points.associateBy { it.id }; val beeById = g.bees.associateBy { it.id }
    g.cycles.filter { it.isInitialGroupLaunch }.forEach { c -> val p = pointById.getValue(beeById.getValue(c.beeId).observationPointId); domain(p.initialGroupReleaseAt == c.departureTime, "initial release timestamp mismatch") }
    domain(g.points.count { it.completedAt == null } <= 1, "more than one active point")
}

private fun territory(o: JsonObject) = TerritoryEntity(o.uuid("id"), o.string("code"), o.string("name"), o.string("region"), o.string("district"), o.instant("createdAt"), o.instant("updatedAt"))
private fun observer(o: JsonObject) = ObserverEntity(o.uuid("id"), o.string("code"), o.string("lastName"), o.string("firstName"), o.optionalString("middleName"), o.optionalString("contact"), o.instant("createdAt"), o.instant("updatedAt"))
private fun point(o: JsonObject) = ObservationPointEntity(o.uuid("id"), o.uuid("territoryId"), o.uuid("observerId"), o.int("observationYear"), o.int("pointNumber"), o.optionalEnum<BeePresenceResult>("beePresenceResult"), o.optionalString("code"), o.double("latitude"), o.double("longitude"), o.optionalDouble("gpsLatitude"), o.optionalDouble("gpsLongitude"), o.optionalDouble("gpsAccuracyM"), o.instant("createdAt"), o.optionalInstant("initialGroupReleaseAt"), o.optionalInstant("completedAt"))
private fun bee(o: JsonObject) = BeeEntity(o.uuid("id"), o.uuid("observationPointId"), o.string("markColor"), o.markPosition("markPosition"), o.instant("createdAt"))
private fun cycle(o: JsonObject) = FlightCycleEntity(o.uuid("id"), o.uuid("beeId"), o.int("sequenceNumber"), o.instant("departureTime"), o.optionalInstant("returnTime"), o.optionalDouble("azimuthDeg"), o.bool("azimuthCaptureConsumed"), o.bool("initialGroupLaunch"), o.bool("initialGroupLaunchCorrectionEligible"), o.instant("createdAt"), o.instant("updatedAt"))

private fun objectFrom(bytes: ByteArray, label: String): JsonObject = try { JSON.parseToJsonElement(decode(bytes)).jsonObject } catch (e: Exception) { throw MalformedBackup("invalid JSON in $label", e) }
private fun decode(bytes: ByteArray): String = try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() } catch (e: Exception) { throw MalformedBackup("invalid UTF-8", e) }
private fun rows(bytes: ByteArray, label: String) = decode(bytes).lineSequence().filter { it.isNotBlank() }.toList()
private fun JsonObject.field(name: String): JsonElement = this[name] ?: throw MalformedBackup("missing field $name")
private fun JsonObject.string(name: String) = field(name).let { if (it is JsonPrimitive && it.isString) it.content else throw MalformedBackup("$name must be string") }
private fun JsonObject.optionalString(name: String) = field(name).let { if (it is JsonNull) null else if (it is JsonPrimitive && it.isString) it.content else throw MalformedBackup("$name must be string or null") }
private fun JsonObject.int(name: String) = field(name).jsonPrimitive.intOrNull ?: throw MalformedBackup("$name must be integer")
private fun JsonObject.long(name: String) = field(name).jsonPrimitive.longOrNull ?: throw MalformedBackup("$name must be long")
private fun JsonObject.double(name: String) = field(name).jsonPrimitive.content.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw MalformedBackup("$name must be finite number")
private fun JsonObject.optionalDouble(name: String) = field(name).let { if (it is JsonNull) null else it.jsonPrimitive.content.toDoubleOrNull()?.takeIf { n -> n.isFinite() } ?: throw MalformedBackup("$name must be finite number or null") }
private fun JsonObject.bool(name: String) = field(name).jsonPrimitive.booleanOrNull ?: throw MalformedBackup("$name must be boolean")
private fun JsonObject.uuid(name: String) = parseUuid(string(name), name)
private fun JsonObject.optionalUuid(name: String) = field(name).let { if (it is JsonNull) null else parseUuid(string(name), name) }
private fun JsonObject.instant(name: String) = try { Instant.ofEpochMilli(long(name)) } catch (e: Exception) { throw MalformedBackup("invalid $name", e) }
private fun JsonObject.optionalInstant(name: String) = field(name).let { if (it is JsonNull) null else try { Instant.ofEpochMilli(it.jsonPrimitive.longOrNull ?: throw IllegalArgumentException()) } catch (e: Exception) { throw MalformedBackup("invalid $name", e) } }
private inline fun <reified T : Enum<T>> JsonObject.enum(name: String) = try { enumValueOf<T>(string(name)) } catch (e: Exception) { throw BackupDomainInvariantViolation("invalid enum $name") }
private inline fun <reified T : Enum<T>> JsonObject.optionalEnum(name: String) = optionalString(name)?.let { try { enumValueOf<T>(it) } catch (e: Exception) { throw BackupDomainInvariantViolation("invalid enum $name") } }

/**
 * Reads a mark position from a backup archive. The mapping is compatible with
 * archives written before the thorax/abdomen marking system, so `NONE` is read
 * as the thorax, `RIGHT_WING` as the abdomen, and `LEFT_WING` stays the legacy
 * value. An unknown token is a rejected archive rather than a silent guess.
 */
private fun JsonObject.markPosition(name: String): MarkPosition =
    MarkPosition.fromPersistedToken(string(name))
        ?: throw BackupDomainInvariantViolation("invalid enum $name")
private fun parseUuid(value: String, label: String) = try { UUID.fromString(value).also { require(it.toString() == value.lowercase()) } } catch (e: Exception) { throw MalformedBackup("invalid UUID $label", e) }

private fun TerritoryEntity.json() = obj("id" to j(id), "code" to j(code), "name" to j(name), "region" to j(region), "district" to j(district), "createdAt" to j(createdAt), "updatedAt" to j(updatedAt))
private fun ObserverEntity.json() = obj("id" to j(id), "code" to j(code), "lastName" to j(lastName), "firstName" to j(firstName), "middleName" to j(middleName), "contact" to j(contact), "createdAt" to j(createdAt), "updatedAt" to j(updatedAt))
private fun ObservationPointEntity.json() = obj("id" to j(id), "territoryId" to j(territoryId), "observerId" to j(observerId), "observationYear" to observationYear.toString(), "pointNumber" to pointNumber.toString(), "beePresenceResult" to j(beePresenceResult?.name), "code" to j(code), "latitude" to latitude.toString(), "longitude" to longitude.toString(), "gpsLatitude" to (gpsLatitude?.toString() ?: "null"), "gpsLongitude" to (gpsLongitude?.toString() ?: "null"), "gpsAccuracyM" to (gpsAccuracyM?.toString() ?: "null"), "createdAt" to j(createdAt), "initialGroupReleaseAt" to j(initialGroupReleaseAt), "completedAt" to j(completedAt))
private fun BeeEntity.json() = obj("id" to j(id), "observationPointId" to j(observationPointId), "markColor" to j(markColor), "markPosition" to j(markPosition.name), "createdAt" to j(createdAt))
private fun FlightCycleEntity.json() = obj("id" to j(id), "beeId" to j(beeId), "sequenceNumber" to sequenceNumber.toString(), "departureTime" to j(departureTime), "returnTime" to j(returnTime), "azimuthDeg" to (azimuthDeg?.toString() ?: "null"), "azimuthCaptureConsumed" to azimuthCaptureConsumed.toString(), "initialGroupLaunch" to isInitialGroupLaunch.toString(), "initialGroupLaunchCorrectionEligible" to isFirstDepartureCancellationEligible.toString(), "createdAt" to j(createdAt), "updatedAt" to j(updatedAt))
private fun obj(vararg fields: Pair<String, String>) = fields.joinToString(",", "{", "}") { j(it.first) + ":" + it.second }
private fun j(value: String) = JsonPrimitive(value).toString()
@JvmName("nullableStringJson")
private fun j(value: String?) = value?.let { JsonPrimitive(it).toString() } ?: "null"
private fun j(value: UUID) = j(value.toString())
@JvmName("nullableUuidJson")
private fun j(value: UUID?) = value?.let(::j) ?: "null"
private fun j(value: Instant?) = value?.toEpochMilli()?.toString() ?: "null"
private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun <T, K> unique(values: List<T>, label: String, key: (T) -> K) { val seen = hashSetOf<K>(); values.forEach { if (!seen.add(key(it))) throw DuplicateBackupIdentity("duplicate $label") } }
private fun domain(ok: Boolean, message: String) { if (!ok) throw BackupDomainInvariantViolation(message) }
private fun ordered(start: Instant, end: Instant, label: String) = domain(!end.isBefore(start), "$label timestamp order invalid")
private fun coordinate(value: Double, min: Double, max: Double) = domain(value.isFinite() && value in min..max, "coordinate out of range")
