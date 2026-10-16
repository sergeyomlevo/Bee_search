package org.beesearch.app.data.gpx

import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.gpx.GpxWarningCategory
import java.util.UUID

/** One-way persistence mapping of the accepted graph; no reparse or value normalization. */
internal fun pendingGpxGraph(
    result: GpxParseResult.Accepted, job: GpxImportJobEntity, displayName: String?,
    importedAtEpochMs: Long, newId: () -> UUID,
): GpxStoredGraph {
    val draft = result.draft
    val content = result.diagnostics.singleOrNull { it.category == GpxWarningCategory.UNSUPPORTED_CONTENT }
    val time = result.diagnostics.singleOrNull { it.category == GpxWarningCategory.UNSUPPORTED_TIME }
    val row = GpxImportEntity(
        id = newId(), territoryId = job.territoryId, importingObserverId = job.importingObserverId,
        originalObjectId = requireNotNull(job.originalObjectId),
        originalSha256 = result.originalIdentity.sha256, originalByteLength = result.originalIdentity.length,
        displayName = displayName, gpxVersion = draft.declaredGpxVersion,
        parserProfileVersion = draft.parserProfileVersion, importedAtEpochMs = importedAtEpochMs,
        creator = draft.metadata.creator, sourceName = draft.metadata.sourceName,
        sourceDescription = draft.metadata.sourceDesc, authorName = draft.metadata.authorName,
        unsupportedContentCount = content?.count ?: 0,
        unsupportedContentFirstScope = content?.firstAddress?.scope?.name,
        unsupportedContentFirstTrackOrdinal = content?.firstAddress?.track,
        unsupportedContentFirstSegmentOrdinal = content?.firstAddress?.segment,
        unsupportedContentFirstSampleOrdinal = content?.firstAddress?.sample,
        unsupportedTimeCount = time?.count ?: 0, unsupportedTimeFirstScope = time?.firstAddress?.scope?.name,
        unsupportedTimeFirstTrackOrdinal = time?.firstAddress?.track,
        unsupportedTimeFirstSegmentOrdinal = time?.firstAddress?.segment,
        unsupportedTimeFirstSampleOrdinal = time?.firstAddress?.sample,
    )
    val tracks = mutableListOf<GpxSourceTrackEntity>()
    val segments = mutableListOf<GpxTrackSegmentEntity>()
    val samples = mutableListOf<GpxTrackSampleEntity>()
    draft.tracks.forEach { track ->
        val trackId = newId()
        tracks += GpxSourceTrackEntity(trackId, row.id, track.ordinal, track.name, track.description)
        track.segments.forEach { segment ->
            val segmentId = newId()
            segments += GpxTrackSegmentEntity(segmentId, trackId, segment.ordinal)
            segment.samples.forEach { sample ->
                samples += GpxTrackSampleEntity(segmentId, sample.ordinal, sample.lat.canonicalPlain,
                    sample.lon.canonicalPlain, sample.elevation?.canonicalPlain, sample.time.raw,
                    sample.time.status.name, sample.time.epochSeconds, sample.time.nanos)
            }
        }
    }
    return GpxStoredGraph(row, tracks, segments, samples).also { it.validate() }
}
