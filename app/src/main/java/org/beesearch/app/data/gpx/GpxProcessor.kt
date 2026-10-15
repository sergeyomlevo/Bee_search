package org.beesearch.app.data.gpx

import org.beesearch.app.domain.gpx.GpxProfileFailure
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException

/**
 * Composes the closed two-pass Android/profile chain. The caller owns the repeatable
 * opener; the closed input chain owns each stream it opens. Cancellation checkpoints
 * may be supplied by the caller's coroutine context. No URI, coordinator or storage.
 *
 * The internal completed-read seam permits JVM proof of result mapping independently
 * of Android provider proof. Production defaults to the sole qualified XML chain.
 */
internal class GpxProcessor(
    private val readProfile: (() -> InputStream, () -> Unit) -> GpxProfileRead = AndroidGpxProfileReader::parse,
) {
    fun process(
        open: () -> InputStream,
        expectedOriginalIdentity: GpxOriginalIdentity? = null,
        checkCancellation: () -> Unit = {},
    ): GpxParseResult = try {
        checkCancellation()
        val complete = readProfile(open, checkCancellation)
        checkCancellation()
        if (expectedOriginalIdentity != null && complete.verification.identity != expectedOriginalIdentity) {
            throw GpxXmlFailure("INPUT_IO_ERROR", "SOURCE_CHANGED")
        }
        checkCancellation()
        GpxParseResult.Accepted(complete.profile.draft, complete.verification.identity, complete.profile.warnings)
    } catch (cancelled: CancellationException) {
        // Preserve object identity AND the corrective chain's suppressed close failure.
        throw cancelled
    } catch (failure: GpxXmlFailure) {
        reject(failure.category, failure.reason, xml = true)
    } catch (failure: GpxProfileFailure) {
        reject(failure.category, failure.reason, xml = false, address = failure.address)
    } catch (_: IOException) {
        GpxParseResult.Rejected(GpxFailureCategory.INPUT_IO_ERROR, GpxFailureContext(GpxFailureReason.SOURCE_IO))
    }

    private fun reject(
        category: String,
        reason: String,
        xml: Boolean,
        address: org.beesearch.app.domain.gpx.GpxProfileAddress? = null,
    ): GpxParseResult.Rejected {
        val typed = GpxFailureCategory.valueOf(category)
        val detail = when {
            typed == GpxFailureCategory.LIMIT_EXCEEDED && reason == "raw input exceeds byte limit" -> GpxFailureReason.G01
            else -> GpxFailureReason.entries.firstOrNull { it.code == reason } ?: when (typed) {
                GpxFailureCategory.INPUT_IO_ERROR -> GpxFailureReason.SOURCE_IO
                GpxFailureCategory.SECURITY_CONFIGURATION_UNAVAILABLE -> GpxFailureReason.CONFIGURATION_UNAVAILABLE
                else -> if (xml) GpxFailureReason.XML_REJECTED else GpxFailureReason.PROFILE_REJECTED
            }
        }
        return GpxParseResult.Rejected(typed, GpxFailureContext(detail, address))
    }
}
