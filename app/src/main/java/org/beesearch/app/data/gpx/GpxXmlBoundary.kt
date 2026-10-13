package org.beesearch.app.data.gpx

import java.io.IOException

/** Slice-1 boundaries only: no GPX/domain/persistence types. */
internal class GpxXmlFailure(val category: String, val reason: String) :
    IOException(category + ":" + reason)

internal data class GpxXmlLimits(
    val bytes: Long = 16L * 1024 * 1024,
    val token: Int = 8192,
    val attributes: Int = 64,
    val depth: Int = 32,
    val events: Long = 2_000_000,
)

internal data class GpxOriginalIdentity(val length: Long, val sha256: String)
internal data class GpxLexicalAttribute(val name: String, val value: String)
internal data class GpxStartFacts(val name: String, val attributes: List<GpxLexicalAttribute>)

/** Observational counters, not a capacity promise or canonical graph. */
internal class GpxXmlMetrics {
    var bytesRead = 0L
    var declarationEvents = 0L
    var decodedChars = 0L
    var deliveredChars = 0L
    var events = 0L
    var maxDepth = 0
    var maxTokenScalars = 0
    var maxBufferedChars = 0
    var maxPendingStarts = 0
    var referenceDigits = 0L
    var maxReferenceState = 0
}

internal fun gpxFail(category: String, reason: String): Nothing =
    throw GpxXmlFailure(category, reason)
