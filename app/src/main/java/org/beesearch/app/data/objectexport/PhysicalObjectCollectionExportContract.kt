package org.beesearch.app.data.objectexport

import java.util.UUID
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.domain.model.PhysicalObjectType

/** Portable package of one concrete Physical Object type in one Territory. */
internal object PhysicalObjectCollectionExportContract {
    const val PROFILE = "PHYSICAL_OBJECT_COLLECTION"
    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val TERRITORY_ENTRY = "territory.json"
    const val OBSERVERS_ENTRY = "observers.json"
    const val MAX_OBJECTS = 256
    const val MAX_ENTRIES = 1_024
    const val MAX_METADATA_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_METADATA_BYTES = 128L * 1024 * 1024

    fun objectEntry(id: UUID): String = "objects/$id.json"
    fun mediaEntry(objectId: UUID, mediaId: UUID): String = "media/$objectId/$mediaId"
}

internal data class PhysicalObjectCollectionExportGraph(
    val territory: TerritoryExportSnapshot,
    val type: PhysicalObjectType,
    val objects: List<PhysicalObjectExportGraph>,
)

internal data class DecodedPhysicalObjectCollectionExport(
    val graph: PhysicalObjectCollectionExportGraph,
    val mediaBytes: Map<UUID, ArchivePayload>,
    private val archive: java.io.Closeable,
) : java.io.Closeable {
    override fun close() = archive.close()
}

internal class EmptyPhysicalObjectCollectionExport(type: PhysicalObjectType) :
    PhysicalObjectExportException(
        when (type) {
            PhysicalObjectType.HOLLOW -> "Нет дупел для экспорта"
            PhysicalObjectType.LOG_HIVE -> "Нет колод для экспорта"
            PhysicalObjectType.APIARY -> "Пасеки пока нельзя экспортировать"
        },
    )
