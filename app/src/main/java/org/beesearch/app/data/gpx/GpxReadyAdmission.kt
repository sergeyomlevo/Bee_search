package org.beesearch.app.data.gpx

/** OWNER-approved per-Territory READY admission; PENDING is not a reservation. */
internal enum class GpxAdmissionRejection { G11, G12, DUPLICATE_CONFIRMATION_REQUIRED }
internal class GpxAdmissionException(val rejection: GpxAdmissionRejection) : Exception(rejection.name)

internal fun gpxAdmissionRejection(readyImports: Long, readySamples: Long, candidateSamples: Long,
                                   duplicate: Boolean, confirmed: Boolean): GpxAdmissionRejection? {
    require(readyImports >= 0 && readySamples >= 0 && candidateSamples >= 0)
    return when {
        readyImports >= 1024 -> GpxAdmissionRejection.G11
        candidateSamples > 200000 || readySamples > 200000 - candidateSamples -> GpxAdmissionRejection.G12
        duplicate && !confirmed -> GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED
        else -> null
    }
}
