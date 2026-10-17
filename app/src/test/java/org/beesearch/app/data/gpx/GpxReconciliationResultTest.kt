package org.beesearch.app.data.gpx

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxReconciliationResultTest {
    private val request = UUID(0, 1)

    @Test
    fun emptyAndSuccessfulResultsAreCompleted() {
        assertTrue(GpxReconciliationResult().completed)
        assertTrue(GpxReconciliationResult(listOf(GpxPreparationResult.Ready(request, UUID(0, 2), UUID(0, 3)))).completed)
        assertTrue(GpxReconciliationResult(listOf(GpxPreparationResult.Aborted(request))).completed)
    }

    @Test
    fun preparedAndFailedResultsRemainIncomplete() {
        assertFalse(GpxReconciliationResult(listOf(GpxPreparationResult.Prepared(request, UUID(0, 2), UUID(0, 3)))).completed)
        assertFalse(GpxReconciliationResult(listOf(GpxPreparationResult.Failed(request, GpxPreparationResult.Kind.PERSISTENCE))).completed)
        assertFalse(GpxReconciliationResult(failure = IllegalStateException("temporary" )).completed)
    }

    @Test
    fun cleanupDebtDoesNotHideCommittedReady() {
        assertTrue(GpxReconciliationResult(listOf(
            GpxPreparationResult.Ready(request, UUID(0, 2), UUID(0, 3), cleanupPending = true),
        )).completed)
    }
}
