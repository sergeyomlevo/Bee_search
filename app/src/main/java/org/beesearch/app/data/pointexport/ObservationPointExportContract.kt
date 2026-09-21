package org.beesearch.app.data.pointexport

import java.util.UUID
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory

internal object ObservationPointExportContract {
    const val FORMAT_VERSION = 1
    const val PROFILE = "SINGLE_OBSERVATION_POINT"
    const val MANIFEST_ENTRY = "manifest.json"
    const val POINT_ENTRY = "point.json"
    const val ATTACHMENT_PREFIX = "attachments/"
    const val MAX_ENTRIES = 64
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    fun attachmentEntry(id: UUID): String = "$ATTACHMENT_PREFIX$id"
}

internal data class ObservationPointExportGraph(
    val point: ObservationPoint,
    val territory: Territory,
    val observer: Observer,
    val weather: ObservationPointWeather?,
    val beeHistories: List<BeeObservationHistory>,
    val attachments: List<ObservationPointAttachment>,
)

internal data class DecodedObservationPointExport(
    val graph: ObservationPointExportGraph,
    val attachmentBytes: Map<UUID, ByteArray>,
)

internal sealed class ObservationPointExportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal class InvalidObservationPointExport(message: String, cause: Throwable? = null) :
    ObservationPointExportException(message, cause)

internal class ObservationPointExportIntegrityError(message: String) :
    ObservationPointExportException(message)

internal class ObservationPointExportSourceMissing(message: String) :
    ObservationPointExportException(message)
