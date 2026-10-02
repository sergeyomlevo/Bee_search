package org.beesearch.app.data.objectexport

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.repository.ObserverRepository
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import org.beesearch.app.domain.repository.TerritoryRepository

internal fun interface PhysicalObjectCollectionExportSource {
    suspend fun load(territoryId: UUID, type: PhysicalObjectType): PhysicalObjectCollectionExportGraph
}

/** Read-only source limited to one Territory and one concrete object type. */
internal class RepositoryPhysicalObjectCollectionExportSource(
    private val objects: PhysicalObjectRepository,
    private val territories: TerritoryRepository,
    private val observers: ObserverRepository,
) : PhysicalObjectCollectionExportSource {
    override suspend fun load(
        territoryId: UUID,
        type: PhysicalObjectType,
    ): PhysicalObjectCollectionExportGraph {
        PhysicalObjectExportValidator.validateSupportedType(type)
        val territory = territories.getTerritory(territoryId)
            ?: throw PhysicalObjectExportSourceMissing("Территория не найдена")
        val values = objects.listForTerritory(territoryId)
        val observerIds = when (type) {
            PhysicalObjectType.HOLLOW -> values.hollows.mapNotNull { it.creatorObserverId }
            PhysicalObjectType.LOG_HIVE -> values.logHives.mapNotNull { it.creatorObserverId }
            PhysicalObjectType.APIARY -> emptyList()
        }.distinct()
        val observerSnapshots = observerIds.associateWith { id ->
            observers.getObserver(id)?.toExportSnapshot()
                ?: throw PhysicalObjectExportSourceMissing("Наблюдатель объекта не найден")
        }
        val territorySnapshot = territory.toExportSnapshot()
        val graphs = when (type) {
            PhysicalObjectType.HOLLOW -> values.hollows.map { value ->
                value.toExportGraph(territorySnapshot, value.creatorObserverId?.let(observerSnapshots::get))
            }
            PhysicalObjectType.LOG_HIVE -> values.logHives.map { value ->
                value.toExportGraph(territorySnapshot, value.creatorObserverId?.let(observerSnapshots::get))
            }
            PhysicalObjectType.APIARY -> throw UnsupportedPhysicalObjectExportType(
                "Пасеки пока нельзя экспортировать",
            )
        }
        return PhysicalObjectCollectionExportGraph(territorySnapshot, type, graphs)
    }
}

internal class PhysicalObjectCollectionExportService(
    private val source: PhysicalObjectCollectionExportSource,
    private val mediaStore: PhysicalObjectMediaFileStore,
) {
    suspend fun export(territoryId: UUID, type: PhysicalObjectType, output: OutputStream) {
        val graph = source.load(territoryId, type)
        if (graph.objects.isEmpty()) throw EmptyPhysicalObjectCollectionExport(type)
        withContext(Dispatchers.IO) {
            val mediaBytes = linkedMapOf<UUID, ByteArray>()
            graph.objects.forEach { value ->
                value.media.forEach { media ->
                    val file = try {
                        mediaStore.resolve(media.relativePath)
                    } catch (error: Exception) {
                        throw PhysicalObjectExportSourceMissing("Некорректный путь медиа объекта")
                    }
                    if (!file.isFile) throw PhysicalObjectExportSourceMissing("Файл медиа объекта отсутствует")
                    if (file.length() != media.byteSize) {
                        throw PhysicalObjectExportIntegrityError("Размер медиа не совпадает с метаданными")
                    }
                    if (mediaBytes.put(media.id, file.readBytes()) != null) {
                        throw InvalidPhysicalObjectExport("duplicate media id")
                    }
                }
            }
            PhysicalObjectCollectionExportCodec.encode(graph, mediaBytes, output)
        }
    }
}

internal fun interface PhysicalObjectCollectionDocumentExporter {
    suspend fun export(territoryId: UUID, type: PhysicalObjectType, destination: Uri)
}

/** Stages and re-reads the complete collection before touching the SAF destination. */
internal class SafPhysicalObjectCollectionDocumentExporter(
    private val service: PhysicalObjectCollectionExportService,
    private val cacheDirectory: File,
    private val openDestination: (Uri) -> OutputStream?,
) : PhysicalObjectCollectionDocumentExporter {
    constructor(
        service: PhysicalObjectCollectionExportService,
        contentResolver: ContentResolver,
        cacheDirectory: File,
    ) : this(service, cacheDirectory, { uri -> contentResolver.openOutputStream(uri, "w") })

    override suspend fun export(territoryId: UUID, type: PhysicalObjectType, destination: Uri) {
        withContext(Dispatchers.IO) {
            val temporaryArchive = File.createTempFile("bee-search-object-collection-", ".zip", cacheDirectory)
            try {
                temporaryArchive.outputStream().buffered().use { service.export(territoryId, type, it) }
                temporaryArchive.inputStream().buffered().use(PhysicalObjectCollectionExportCodec::decode)
                val output = openDestination(destination)
                    ?: throw PhysicalObjectExportSourceMissing("Выбранный файл недоступен для записи")
                output.use { destinationStream ->
                    temporaryArchive.inputStream().buffered().use { source -> source.copyTo(destinationStream) }
                }
            } finally {
                temporaryArchive.delete()
            }
        }
    }
}
