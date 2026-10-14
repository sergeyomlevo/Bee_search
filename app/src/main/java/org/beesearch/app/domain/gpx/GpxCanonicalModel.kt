package org.beesearch.app.domain.gpx

import java.util.Collections

/** One defensive freeze per owned list. copy() can only receive another frozen list. */
internal class GpxFrozenList<T>(source: List<T>) : AbstractList<T>(), RandomAccess {
    private val items = Collections.unmodifiableList(ArrayList(source))
    override val size: Int get() = items.size
    override fun get(index: Int): T = items[index]
}

internal data class GpxMetadata(
    val creator: String,
    val sourceName: String? = null,
    val sourceDesc: String? = null,
    val authorName: String? = null,
) {
    init { listOf(creator, sourceName, sourceDesc, authorName).forEach { metadataText(it) } }
}

internal data class GpxSample(
    val ordinal: Int,
    val lat: GpxDecimal,
    val lon: GpxDecimal,
    val elevation: GpxDecimal? = null,
    val time: GpxSampleTime = GpxCanonicalValues.time(null),
) {
    init {
        require(ordinal >= 0)
        require(lat.inLatitudeRange() && lon.inLongitudeRange())
    }
}

internal data class GpxSegment(val ordinal: Int, val samples: GpxFrozenList<GpxSample>) {
    constructor(ordinal: Int, samples: List<GpxSample>) : this(ordinal, GpxFrozenList(samples))
    init {
        require(ordinal >= 0)
        samples.forEachIndexed { index, sample -> require(sample.ordinal == index) }
    }
}

internal data class GpxTrack(
    val ordinal: Int,
    val name: String?,
    val description: String?,
    val segments: GpxFrozenList<GpxSegment>,
) {
    constructor(ordinal: Int, segments: List<GpxSegment>, name: String? = null, description: String? = null) :
        this(ordinal, name, description, GpxFrozenList(segments))
    init {
        require(ordinal >= 0)
        metadataText(name)
        metadataText(description)
        segments.forEachIndexed { index, segment -> require(segment.ordinal == index) }
    }
}

/** Value equality, never logical import identity. Provenance is not a CM1 field. */
internal data class GpxCanonicalDraft(
    val metadata: GpxMetadata,
    val tracks: GpxFrozenList<GpxTrack>,
) {
    constructor(metadata: GpxMetadata, tracks: List<GpxTrack>) : this(metadata, GpxFrozenList(tracks))
    val declaredGpxVersion: String get() = "1.1"
    val parserProfileVersion: String get() = "GPX_1_1_STAGE_1"
    init { tracks.forEachIndexed { index, track -> require(track.ordinal == index) } }
}

private fun metadataText(text: String?) { text?.let { GpxValueText.check(it, 1024, "G08") } }