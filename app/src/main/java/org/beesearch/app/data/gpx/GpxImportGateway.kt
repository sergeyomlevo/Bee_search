package org.beesearch.app.data.gpx

import java.io.InputStream
import java.util.UUID

/**
 * Narrow UI-facing seam over the qualified import workflow.
 *
 * The UI never re-implements capture, cancellation, duplicate or READY correlation; it only drives
 * this gateway. The implementation remains [GpxImportWorkflow].
 */
internal interface GpxImportGateway {
    suspend fun capture(displayName: String? = null): GpxCapturedRequest

    suspend fun execute(
        captured: GpxCapturedRequest,
        open: () -> InputStream,
        confirmRepeatedOriginal: Boolean = false,
    ): GpxPreparationResult

    suspend fun lookup(requestUuid: UUID): GpxPreparationResult.Ready?
}
