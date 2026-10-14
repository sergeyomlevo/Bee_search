package org.beesearch.app.data.gpx

import org.beesearch.app.domain.gpx.GpxProfileAttribute
import org.beesearch.app.domain.gpx.GpxProfileEvent
import org.beesearch.app.domain.gpx.GpxProfileFailure
import org.beesearch.app.domain.gpx.GpxProfileParser
import org.beesearch.app.domain.gpx.GpxProfileResult
import java.io.IOException
import java.io.InputStream

internal data class GpxProfileRead(val profile: GpxProfileResult, val verification: GpxXmlVerification)

/** Minimal S3 bridge. The original qualified chain remains the sole production XML parser. */
internal object AndroidGpxProfileReader {
    fun parse(open: () -> InputStream, checkCancellation: () -> Unit = {}): GpxProfileRead {
        val profile = GpxProfileParser(checkCancellation)
        try {
            val verification = AndroidGpxXmlReader.verify(open, checkCancellation) { event ->
                when (event.kind) {
                    GpxXmlObservation.Kind.START -> profile.on(GpxProfileEvent.Start(
                        checkNotNull(event.name), event.namespace.orEmpty(), event.attributes.map {
                            // Prefix syntax/resolution/expanded-name uniqueness were checked by Slice1.
                            GpxProfileAttribute(it.name.substringAfter(':'), it.namespace, it.value)
                        },
                    ))
                    GpxXmlObservation.Kind.END -> profile.on(GpxProfileEvent.End(checkNotNull(event.name), event.namespace.orEmpty()))
                    GpxXmlObservation.Kind.TEXT, GpxXmlObservation.Kind.CDATA -> profile.on(GpxProfileEvent.Text(event.value.orEmpty()))
                    GpxXmlObservation.Kind.PROCESSING_INSTRUCTION -> profile.on(GpxProfileEvent.ProcessingInstruction)
                    GpxXmlObservation.Kind.COMMENT -> Unit
                }
            }
            // EOF, well-formedness, GUARD_SDK_AGREEMENT, exact-source equality and close all succeeded.
            return GpxProfileRead(profile.finish(), verification)
        } catch (error: Throwable) {
            profile.abort()
            if (error is IOException && error !is GpxXmlFailure) throw GpxProfileFailure("INPUT_IO_ERROR", "SOURCE_IO")
            throw error
        }
    }
}