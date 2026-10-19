package org.beesearch.app.domain.gpx

/** Tentative owned builders; only the profile parser can expose the final immutable root. */
internal class GpxProfileDraftBuilder {
    var creator = ""
    var sourceName: String? = null
    var version = "1.1"
    var sourceDesc: String? = null
    var authorName: String? = null
    val tracks = ArrayList<GpxTrack>()
    var track: Track? = null
    var segment: Segment? = null
    var sample: Sample? = null
    var segmentCount = 0
    var sampleCount = 0
    class Track(val ordinal: Int) {
        var name: String? = null
        var description: String? = null
        val segments = ArrayList<GpxSegment>()
    }
    class Segment(val ordinal: Int) { val samples = ArrayList<GpxSample>() }
    class Sample(val ordinal: Int, val lat: GpxDecimal, val lon: GpxDecimal) {
        var elevation: GpxDecimal? = null
        var time = GpxCanonicalValues.time(null)
    }
    fun address(scope: GpxProfileScope) = GpxProfileAddress(scope, track?.ordinal, segment?.ordinal, sample?.ordinal)
    fun closeSample() {
        val s = checkNotNull(sample)
        checkNotNull(segment).samples.add(GpxSample(s.ordinal, s.lat, s.lon, s.elevation, s.time))
        sample = null
    }
    fun closeSegment() {
        val s = checkNotNull(segment)
        checkNotNull(track).segments.add(GpxSegment(s.ordinal, s.samples))
        segment = null
    }
    fun closeTrack() {
        val t = checkNotNull(track)
        tracks.add(GpxTrack(t.ordinal, t.segments, t.name, t.description))
        track = null
    }
    fun freeze() = GpxCanonicalDraft(GpxMetadata(creator, sourceName, sourceDesc, authorName), tracks, version)
    fun clear() {
        tracks.clear(); track = null; segment = null; sample = null
        creator = ""; sourceName = null; sourceDesc = null; authorName = null
    }
}