package org.beesearch.app.data.media

import java.util.UUID
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.beesearch.app.domain.repository.ObservationRepository

/** Keeps app-owned photo bytes aligned with selective and full research-data deletion. */
internal class FileAwareObservationDataMaintenance(
    private val repository: ObservationRepository,
    private val fileStore: ObservationAttachmentFileStore,
) : ObservationDataMaintenance {
    override suspend fun getObservationDataCounts(): ObservationDataCounts =
        repository.getObservationDataCounts()

    override suspend fun getCompletedObservationPoints(): List<CompletedObservationPointSummary> =
        repository.getCompletedObservationPoints()

    override suspend fun deleteCompletedObservationPoint(pointId: UUID): ObservationDataCounts {
        val paths = repository.listObservationPointAttachments(pointId).map { it.relativePath }
        val deletion = fileStore.stageDeletion(paths)
        val result = try {
            repository.deleteCompletedObservationPoint(pointId)
        } catch (error: Throwable) {
            deletion.rollback()
            throw error
        }
        deletion.commit()
        return result
    }

    override suspend fun clearObservationData(): ObservationDataCounts {
        val paths = repository.listAllObservationPointAttachments().map { it.relativePath }
        val deletion = fileStore.stageDeletion(paths)
        val result = try {
            repository.clearObservationData()
        } catch (error: Throwable) {
            deletion.rollback()
            throw error
        }
        deletion.commit()
        return result
    }
}
