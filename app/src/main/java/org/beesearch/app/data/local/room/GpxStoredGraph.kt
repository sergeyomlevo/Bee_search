package org.beesearch.app.data.local.room

import java.math.BigDecimal
import org.beesearch.app.domain.gpx.*

/** Persisted envelope and ordered source identities; no identity generation or XML reinterpretation. */
internal data class GpxStoredGraph(
    val importRow: GpxImportEntity,
    val tracks: List<GpxSourceTrackEntity>,
    val segments: List<GpxTrackSegmentEntity>,
    val samples: List<GpxTrackSampleEntity>,
) {
    fun canonical(): GpxCanonicalDraft = integrity {
        require((importRow.gpxVersion == "1.1" && importRow.parserProfileVersion == "GPX_1_1_STAGE_1") ||
            (importRow.gpxVersion == "1.0" && importRow.parserProfileVersion == "GPX_1_0_STAGE_1"))
        require(tracks.map { it.id }.toSet().size == tracks.size)
        require(segments.map { it.id }.toSet().size == segments.size)
        require(samples.map { it.segmentId to it.ordinal }.toSet().size == samples.size)
        val trackIds = tracks.map { it.id }.toSet()
        val segmentIds = segments.map { it.id }.toSet()
        require(tracks.all { it.importId == importRow.id })
        require(segments.all { it.trackId in trackIds } && samples.all { it.segmentId in segmentIds })
        require(samples.isNotEmpty())
        val byTrack = segments.groupBy { it.trackId }
        val bySegment = samples.groupBy { it.segmentId }
        GpxCanonicalDraft(GpxMetadata(importRow.creator, importRow.sourceName, importRow.sourceDescription, importRow.authorName),
            tracks.sortedBy { it.ordinal }.map { track ->
                GpxTrack(track.ordinal, byTrack[track.id].orEmpty().sortedBy { it.ordinal }.map { segment ->
                    GpxSegment(segment.ordinal, bySegment[segment.id].orEmpty().sortedBy { it.ordinal }.map { row ->
                        GpxSample(row.ordinal, decimal(row.latitude), decimal(row.longitude), row.elevation?.let(::decimal),
                            GpxSampleTime(row.timeRaw, GpxTimeStatus.valueOf(row.timeStatus), row.timeEpochSeconds, row.timeNanos))
                    })
                }, track.name, track.description)
            }, importRow.gpxVersion)
    }
    fun warnings(): List<GpxProfileWarning> = integrity {
        listOfNotNull(
            warning(GpxWarningCategory.UNSUPPORTED_CONTENT, importRow.unsupportedContentCount,
                importRow.unsupportedContentFirstScope, importRow.unsupportedContentFirstTrackOrdinal,
                importRow.unsupportedContentFirstSegmentOrdinal, importRow.unsupportedContentFirstSampleOrdinal),
            warning(GpxWarningCategory.UNSUPPORTED_TIME, importRow.unsupportedTimeCount,
                importRow.unsupportedTimeFirstScope, importRow.unsupportedTimeFirstTrackOrdinal,
                importRow.unsupportedTimeFirstSegmentOrdinal, importRow.unsupportedTimeFirstSampleOrdinal),
        )
    }
    fun validate() { integrity {
        require(importRow.publicationState in setOf("PENDING", "READY"))
        require(importRow.publicationState == "READY" || importRow.requestUuid == null)
        require(importRow.originalByteLength > 0 && importRow.originalSha256.matches(Regex("[0-9a-fA-F]{64}")))
        canonical(); warnings()
    } }
    fun snapshot() = copy(tracks = tracks.toList(), segments = segments.toList(), samples = samples.toList())

    private fun decimal(text: String): GpxDecimal = GpxDecimal.fromDecimal(BigDecimal(text)).also {
        require(it.canonicalPlain == text)
    }
    private fun warning(category: GpxWarningCategory, count: Long, scope: String?, track: Int?, segment: Int?, sample: Int?): GpxProfileWarning? {
        require(count >= 0)
        if (count == 0L) { require(scope == null && track == null && segment == null && sample == null); return null }
        require(listOfNotNull(track, segment, sample).all { it >= 0 })
        return GpxProfileWarning(category, count, GpxProfileAddress(GpxProfileScope.valueOf(requireNotNull(scope)), track, segment, sample))
    }
    private inline fun <T> integrity(block: () -> T): T = try { block() } catch (_: IllegalArgumentException) {
        throw GpxPersistenceIntegrityException("CANONICAL_GRAPH")
    }
}
