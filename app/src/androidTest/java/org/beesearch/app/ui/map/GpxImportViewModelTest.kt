package org.beesearch.app.ui.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.gpx.GpxAdmissionRejection
import org.beesearch.app.data.gpx.GpxCapturedRequest
import org.beesearch.app.data.gpx.GpxFailureCategory
import org.beesearch.app.data.gpx.GpxFailureContext
import org.beesearch.app.data.gpx.GpxFailureReason
import org.beesearch.app.data.gpx.GpxImportGateway
import org.beesearch.app.data.gpx.GpxImportRequest
import org.beesearch.app.data.gpx.GpxOriginalStoreError
import org.beesearch.app.data.gpx.GpxParseResult
import org.beesearch.app.data.gpx.GpxPreparationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxImportViewModelTest {
    private val territory = UUID.randomUUID()
    private val observer = UUID.randomUUID()

    @Test
    fun pickerCancelIsNeutralAndClearsTheCapturedIntent() = runBlocking {
        val vm = viewModel(FakeGateway())

        vm.beginImport("Лесная поляна")
        val request = waitForPicker(vm)
        vm.onPickerLaunched(request.token)
        assertNull(vm.pickerRequest.value)

        vm.onPickerCancelled()

        assertTrue(vm.state.value is GpxImportUiState.Idle)
        assertNull(vm.pickerRequest.value)
        vm.reset()
    }

    @Test
    fun successfulImportShowsTheApprovedResult() = runBlocking {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)
        startImport(vm)

        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }

        val success = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Success }
        } as GpxImportUiState.Success
        assertEquals("поход.gpx", success.fileName)
        assertEquals("Лесная поляна", success.territoryName)
        assertEquals(listOf(false), gateway.confirmations)
        vm.reset()
    }

    @Test
    fun duplicateRequiresConfirmationAndThenCreatesAFreshCapture() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { captured, confirm ->
            if (confirm) ready(captured)
            else duplicate(captured)
        }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }

        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Duplicate } }
        vm.confirmDuplicate()

        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Success } }
        assertEquals(listOf(false, true), gateway.confirmations)
        assertEquals(2, gateway.captureCount)
        vm.reset()
    }

    @Test
    fun decliningTheDuplicateCreatesNothing() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { captured, _ -> duplicate(captured) }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Duplicate } }

        vm.declineDuplicate()

        assertTrue(vm.state.value is GpxImportUiState.DuplicateDeclined)
        assertEquals(listOf(false), gateway.confirmations)
        vm.reset()
    }

    @Test
    fun confirmedDuplicateIsNotRetargetedWhenTheTerritoryChanged() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { captured, _ -> duplicate(captured) }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Duplicate } }

        gateway.captureTerritory = UUID.randomUUID()
        vm.confirmDuplicate()

        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.ContextChanged } }
        assertEquals(listOf(false), gateway.confirmations)
        vm.reset()
    }

    @Test
    fun cancellationChecksDurableResultAndKeepsCommittedReady() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { _, _ -> awaitCancellation() }
        gateway.lookupBlock = { requestUuid -> readyFor(requestUuid) }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }

        vm.cancelImport()

        val success = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Success }
        } as GpxImportUiState.Success
        assertEquals("поход.gpx", success.fileName)
        vm.reset()
    }

    @Test
    fun cancellationWithoutCommittedReadyIsNeutral() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { _, _ -> awaitCancellation() }
        gateway.lookupBlock = { null }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }

        vm.cancelImport()

        assertTrue(
            withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Cancelled } }
                is GpxImportUiState.Cancelled,
        )
        vm.reset()
    }

    @Test
    fun parseFailureMapsToApprovedFailureReasons() = runBlocking {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)

        gateway.executeBlock = { captured, _ ->
            failed(captured, GpxPreparationResult.Kind.PARSE,
                parse = GpxParseResult.Rejected(
                    GpxFailureCategory.LIMIT_EXCEEDED,
                    GpxFailureContext(GpxFailureReason.G01),
                ))
        }
        startImport(vm)
        vm.onFilePicked("limit.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        val limit = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Failure }
        } as GpxImportUiState.Failure
        assertEquals(GpxImportFailure.LIMIT, limit.reason)

        vm.reset()
        gateway.executeBlock = { captured, _ ->
            failed(captured, GpxPreparationResult.Kind.PARSE,
                parse = GpxParseResult.Rejected(
                    GpxFailureCategory.MALFORMED_XML,
                    GpxFailureContext(GpxFailureReason.XML_REJECTED),
                ))
        }
        startImport(vm)
        vm.onFilePicked("broken.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        val invalid = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Failure }
        } as GpxImportUiState.Failure
        assertEquals(GpxImportFailure.INVALID_GPX, invalid.reason)
        vm.reset()
    }

    @Test
    fun originalStoreFailureMapsNoSpaceAndRead() = runBlocking {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)

        gateway.executeBlock = { captured, _ ->
            failed(captured, GpxPreparationResult.Kind.ORIGINAL,
                original = GpxOriginalStoreError.STORAGE_EXHAUSTED)
        }
        startImport(vm)
        vm.onFilePicked("space.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        val noSpace = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Failure }
        } as GpxImportUiState.Failure
        assertEquals(GpxImportFailure.NO_SPACE, noSpace.reason)

        vm.reset()
        gateway.executeBlock = { captured, _ ->
            failed(captured, GpxPreparationResult.Kind.ORIGINAL,
                original = GpxOriginalStoreError.IO_FAILURE)
        }
        startImport(vm)
        vm.onFilePicked("io.gpx") { ByteArrayInputStream(byteArrayOf(1)) }
        val read = withTimeout(5_000) {
            vm.state.first { it is GpxImportUiState.Failure }
        } as GpxImportUiState.Failure
        assertEquals(GpxImportFailure.READ, read.reason)
        vm.reset()
    }

    @Test
    fun unknownResultCheckCanBeRetriedWithoutCreatingANewImport() = runBlocking {
        val gateway = FakeGateway()
        gateway.executeBlock = { captured, _ ->
            failed(captured, GpxPreparationResult.Kind.PERSISTENCE)
        }
        gateway.lookupBlock = { throw IllegalStateException("reconciliation unavailable") }
        val vm = viewModel(gateway)
        startImport(vm)
        vm.onFilePicked("поход.gpx") { ByteArrayInputStream(byteArrayOf(1)) }

        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.CheckFailed } }
        gateway.lookupBlock = { requestUuid -> readyFor(requestUuid) }
        vm.retryCheck()

        withTimeout(5_000) { vm.state.first { it is GpxImportUiState.Success } }
        assertEquals(1, gateway.captureCount)
        vm.reset()
    }

    private fun viewModel(gateway: FakeGateway): GpxImportViewModel =
        GpxImportViewModel(gateway)

    private suspend fun startImport(vm: GpxImportViewModel) {
        vm.beginImport("Лесная поляна")
        val request = waitForPicker(vm)
        vm.onPickerLaunched(request.token)
    }

    private suspend fun waitForPicker(vm: GpxImportViewModel): GpxPickerRequest =
        withTimeout(5_000) { vm.pickerRequest.first { it != null }!! }

    private inner class FakeGateway : GpxImportGateway {
        var captureTerritory: UUID = territory
        var captureObserver: UUID = observer
        var captureCount = 0
        val confirmations = mutableListOf<Boolean>()
        var executeBlock: suspend (GpxCapturedRequest, Boolean) -> GpxPreparationResult =
            { captured, _ -> ready(captured) }
        var lookupBlock: suspend (UUID) -> GpxPreparationResult.Ready? = { null }

        override suspend fun capture(displayName: String?): GpxCapturedRequest {
            captureCount++
            return GpxCapturedRequest(
                GpxImportRequest(
                    UUID.randomUUID(), captureTerritory, captureObserver, displayName, 0L,
                ),
                Any(),
                0L,
            )
        }

        override suspend fun execute(
            captured: GpxCapturedRequest,
            open: () -> InputStream,
            confirmRepeatedOriginal: Boolean,
        ): GpxPreparationResult {
            confirmations += confirmRepeatedOriginal
            return executeBlock(captured, confirmRepeatedOriginal)
        }

        override suspend fun lookup(requestUuid: UUID): GpxPreparationResult.Ready? =
            lookupBlock(requestUuid)
    }
}

private fun ready(captured: GpxCapturedRequest) =
    readyFor(captured.request.requestUuid)

private fun readyFor(requestUuid: UUID) = GpxPreparationResult.Ready(
    requestUuid, UUID.randomUUID(), UUID.randomUUID(),
)

private fun duplicate(captured: GpxCapturedRequest) = GpxPreparationResult.Failed(
    captured.request.requestUuid,
    GpxPreparationResult.Kind.ADMISSION,
    admission = GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED,
)

private fun failed(
    captured: GpxCapturedRequest,
    kind: GpxPreparationResult.Kind,
    parse: GpxParseResult.Rejected? = null,
    original: GpxOriginalStoreError? = null,
) = GpxPreparationResult.Failed(
    captured.request.requestUuid,
    kind,
    parse = parse,
    original = original,
)
