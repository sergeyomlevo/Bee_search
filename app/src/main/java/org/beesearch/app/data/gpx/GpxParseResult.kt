package org.beesearch.app.data.gpx

import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxFrozenList
import org.beesearch.app.domain.gpx.GpxProfileAddress
import org.beesearch.app.domain.gpx.GpxProfileWarning

/** Processing admission only: no persistence, ownership or logical import identity. */
internal sealed interface GpxParseResult {
    data class Accepted(
        val draft: GpxCanonicalDraft,
        val originalIdentity: GpxOriginalIdentity,
        val diagnostics: GpxFrozenList<GpxProfileWarning>,
    ) : GpxParseResult

    data class Rejected(
        val category: GpxFailureCategory,
        val boundedContext: GpxFailureContext,
    ) : GpxParseResult
}

internal enum class GpxFailureCategory {
    UNSUPPORTED_PROFILE, UNSUPPORTED_ENCODING, MALFORMED_XML, INVALID_STRUCTURE,
    INVALID_COORDINATE, INVALID_OPTIONAL_VALUE, NO_IMPORTABLE_TRACK,
    SECURITY_REJECTED, SECURITY_CONFIGURATION_UNAVAILABLE, LIMIT_EXCEEDED, INPUT_IO_ERROR,
}

/** Finite diagnostic vocabulary; never retain source text, URI or exception messages. */
internal enum class GpxFailureReason(val code: String) {
    XML_REJECTED("XML_REJECTED"), PROFILE_REJECTED("PROFILE_REJECTED"),
    CONFIGURATION_UNAVAILABLE("CONFIGURATION_UNAVAILABLE"),
    SOURCE_CHANGED("SOURCE_CHANGED"), SOURCE_OPEN("source-open"), SOURCE_READ("source-read"),
    SOURCE_CLOSE("source-close"), SOURCE_IO("SOURCE_IO"),
    G01("G01"), G02("G02"), G03("G03"), G04("G04"), G05("G05"),
    G06("G06"), G07("G07"), G08("G08"), G09("G09"), G10("G10"),
    MISSING_LATITUDE("MISSING_LATITUDE"), MISSING_LONGITUDE("MISSING_LONGITUDE"),
    LATITUDE_RANGE("LATITUDE_RANGE"), LONGITUDE_RANGE("LONGITUDE_RANGE"),
    DECIMAL_GRAMMAR("DECIMAL_GRAMMAR"), DECLARATION_BARRIER("DECLARATION_BARRIER"),
}

internal data class GpxFailureContext(
    val reason: GpxFailureReason,
    val address: GpxProfileAddress? = null,
)
