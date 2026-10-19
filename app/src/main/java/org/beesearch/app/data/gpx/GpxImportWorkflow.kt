package org.beesearch.app.data.gpx

import java.io.InputStream
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.beesearch.app.data.local.settings.SettingsSelectionGuard
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.repository.SettingsRepository

/** One explicit captured intent; never recreate this handle merely because a caller retries. */
internal class GpxCapturedRequest internal constructor(
    val request: GpxImportRequest,
    internal val issuer: Any,
    internal val selectionRevision: Long,
) {
    internal val pending = AtomicBoolean(true)
}

/** Application request lifecycle only. The accepted import state machine stays in Coordinator. */
internal class GpxImportWorkflow(
    createRuntime: () -> GpxRuntime,
    private val settings: SettingsRepository,
    private val selection: SettingsSelectionGuard,
    private val clock: Clock = Clock.systemUTC(),
    private val newRequestId: () -> UUID = UUID::randomUUID,
) : GpxImportGateway {
    private val runtime by lazy(createRuntime)
    constructor(runtime: GpxRuntime, settings: SettingsRepository, selection: SettingsSelectionGuard,
                clock: Clock = Clock.systemUTC(), newRequestId: () -> UUID = UUID::randomUUID) :
        this({ runtime }, settings, selection, clock, newRequestId)

    private val issuer = Any()

    /** User import UI is authorized by the approved mockup v1 (OWNER 2026-10-17). No override switch. */
    val userImportAvailable: Boolean = true

    /** Call once for a new explicit intent, before source selection; retain the returned handle. */
    override suspend fun capture(displayName: String?): GpxCapturedRequest = selection.withSelection {
        val context = settings.getSettings()
        val territory = context.currentTerritoryId ?: throw TerritoryRequiredException()
        GpxCapturedRequest(GpxImportRequest(newRequestId(), territory,
            context.currentObserverId, displayName, clock.millis()), issuer, selection.revision)
    }

    /** Read-only correlation after lost delivery/process restart; it never authorizes a new import. */
    override suspend fun lookup(requestUuid: UUID): GpxPreparationResult.Ready? =
        runtime.withImportCoordinator { it.lookup(requestUuid) }

    override suspend fun execute(captured: GpxCapturedRequest, open: () -> InputStream,
                                 confirmRepeatedOriginal: Boolean, displayName: String?): GpxPreparationResult {
        // SAF metadata arrives after capture. Bind only the name; keep the same one-shot capability.
        val request = captured.request.copy(
            displayName = displayName?.takeIf(String::isNotBlank) ?: captured.request.displayName,
        )
        if (captured.issuer !== issuer) return GpxPreparationResult.Failed(request.requestUuid,
            GpxPreparationResult.Kind.REQUEST)
        return runtime.withImportCoordinator { coordinator ->
            // Durable completion wins over consumed intent, changed settings or lost response.
            if (coordinator.lookup(request.requestUuid) != null) {
                return@withImportCoordinator coordinator.complete(request,
                    isCapturedRequestCurrent = { true }) // Coordinator rechecks captured identity/context.
            }
            val authorized = captured.pending.getAndSet(false)
            val currentAtStart = selection.withSelection { matches(request, settings.getSettings()) }
            val current = { currentAtStart && captured.selectionRevision == selection.revision }
            val prepared = coordinator.prepare(request, open,
                authorizeNewRequest = { authorized }, isCapturedRequestCurrent = current)
            if (prepared is GpxPreparationResult.Prepared) try {
                selection.withSelection {
                    // Selection commits either invalidate first, or linearize after committed READY.
                    val currentAtPublication = matches(request, settings.getSettings())
                    coordinator.complete(request, confirmRepeatedOriginal,
                        isCapturedRequestCurrent = { currentAtPublication && current() })
                }
            } catch (cancelled: CancellationException) {
                // Includes cancellation while waiting for selection, before complete() owns cleanup.
                try {
                    val settled = withContext(NonCancellable) { coordinator.abort(request.requestUuid) }
                    check(settled !is GpxPreparationResult.Failed) { "GPX_WORKFLOW_ABORT_INCOMPLETE" }
                } catch (cleanup: Exception) {
                    if (cleanup !== cancelled) cancelled.addSuppressed(cleanup)
                }
                throw cancelled
            } catch (_: Exception) {
                val settled = try {
                    withContext(NonCancellable) { coordinator.abort(request.requestUuid) }
                } catch (_: Exception) { null }
                // abort() itself looks up committed READY first; never infer rollback from delivery.
                settled as? GpxPreparationResult.Ready ?: GpxPreparationResult.Failed(
                    request.requestUuid, GpxPreparationResult.Kind.PERSISTENCE,
                    cleanupPending = settled == null || settled is GpxPreparationResult.Failed)
            } else prepared
        }
    }

    private fun matches(request: GpxImportRequest, context: AppSettings) =
        request.territoryId == context.currentTerritoryId && request.observerId == context.currentObserverId
}
