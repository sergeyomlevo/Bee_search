package org.beesearch.app

import org.beesearch.app.ui.map.ActiveMapPackage
import org.beesearch.app.ui.map.BeeSearchMapPackageCompatibility

private const val TEST_AREA_GEOJSON = """
{"type":"FeatureCollection","features":[
{"type":"Feature","properties":{"label":"FOREST","color":"#d85d3c"},"geometry":{"type":"Polygon","coordinates":[[[42.7460,56.0615],[42.7940,56.0615],[42.7940,56.0885],[42.7460,56.0885],[42.7460,56.0615]]]}},
{"type":"Feature","properties":{"label":"SAPUNOVO","color":"#315fb5"},"geometry":{"type":"Polygon","coordinates":[[[42.6413,56.0933],[42.6893,56.0933],[42.6893,56.1203],[42.6413,56.1203],[42.6413,56.0933]]]}}
]}
"""

internal data class BeeMapProfile(
    val profileId: String,
    val profileVersion: String,
    val datasetVersion: String,
    val styleVersion: String,
    val styleJson: String,
    val sourceMaxZoom: Double,
    val uiMaxZoom: Double,
)

internal fun beeSearchFieldMapProfile(): BeeMapProfile {
    return BeeMapProfile(
        profileId = "osm-standard-evaluation",
        profileVersion = "temporary-v1",
        datasetVersion = "OpenStreetMap Standard",
        styleVersion = "raster-v1",
        styleJson = OSM_STANDARD_EVALUATION_STYLE,
        sourceMaxZoom = 19.0,
        uiMaxZoom = 20.0,
    )
}

/**
 * The only normal-user local-vector profile: a D065-validated package that is
 * active for the current Territory. Fixture and diagnostic profiles remain
 * development-only (debug source set) and are never selected by this function.
 */
internal fun beeSearchActivePmtilesMapProfile(activePackage: ActiveMapPackage): BeeMapProfile {
    val manifest = activePackage.manifest
    check(manifest.profileId == BeeSearchMapPackageCompatibility.PROFILE_ID)
    check(manifest.profileVersion == BeeSearchMapPackageCompatibility.PROFILE_VERSION)
    check(manifest.styleVersion == BeeSearchMapPackageCompatibility.STYLE_VERSION)
    val archivePath = activePackage.pmtilesFile.absolutePath.replace('\\', '/')
    return BeeMapProfile(
        profileId = manifest.profileId,
        profileVersion = manifest.profileVersion,
        datasetVersion = manifest.datasetVersion,
        styleVersion = manifest.styleVersion,
        styleJson = localVectorPmtilesStyle("pmtiles://file://$archivePath", "Bee Search offline vector map"),
        sourceMaxZoom = manifest.maxZoom.toDouble(),
        uiMaxZoom = 20.0,
    )
}

/**
 * Style shared by the active offline-vector profile (main) and the legacy
 * local PMTiles PoC profiles (debug source set). Label glyphs are referenced
 * from assets/map-poc/glyphs, which the release build therefore keeps.
 */
