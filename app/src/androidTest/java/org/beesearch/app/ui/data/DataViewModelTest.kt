package org.beesearch.app.ui.data

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.domain.model.CompletedObservationPointSummary
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

class DataViewModelTest {
    @Test
    fun successfulExportPublishesSuccessWithoutCallingCleanup() = runBlocking {
        val data = FakeObservationData()
        val viewModel = DataViewModel(
            backupExporter = BackupDocumentExporter { UUID.randomUUID() },
            observationData = data,
        )

        viewModel.export(Uri.EMPTY)
        val state = withTimeout(5_000) { viewModel.state.first { it.status != null } }

        assertFalse(state.status!!.isError)
        assertEquals(0, data.clearCalls)
    }

    @Test
    fun exportFailurePublishesPublicErrorWithoutCallingCleanup() = runBlocking {
        val data = FakeObservationData()
        val viewModel = DataViewModel(
            backupExporter = BackupDocumentExporter { throw IOException("internal path") },
            observationData = data,
        )

        viewModel.export(Uri.EMPTY)
        val state = withTimeout(5_000) { viewModel.state.first { it.status != null } }

        assertTrue(state.status!!.isError)
        assertFalse(state.status.message.contains("internal path"))
        assertEquals(0, data.clearCalls)
    }

    @Test
    fun deletingOnePointUpdatesTheListAndCounts() = runBlocking {
        val data = FakeObservationData()
        val deletedId = data.completedPoints.first().id
        val viewModel = DataViewModel(
            backupExporter = BackupDocumentExporter { UUID.randomUUID() },
            observationData = data,
        )
        viewModel.refreshCounts()
        withTimeout(5_000) { viewModel.state.first { it.counts != null } }

        viewModel.deleteCompletedObservationPoint(deletedId)
        val state = withTimeout(5_000) {
            viewModel.state.first { it.status?.message == "Точка наблюдения удалена." }
        }

        assertEquals(listOf(data.completedPoints.last()), state.completedPoints)
        assertEquals(ObservationDataCounts(1, 1, 1), state.counts)
        assertEquals(listOf(deletedId), data.deletedPointIds)
    }

    private class FakeObservationData : ObservationDataMaintenance {
        var clearCalls = 0
        val completedPoints = listOf(pointSummary(1), pointSummary(2))
        val deletedPointIds = mutableListOf<UUID>()

        override suspend fun getObservationDataCounts() = ObservationDataCounts(2, 2, 2)

        override suspend fun getCompletedObservationPoints() = completedPoints

        override suspend fun deleteCompletedObservationPoint(pointId: UUID): ObservationDataCounts {
            deletedPointIds += pointId
            return ObservationDataCounts(1, 1, 1)
        }

        override suspend fun clearObservationData(): ObservationDataCounts {
            clearCalls += 1
            return ObservationDataCounts(1, 1, 1)
        }

        companion object {
            private fun pointSummary(number: Int) = CompletedObservationPointSummary(
                id = UUID.randomUUID(),
                createdAt = Instant.parse("2026-09-0${number}T08:00:00Z"),
                observationYear = 2026,
                pointNumber = number,
                territoryCode = "T01",
                territoryName = "Территория",
                beeCount = 1,
            )
        }
    }
}
