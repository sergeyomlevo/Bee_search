package org.beesearch.app.data.objectexport

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.ObserverRepository
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import org.beesearch.app.domain.repository.TerritoryRepository

internal fun interface PhysicalObjectExportSource {
    suspend fun load(objectId: UUID): PhysicalObjectExportGraph?
}

/**
 * The read-only source of one object package.
 *
 * The object, its subtype properties and its media come from the object repository, which has no read
 * path to Bees, ObservationPoints or their attachments at all - so "object-owned data only" is a
 * property of the shape of the read, not a filter that could be forgotten. Territory and creator
 * Observer are read separately and only for the minimal labelling snapshot.
 *
 * Apiary fails closed: the type exists in storage but has no creation, card or media lifecycle yet,
 * so a package for it would describe a state no screen can produce or explain.
 */
internal class RepositoryPhysicalObjectExportSource(
    private val objects: PhysicalObjectRepository,
    private val territories: TerritoryRepository,
    private val observers: ObserverRepository,
) : PhysicalObjectExportSource {
    override suspend fun load(objectId: UUID): PhysicalObjectExportGraph? {
        if (objects.getApiary(objectId) != null) {
            throw UnsupportedPhysicalObjectExportType("Пасеку пока нельзя экспортировать")
        }
        val hollow = objects.getHollow(objectId)
        val logHive = if (hollow == null) objects.getLogHive(objectId) else null
        val territoryId = hollow?.territoryId ?: logHive?.territoryId ?: return null
        val creatorObserverId = hollow?.creatorObserverId ?: logHive?.creatorObserverId
        val territory = territories.getTerritory(territoryId)
            ?: throw PhysicalObjectExportSourceMissing("Территория объекта не найдена")
        val observer = creatorObserverId?.let { id ->
            observers.getObserver(id)
                ?: throw PhysicalObjectExportSourceMissing("Наблюдатель объекта не найден")
        }
        return when {
            hollow != null -> hollow.toExportGraph(territory.toExportSnapshot(), observer?.toExportSnapshot())
            logHive != null -> logHive.toExportGraph(territory.toExportSnapshot(), observer?.toExportSnapshot())
            else -> null
        }
    }
}

/**
 * Only the identifying fields of the Territory become part of the package; region, district and
 * timestamps are not labelling information for an object and are not invented when absent.
 */
internal fun Territory.toExportSnapshot() = TerritoryExportSnapshot(id = id, code = code, name = name)

/** Only the human-identifying fields of the creator become part of the package. */
internal fun Observer.toExportSnapshot() = ObserverExportSnapshot(
    id = id,
    code = code,
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
)

internal fun Hollow.toExportGraph(territory: TerritoryExportSnapshot, observer: ObserverExportSnapshot?) = PhysicalObjectExportGraph(
    id = id,
    type = PhysicalObjectType.HOLLOW,
    territoryId = territoryId,
    sequenceNumber = sequenceNumber,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    fixationDate = fixationDate,
    creatorObserverId = creatorObserverId,
    name = name,
    properties = properties?.let(PhysicalObjectExportProperties::Hollow),
    media = media,
    territory = territory,
    observer = observer,
)

internal fun LogHive.toExportGraph(territory: TerritoryExportSnapshot, observer: ObserverExportSnapshot?) = PhysicalObjectExportGraph(
    id = id,
    type = PhysicalObjectType.LOG_HIVE,
    territoryId = territoryId,
    sequenceNumber = sequenceNumber,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    fixationDate = fixationDate,
    creatorObserverId = creatorObserverId,
    name = name,
    properties = properties?.let(PhysicalObjectExportProperties::LogHive),
    media = media,
    territory = territory,
    observer = observer,
)

/**
 * Builds one read-only snapshot and writes it as a `SINGLE_PHYSICAL_OBJECT` v1 package.
 *
 * Export never mutates anything: it reads the object, verifies its app-owned bytes against the
 * persisted size and SHA-256, and writes a new archive. A stored media row whose file is missing or
 * whose bytes disagree with the metadata refuses the export, so a package that looks successful but
 * contains damaged media cannot be produced.
 */
internal class PhysicalObjectExportService(
    private val source: PhysicalObjectExportSource,
    private val mediaStore: PhysicalObjectMediaFileStore,
) {
    suspend fun export(objectId: UUID, output: OutputStream) {
        val graph = source.load(objectId)
            ?: throw PhysicalObjectExportSourceMissing("Объект не найден")
        withContext(Dispatchers.IO) {
            val mediaBytes = graph.media.associate { media ->
                val file = try {
                    mediaStore.resolve(media.relativePath)
                } catch (error: Exception) {
                    throw PhysicalObjectExportSourceMissing("Некорректный путь медиа объекта")
                }
                if (!file.isFile) throw PhysicalObjectExportSourceMissing("Файл медиа объекта отсутствует")
                if (file.length() != media.byteSize) {
                    throw PhysicalObjectExportIntegrityError("Размер медиа не совпадает с метаданными")
                }
                media.id to file.readBytes()
            }
            PhysicalObjectExportCodec.encode(graph, mediaBytes, output)
        }
    }
}

internal fun interface PhysicalObjectDocumentExporter {
    suspend fun export(objectId: UUID, destination: Uri)
}

/**
 * Writes one object package into the document the user picked.
 *
 * The whole archive is built and verified in the app cache first, and only a completely assembled
 * package is copied into the destination. A missing or damaged media file therefore fails before the
 * destination is touched, instead of leaving a truncated archive that looks like a finished export.
 *
 * What this does not guarantee: the Storage Access Framework creates the destination document when
 * the user confirms the file name, so a failure before the copy can leave an empty document behind.
 * The app deliberately does not delete it - it is the file the user chose, not app-owned storage.
 * The guarantee is "no partial or corrupt package is written", not "no empty document is left".
 */
internal class SafPhysicalObjectDocumentExporter(
    private val service: PhysicalObjectExportService,
    private val cacheDirectory: File,
    private val openDestination: (Uri) -> OutputStream?,
) : PhysicalObjectDocumentExporter {
    constructor(
        service: PhysicalObjectExportService,
        contentResolver: ContentResolver,
        cacheDirectory: File,
    ) : this(
        service = service,
        cacheDirectory = cacheDirectory,
        openDestination = { uri -> contentResolver.openOutputStream(uri, "w") },
    )

    override suspend fun export(objectId: UUID, destination: Uri) {
        withContext(Dispatchers.IO) {
            val temporaryArchive = File.createTempFile("bee-search-object-", ".zip", cacheDirectory)
            try {
                temporaryArchive.outputStream().buffered().use { service.export(objectId, it) }
                val output = openDestination(destination)
                    ?: throw PhysicalObjectExportSourceMissing("Выбранный файл недоступен для записи")
                output.use { destinationStream ->
                    temporaryArchive.inputStream().buffered().use { source ->
                        source.copyTo(destinationStream)
                    }
                }
            } finally {
                temporaryArchive.delete()
            }
        }
    }
}