internal fun localVectorPmtilesStyle(archiveUrl: String, styleName: String): String = """
{
  "version": 8,
  "name": "$styleName",
  "glyphs": "asset://map-poc/glyphs/{fontstack}/{range}.pbf",
  "sources": {
    "bee-field": {
      "type": "vector",
      "url": "$archiveUrl",
      "attribution": "© OpenStreetMap contributors"
    },
    "test-areas": {
      "type": "geojson",
      "data": $TEST_AREA_GEOJSON
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f3efdf" } },
    { "id": "open-land", "type": "fill", "source": "bee-field", "source-layer": "landcover", "filter": ["in", ["get", "class"], ["literal", ["farmland", "meadow", "grassland", "heath"]]], "paint": { "fill-color": "#d8d29e", "fill-opacity": 0.72 } },
    { "id": "forest", "type": "fill", "source": "bee-field", "source-layer": "landcover", "filter": ["in", ["get", "class"], ["literal", ["forest", "wood"]]], "paint": { "fill-color": "#78a86b", "fill-opacity": 0.72 } },
    { "id": "wetland", "type": "fill", "source": "bee-field", "source-layer": "landcover", "filter": ["==", ["get", "class"], "wetland"], "paint": { "fill-color": "#79b9b0", "fill-opacity": 0.62 } },
    { "id": "water", "type": "fill", "source": "bee-field", "source-layer": "water", "paint": { "fill-color": "#68aee8", "fill-opacity": 0.84 } },
    { "id": "waterways", "type": "line", "source": "bee-field", "source-layer": "waterway", "paint": { "line-color": "#277fc0", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1.0, 15, 3.0] } },
    { "id": "roads", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["in", ["get", "class"], ["literal", ["motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service"]]], "paint": { "line-color": "#b36b3e", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.8, 15, 4.0] } },
    { "id": "tracks", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["in", ["get", "class"], ["literal", ["track", "path", "footway", "steps"]]], "paint": { "line-color": "#8d6d4f", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 15, 2.5], "line-dasharray": [2, 1] } },
    { "id": "railway", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["==", ["get", "class"], "rail"], "paint": { "line-color": "#493e3a", "line-width": 2.0, "line-dasharray": [2, 2] } },
    { "id": "power-lines", "type": "line", "source": "bee-field", "source-layer": "field_infrastructure", "filter": ["==", ["get", "class"], "power"], "paint": { "line-color": "#8f5bb5", "line-width": 1.5, "line-dasharray": [3, 2] } },
    { "id": "cutlines", "type": "line", "source": "bee-field", "source-layer": "field_infrastructure", "filter": ["==", ["get", "class"], "cutline"], "paint": { "line-color": "#d85d3c", "line-width": 2.0, "line-dasharray": [1, 1] } },
    { "id": "buildings", "type": "fill", "source": "bee-field", "source-layer": "building", "paint": { "fill-color": "#c49a78", "fill-opacity": 0.72 } },
    { "id": "place-labels", "type": "symbol", "source": "bee-field", "source-layer": "place", "minzoom": 8, "maxzoom": 20, "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 14 }, "paint": { "text-color": "#3b3028", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "water-labels", "type": "symbol", "source": "bee-field", "source-layer": "water", "minzoom": 10, "maxzoom": 20, "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 13 }, "paint": { "text-color": "#1d5b91", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "waterway-labels", "type": "symbol", "source": "bee-field", "source-layer": "waterway", "minzoom": 11, "maxzoom": 20, "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 }, "paint": { "text-color": "#1d5b91", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "road-labels", "type": "symbol", "source": "bee-field", "source-layer": "transportation", "minzoom": 12, "maxzoom": 20, "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 }, "paint": { "text-color": "#5b4130", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()

private const val OSM_STANDARD_EVALUATION_STYLE = """
{
  "version": 8,
  "name": "OpenStreetMap Standard evaluation",
  "sources": {
    "openstreetmap-standard": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "minzoom": 0,
      "maxzoom": 19,
      "attribution": "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"
    },
    "test-areas": {
      "type": "geojson",
      "data": $TEST_AREA_GEOJSON
    }
  },
  "layers": [
    {
      "id": "openstreetmap-standard",
      "type": "raster",
      "source": "openstreetmap-standard"
    },
    { "id": "test-areas-fill", "type": "fill", "source": "test-areas", "paint": { "fill-color": ["get", "color"], "fill-opacity": 0.08 } },
    { "id": "test-areas-outline", "type": "line", "source": "test-areas", "paint": { "line-color": ["get", "color"], "line-width": 2.5, "line-opacity": 0.95 } },
    { "id": "test-areas-label", "type": "symbol", "source": "test-areas", "layout": { "text-field": ["get", "label"], "text-size": 14, "text-font": ["Open Sans Semibold"], "text-allow-overlap": true }, "paint": { "text-color": ["get", "color"], "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
"""
