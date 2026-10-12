package org.beesearch.app.ui.map

import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.Source

/**
 * Synthetic, glyph-free runtime content used only by the opt-in MapLibre lifecycle tests.
 * It deliberately has no Territory, Room, package, or user-data dependency.
 */
internal const val OVERLAY_REGISTRY_FIXTURE_KEY = "registry-fixture"

internal fun overlayRegistryFixture(
    revision: Long = 1L,
    visible: Boolean = true,
    key: String = OVERLAY_REGISTRY_FIXTURE_KEY,
): MapOverlay<Source, Layer> = MapOverlay(
    key = key,
    revision = revision,
    visible = visible,
    sources = listOf(
        OverlaySourceFactory("points") { id -> GeoJsonSource(id, FIXTURE_GEOJSON) },
    ),
    layers = listOf(
        OverlayLayerFactory("circles", "points") { layerId, sourceId ->
            CircleLayer(layerId, sourceId)
        },
    ),
)

internal const val OVERLAY_REGISTRY_FIXTURE_STYLE_A = """
{
  "version": 8,
  "name": "Overlay Registry Fixture A",
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#f4f0e6"}}]
}
"""

internal const val OVERLAY_REGISTRY_FIXTURE_STYLE_B = """
{
  "version": 8,
  "name": "Overlay Registry Fixture B",
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#e8f2f5"}}]
}
"""

private const val FIXTURE_GEOJSON = """
{"type":"FeatureCollection","features":[
  {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[39.0,57.0]}}
]}
"""
