package org.beesearch.app.data.pointexport

import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.repository.ObservationRepository

internal fun interface ObservationPointExportSource {
    suspend fun load(pointId: UUID): ObservationPointDetail?
}

internal class RepositoryObservationPointExportSource(
    private val repository: ObservationRepository,
) : ObservationPointExportSource {
    override suspend fun load(pointId: UUID): ObservationPointDetail? =
        repository.getObservationPointDetail(pointId)
}

/** Builds one read-only snapshot without touching backup or portable settings state. */
internal class ObservationPointExportService(
    private val source: ObservationPointExportSource,
    private val attachmentStore: ObservationAttachmentFileStore,
) {
    suspend fun export(pointId: UUID, output: OutputStream) {
        val detail = source.load(pointId)
            ?: throw ObservationPointExportSourceMissing("Точка наблюдения не найдена")
        withContext(Dispatchers.IO) {
            val attachmentBytes = detail.attachments.associate { attachment ->
                val file = try {
                    attachmentStore.resolve(attachment.relativePath)
                } catch (error: Exception) {
                    throw ObservationPointExportSourceMissing("Некорректный путь фотографии")
                }
                if (!file.isFile) throw ObservationPointExportSourceMissing("Файл фотографии отсутствует")
                if (file.length() != attachment.byteSize) {
                    throw ObservationPointExportIntegrityError("Размер фотографии не совпадает с метаданными")
                }
                attachment.id to file.readBytes()
            }
            ObservationPointExportCodec.encode(
                graph = ObservationPointExportGraph(
                    point = detail.point,
                    territory = detail.territory,
                    observer = detail.observer,
                    weather = detail.weather,
                    beeHistories = detail.beeHistories,
                    attachments = detail.attachments,
                ),
                attachmentBytes = attachmentBytes,
                output = output,
            )
        }
    }
}

internal fun interface ObservationPointDocumentExporter {
    suspend fun export(pointId: UUID, destination: Uri)
}

internal class SafObservationPointDocumentExporter(
    private val service: ObservationPointExportService,
    private val openDestination: (Uri) -> OutputStream?,
) : ObservationPointDocumentExporter {
    constructor(service: ObservationPointExportService, contentResolver: ContentResolver) : this(
        service = service,
        openDestination = { uri -> contentResolver.openOutputStream(uri, "w") },
    )

    override suspend fun export(pointId: UUID, destination: Uri) {
        val output = openDestination(destination)
            ?: throw ObservationPointExportSourceMissing("Выбранный файл недоступен для записи")
        output.use { service.export(pointId, it) }
    }
}
