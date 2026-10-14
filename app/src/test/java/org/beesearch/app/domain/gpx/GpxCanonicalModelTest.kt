package org.beesearch.app.domain.gpx

import org.junit.Assert.*
import org.junit.Test

class GpxCanonicalModelTest {
    private fun sample(ordinal: Int = 0, lat: String = "0") = GpxSample(ordinal,
        GpxCanonicalValues.latitude(lat), GpxCanonicalValues.longitude("0"))

    @Test fun everyOwnedListIsDefensivelyFrozen() {
        val samples = mutableListOf(sample())
        val segment = GpxSegment(0, samples)
        val segments = mutableListOf(segment)
        val track = GpxTrack(0, segments)
        val tracks = mutableListOf(track)
        val root = GpxCanonicalDraft(GpxMetadata(""), tracks)
        val beforeHash = root.hashCode()
        samples.clear(); segments.clear(); tracks.clear()
        assertEquals(1, root.tracks.size)
        assertEquals(1, root.tracks[0].segments.size)
        assertEquals(listOf(sample()), root.tracks[0].segments[0].samples)
        assertEquals(beforeHash, root.hashCode())
        assertThrows(RuntimeException::class.java) { (root.tracks as MutableList<*>).clear() }
        assertThrows(RuntimeException::class.java) { (segment.samples as MutableList<*>).clear() }
        assertThrows(RuntimeException::class.java) { (track.segments as MutableList<*>).clear() }
    }

    @Test fun copyCannotIntroduceMutableListAliases() {
        val mutable = mutableListOf(sample())
        val root = GpxCanonicalDraft(GpxMetadata("creator"), listOf(GpxTrack(0, listOf(GpxSegment(0, mutable)))))
        val copy = root.copy()
        mutable[0] = sample(lat = "1")
        assertEquals(root, copy)
        assertEquals("0", copy.tracks[0].segments[0].samples[0].lat.canonicalPlain)
        assertThrows(IllegalArgumentException::class.java) {
            root.copy(tracks = GpxFrozenList(listOf(GpxTrack(1, emptyList()))))
        }
    }

    @Test fun a01SingletonAndOptionalNullsHaveValueEquality() {
        fun root() = GpxCanonicalDraft(GpxMetadata("fixture"), listOf(GpxTrack(0, listOf(GpxSegment(0, listOf(sample()))))))
        assertEquals(root(), root())
        assertEquals(root().hashCode(), root().hashCode())
        val s = root().tracks[0].segments[0].samples.single()
        assertNull(s.elevation)
        assertEquals(GpxTimeStatus.MISSING, s.time.status)
        assertNull(s.time.raw)
        assertEquals("1.1", root().declaredGpxVersion)
    }

    @Test fun a02A03A04OrderAndEmptyBoundariesAreRetained() {
        val root = GpxCanonicalDraft(GpxMetadata("creator"), listOf(
            GpxTrack(0, emptyList()), GpxTrack(1, listOf(GpxSegment(0, emptyList()),
                GpxSegment(1, listOf(sample(0, "3"), sample(1, "1"), sample(2, "2"))))),
        ))
        assertTrue(root.tracks[0].segments.isEmpty())
        assertTrue(root.tracks[1].segments[0].samples.isEmpty())
        assertEquals(listOf("3", "1", "2"), root.tracks[1].segments[1].samples.map { it.lat.canonicalPlain })
        assertEquals(listOf(0, 1, 2), root.tracks[1].segments[1].samples.map { it.ordinal })
        assertEquals(listOf(0, 1), root.tracks.map { it.ordinal })
        assertTrue(GpxCanonicalDraft(GpxMetadata("creator"), emptyList()).tracks.isEmpty())
    }

    @Test fun zeroBasedParentLocalOrdinalConsistencyIsMandatory() {
        assertThrows(IllegalArgumentException::class.java) { sample(-1) }
        assertThrows(IllegalArgumentException::class.java) { GpxSegment(0, listOf(sample(1))) }
        assertThrows(IllegalArgumentException::class.java) { GpxTrack(0, listOf(GpxSegment(1, emptyList()))) }
        assertThrows(IllegalArgumentException::class.java) { GpxCanonicalDraft(GpxMetadata("x"), listOf(GpxTrack(1, emptyList()))) }
        assertThrows(IllegalArgumentException::class.java) { GpxSegment(-1, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { GpxTrack(-1, emptyList()) }
    }

    @Test fun a05A06MetadataIsExactNullEmptyWhitespaceAndNoNfc() {
        val nullName = GpxMetadata("creator")
        assertNotEquals(nullName, nullName.copy(sourceName = ""))
        assertNotEquals(nullName.copy(sourceName = ""), nullName.copy(sourceName = " "))
        assertNotEquals(nullName.copy(sourceName = "é"), nullName.copy(sourceName = "e\u0301"))
        val m = GpxMetadata("", "😀", "\t description\n", "author")
        assertEquals("", m.creator)
        assertEquals("\t description\n", m.sourceDesc)
        assertEquals("😀", m.sourceName)
        assertEquals("author", m.authorName)
    }

    @Test fun semanticMetadataLimitsUseScalarsAndValidateConstructedUnicode() {
        assertEquals("😀".repeat(1024), GpxMetadata("", sourceName = "😀".repeat(1024)).sourceName)
        val limit = assertThrows(GpxValueFailure::class.java) { GpxMetadata("x".repeat(1025)) }
        assertEquals("G08", limit.reason)
        assertThrows(GpxValueFailure::class.java) { GpxTrack(0, emptyList(), name = "\ud800") }
    }

    @Test fun sampleCannotUseElevationValueToBypassCoordinateRange() {
        assertThrows(IllegalArgumentException::class.java) {
            GpxSample(0, GpxCanonicalValues.elevation("1000")!!, GpxCanonicalValues.longitude("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GpxSample(0, GpxCanonicalValues.latitude("0"), GpxCanonicalValues.elevation("180")!!)
        }
    }
}