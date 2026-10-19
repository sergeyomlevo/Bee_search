package org.beesearch.app.ui.map

import java.util.UUID
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import org.beesearch.app.data.local.room.GpxImportEntity
import org.beesearch.app.data.local.room.GpxPersistenceIntegrityException
import org.beesearch.app.data.local.room.GpxSourceTrackEntity
import org.beesearch.app.data.local.room.GpxStoredGraph
import org.beesearch.app.data.local.room.GpxTrackSampleEntity
import org.beesearch.app.data.local.room.GpxTrackSegmentEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure geometry contract for READY canonical GPX display preparation. */
class GpxGeometryDisplayTest {
    private val territory = id(1)
    private val otherTerritory = id(2)

    @Test
    fun preparesEveryTrackAndSegmentWithoutBridgingAndPreservesLonLat() = runBlocking {
        val graph = graph(
            tracks = listOf(track(10, 0), track(11, 1)),
            segments = listOf(segment(20, 10, 0), segment(21, 10, 1), segment(22, 11, 0)),
            samples = listOf(
                sample(20, 0, "56.100", "42.700"), sample(20, 1, "56.200", "42.800"),
                sample(21, 0, "57.000", "43.000"),
                sample(22, 0, "58.000", "44.000"), sample(22, 1, "58.100", "44.100"),
            ),
        )

        val json = JSONObject(prepareGpxOverlay(territory, graph, revision = 7).json)
        val features = json.getJSONArray("features")
        assertEquals(3, features.length())
        assertEquals("LineString", features.getJSONObject(0).getJSONObject("geometry").getString("type"))
        assertEquals("Point", features.getJSONObject(1).getJSONObject("geometry").getString("type"))
        assertEquals("LineString", features.getJSONObject(2).getJSONObject("geometry").getString("type"))

        val lineCoordinates = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(42.700, lineCoordinates.getJSONArray(0).getDouble(0), 0.0)
        assertEquals(56.100, lineCoordinates.getJSONArray(0).getDouble(1), 0.0)
        assertEquals(42.800, lineCoordinates.getJSONArray(1).getDouble(0), 0.0)
        assertEquals(56.200, lineCoordinates.getJSONArray(1).getDouble(1), 0.0)
        assertEquals(2, features.getJSONObject(1).getJSONObject("geometry").getJSONArray("coordinates").length())
    }

    @Test
    fun emptySegmentsProduceNoGeometryAndSingletonProducesPoint() = runBlocking {
        val graph = graph(
            tracks = listOf(track(10, 0)),
            segments = listOf(segment(20, 10, 0), segment(21, 10, 1)),
            samples = listOf(sample(20, 0, "56", "42")),
        )
        val features = JSONObject(prepareGpxOverlay(territory, graph, 1).json).getJSONArray("features")
        assertEquals(1, features.length())
        assertEquals("Point", features.getJSONObject(0).getJSONObject("geometry").getString("type"))
    }

    @Test
    fun invalidCanonicalGraphFailsAsOneFileAndTerritoryMismatchIsRejected(): Unit = runBlocking {
        val invalidCoordinate = graph(
            tracks = listOf(track(10, 0)), segments = listOf(segment(20, 10, 0)),
            samples = listOf(sample(20, 0, "91", "42")),
        )
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            runBlocking { prepareGpxOverlay(territory, invalidCoordinate, 1) }
        }

        val ordinalHole = graph(
            tracks = listOf(track(10, 0)), segments = listOf(segment(20, 10, 0)),
            samples = listOf(sample(20, 0, "56", "42"), sample(20, 2, "56.1", "42.1")),
        )
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            runBlocking { prepareGpxOverlay(territory, ordinalHole, 1) }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { prepareGpxOverlay(otherTerritory, graph(), 1) }
        }
    }

    @Test
    fun zeroHeadroomRejectsBeforePublishingAnyOverlay(): Unit = runBlocking {
        assertThrows(GpxPresentationCapacityException::class.java) {
            runBlocking {
                prepareGpxOverlay(territory, graph(), revision = 1, availableHeapBytes = { 0L })
            }
        }
    }

    @Test
    fun largeAllowedGraphRecordsCapacityMeasurement() = runBlocking {
        val graph = graphWithSamples(100_000)
        val runtime = Runtime.getRuntime()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        val overlay = prepareGpxOverlay(territory, graph, revision = 2, availableHeapBytes = { Long.MAX_VALUE })
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        check(overlay.json.isNotEmpty())
        val evidenceDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null)!!.resolve("d106-gpx-display")
        evidenceDir.mkdirs()
        evidenceDir.resolve("capacity.txt").writeText(
            "samples=100000\n" +
                "elapsedMs=$elapsedMs\n" +
                "jsonBytes=${overlay.json.toByteArray(Charsets.UTF_8).size}\n" +
                "heapBefore=$heapBefore\n" +
                "heapAfter=$heapAfter\n",
        )
    }

    private fun graph(
        tracks: List<GpxSourceTrackEntity> = listOf(track(10, 0)),
        segments: List<GpxTrackSegmentEntity> = listOf(segment(20, 10, 0)),
        samples: List<GpxTrackSampleEntity> = listOf(sample(20, 0, "56", "42")),
    ) = GpxStoredGraph(importRow(), tracks, segments, samples)

    private fun graphWithSamples(count: Int): GpxStoredGraph = graph(
        samples = List(count) { ordinal ->
            sample(20, ordinal, "56.${ordinal % 10}", "42.${ordinal % 10}")
        },
    )

    private fun importRow(territoryId: UUID = territory) = GpxImportEntity(
        id = id(100), territoryId = territoryId, originalObjectId = id(101),
        originalSha256 = "a".repeat(64), originalByteLength = 1,
        gpxVersion = "1.1", parserProfileVersion = "GPX_1_1_STAGE_1",
        importedAtEpochMs = 1, creator = "test", publicationState = "READY",
    )

    private fun track(id: Int, ordinal: Int) = GpxSourceTrackEntity(this.id(id), this.id(100), ordinal)
    private fun segment(id: Int, trackId: Int, ordinal: Int) = GpxTrackSegmentEntity(this.id(id), this.id(trackId), ordinal)
    private fun sample(segmentId: Int, ordinal: Int, latitude: String, longitude: String) =
        GpxTrackSampleEntity(this.id(segmentId), ordinal, java.math.BigDecimal(latitude).stripTrailingZeros().toPlainString(), java.math.BigDecimal(longitude).stripTrailingZeros().toPlainString(), timeStatus = "MISSING")

    private fun id(value: Int) = UUID(0, value.toLong())
}
