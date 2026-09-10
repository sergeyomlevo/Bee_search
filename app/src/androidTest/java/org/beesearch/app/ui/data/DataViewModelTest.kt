package org.beesearch.app.ui.data

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.repository.ObservationDataMaintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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

    private class FakeObservationData : ObservationDataMaintenance {
        var clearCalls = 0

        override suspend fun getObservationDataCounts() = ObservationDataCounts(1, 1, 1)

        override suspend fun clearObservationData(): ObservationDataCounts {
            clearCalls += 1
            return ObservationDataCounts(1, 1, 1)
        }
    }
}
