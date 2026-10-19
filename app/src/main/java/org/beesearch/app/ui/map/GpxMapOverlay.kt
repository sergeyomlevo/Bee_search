package org.beesearch.app.ui.map

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.beesearch.app.data.local.room.GpxStoredGraph
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.Source

internal data class GpxPreparedOverlay(val id: UUID, val revision: Long, val json: String) {
    val key: String get() = "gpx-$id"
    fun declaration(): MapOverlay<Source, Layer> = MapOverlay(
        key = key, revision = revision,
        sources = listOf(OverlaySourceFactory("geometry") { GeoJsonSource(it, json) }),
        layers = listOf(
            OverlayLayerFactory("lines", "geometry") { layer, source ->
                LineLayer(layer, source).withFilter(eq(geometryType(), literal("LineString")))
                    .withProperties(lineColor("#8B4F2B"), lineWidth(3f))
            },
            OverlayLayerFactory("singletons", "geometry") { layer, source ->
                CircleLayer(layer, source).withFilter(eq(geometryType(), literal("Point")))
                    .withProperties(circleColor("#8B4F2B"), circleRadius(5f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(1.5f))
            },
        ),
    )
}

internal class GpxPresentationCapacityException : IllegalStateException("GPX presentation memory unavailable")

/** All-or-nothing direct serialization: no second Feature/Point object graph, no original XML. */
internal suspend fun prepareGpxOverlay(
    territoryId: UUID, graph: GpxStoredGraph, revision: Long,
    availableHeapBytes: () -> Long = { Runtime.getRuntime().let { it.maxMemory() - it.totalMemory() + it.freeMemory() } },
): GpxPreparedOverlay = withContext(Dispatchers.Default) {
    require(graph.importRow.territoryId == territoryId && graph.importRow.publicationState == "READY")
    val canonical = graph.canonical()
    val context = currentCoroutineContext()
    // Exact ASCII JSON size, checked before allocation. Budget the builder and final String at
    // worst-case UTF-16 (two bytes/char each); this is live headroom, not a product point/file cap.
    var chars = 0L
    fun count(value: String) { chars = Math.addExact(chars, value.length.toLong()) }
    fun write(append: (String) -> Unit) {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        for (track in canonical.tracks) for (segment in track.segments) {
            context.ensureActive()
            if (segment.samples.isEmpty()) continue
            if (!first) append(",")
            first = false
            append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":")
            val singleton = segment.samples.size == 1
            append(if (singleton) "\"Point\",\"coordinates\":" else "\"LineString\",\"coordinates\":[")
            segment.samples.forEachIndexed { index, sample ->
                context.ensureActive()
                if (index != 0) append(",")
                append("["); append(sample.lon.canonicalPlain); append(","); append(sample.lat.canonicalPlain); append("]")
            }
            if (!singleton) append("]")
            append("}}")
        }
        append("]}")
    }
    write(::count)
    if (chars > Int.MAX_VALUE || Math.multiplyExact(chars, 4L) > availableHeapBytes()) throw GpxPresentationCapacityException()
    try {
        val builder = StringBuilder(chars.toInt())
        write { builder.append(it) }
        context.ensureActive()
        GpxPreparedOverlay(graph.importRow.id, revision, builder.toString())
    } catch (_: OutOfMemoryError) { throw GpxPresentationCapacityException() }
}
