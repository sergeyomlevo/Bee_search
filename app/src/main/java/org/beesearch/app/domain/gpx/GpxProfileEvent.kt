package org.beesearch.app.domain.gpx

/** Provider-normalized facts only. This API does not parse bytes, XML syntax or namespaces. */
internal sealed interface GpxProfileEvent {
    data class Start(val name: String, val namespace: String, val attributes: List<GpxProfileAttribute> = emptyList()) : GpxProfileEvent
    data class End(val name: String, val namespace: String) : GpxProfileEvent
    data class Text(val value: String) : GpxProfileEvent
    data object ProcessingInstruction : GpxProfileEvent
}
internal data class GpxProfileAttribute(val name: String, val namespace: String, val value: String)
internal enum class GpxWarningCategory { UNSUPPORTED_CONTENT, UNSUPPORTED_TIME }
internal enum class GpxProfileScope { ROOT, METADATA, AUTHOR, TRACK, SEGMENT, SAMPLE }
internal data class GpxProfileAddress(
    val scope: GpxProfileScope,
    val track: Int? = null,
    val segment: Int? = null,
    val sample: Int? = null,
)
internal data class GpxProfileWarning(
    val category: GpxWarningCategory,
    val count: Long,
    val firstAddress: GpxProfileAddress,
)
internal data class GpxProfileResult(val draft: GpxCanonicalDraft, val warnings: GpxFrozenList<GpxProfileWarning>)
internal class GpxProfileFailure(val category: String, val reason: String, val address: GpxProfileAddress? = null) :
    IllegalArgumentException("$category:$reason")