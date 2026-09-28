package org.beesearch.app

import android.content.Context
import java.io.File

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
 * Temporary developer-only basemaps.
 *
 * The debug build type is the only debuggable variant of this project (`beta` and
 * `release` both set `isDebuggable = false`), so this gate is true exactly for
 * `org.beesearch.app.dev` and false for Beta and Stable. It guards both the extra
 * selector item and the profile itself, so no other build can show or select them.
 */
internal val devMapBasemapsEnabled: Boolean = BuildConfig.DEBUG

/** The staged DEV Sentinel-2 archive, or null when it is not present on this device. */
internal fun devSentinelArchive(context: Context): File? {
    val root = context.getExternalFilesDir(null) ?: return null
    return File(root, "poc-sentinel/sentinel-area-z10-13.pmtiles").takeIf { it.isFile }
}

/**
 * Temporary DEV-only raster profile for the whole-area Sentinel-2 offline package
 * (real levels z10..z13, source-direct "variant B" rendering). The package is a fixed
 * local file: outside the package bounds only the background colour is drawn.
 *
 * Zoom is capped at the package maxzoom: above z13 the imagery only blurs, so the camera
 * is not allowed to go there (owner decision).
 */
internal fun beeSearchDevSentinelMapProfile(archive: File): BeeMapProfile {
    return BeeMapProfile(
        profileId = "sentinel-area-raster-dev",
        profileVersion = "temporary-v1",
        datasetVersion = "Sentinel-2 L2A 2026-07-18 raster z10-z13",
        styleVersion = "raster-pmtiles-v1",
        styleJson = devSentinelRasterStyle(archiveUrl(archive)),
        sourceMaxZoom = 13.0,
        uiMaxZoom = 13.0,
    )
}

/**
 * Minimal raster style for the DEV Sentinel-2 package: one PMTiles raster source over a
 * light neutral background, plus the same test-area markers the other profiles carry so
 * the developer can see the overlays stay aligned on top of the imagery.
 */
private fun devSentinelRasterStyle(archiveUrl: String): String = """
{
  "version": 8,
  "name": "Sentinel-2 whole-area raster DEV",
  "sources": {
    "sentinel-area": {
      "type": "raster",
      "url": "$archiveUrl",
      "tileSize": 256,
      "minzoom": 10,
      "maxzoom": 13,
      "attribution": "Contains modified Copernicus Sentinel data 2026"
    },
    "test-areas": {
      "type": "geojson",
      "data": $TEST_AREA_GEOJSON
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f0f0f0" } },
    { "id": "sentinel-area-raster", "type": "raster", "source": "sentinel-area", "paint": { "raster-resampling": "linear" } },
    { "id": "test-areas-fill", "type": "fill", "source": "test-areas", "paint": { "fill-color": ["get", "color"], "fill-opacity": 0.08 } },
    { "id": "test-areas-outline", "type": "line", "source": "test-areas", "paint": { "line-color": ["get", "color"], "line-width": 2.5, "line-opacity": 0.95 } },
    { "id": "test-areas-label", "type": "symbol", "source": "test-areas", "layout": { "text-field": ["get", "label"], "text-size": 14, "text-font": ["Open Sans Semibold"], "text-allow-overlap": true }, "paint": { "text-color": ["get", "color"], "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()

/** Local PMTiles archive URL in the form the proven raster/vector sources use. */
private fun archiveUrl(archive: File): String =
    "pmtiles://file://" + archive.absolutePath.replace('\\', '/')

/**
 * Temporary DEV-only hybrid profile: the accepted Sentinel-2 package as the visual base with
 * the selected layers of the existing offline vector package drawn on top. The two packages
 * stay independent sources, so a future high-resolution raster can replace the Sentinel one
 * without touching the vector overlay, and the vector overlay can change without re-rendering
 * any imagery.
 *
 * Deliberately NOT drawn: the vector background and the large vector fills (landcover, forest,
 * wetland, water, buildings), so the imagery stays readable. Zoom matches the raster package.
 */
internal fun beeSearchDevHybridMapProfile(sentinelArchive: File, vectorArchive: File): BeeMapProfile {
    return BeeMapProfile(
        profileId = "sentinel-vector-hybrid-dev",
        profileVersion = "temporary-v1",
        datasetVersion = "Sentinel-2 2026-07-18 + offline vector overlay",
        styleVersion = "raster-vector-hybrid-v1",
        styleJson = devHybridStyle(archiveUrl(sentinelArchive), archiveUrl(vectorArchive)),
        sourceMaxZoom = 13.0,
        uiMaxZoom = 13.0,
    )
}

/**
 * The vector line and symbol layers that carry orientation value over imagery: water lines,
 * roads, tracks, railways and the existing labels. Shared verbatim with the offline-vector
 * style above, so the hybrid overlay can never drift away from the vector map it comes from.
 */
private const val VECTOR_OVERLAY_LINE_LAYERS = """    { "id": "waterways", "type": "line", "source": "bee-field", "source-layer": "waterway", "paint": { "line-color": "#277fc0", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1.0, 15, 3.0] } },
    { "id": "roads", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["in", ["get", "class"], ["literal", ["motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service"]]], "paint": { "line-color": "#b36b3e", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.8, 15, 4.0] } },
    { "id": "tracks", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["in", ["get", "class"], ["literal", ["track", "path", "footway", "steps"]]], "paint": { "line-color": "#8d6d4f", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 15, 2.5], "line-dasharray": [2, 1] } },
    { "id": "railway", "type": "line", "source": "bee-field", "source-layer": "transportation", "filter": ["==", ["get", "class"], "rail"], "paint": { "line-color": "#493e3a", "line-width": 2.0, "line-dasharray": [2, 2] } }"""

private const val VECTOR_OVERLAY_LABEL_LAYERS = """    { "id": "place-labels", "type": "symbol", "source": "bee-field", "source-layer": "place", "minzoom": 8, "maxzoom": 20, "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 14 }, "paint": { "text-color": "#3b3028", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "water-labels", "type": "symbol", "source": "bee-field", "source-layer": "water", "minzoom": 10, "maxzoom": 20, "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 13 }, "paint": { "text-color": "#1d5b91", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "waterway-labels", "type": "symbol", "source": "bee-field", "source-layer": "waterway", "minzoom": 11, "maxzoom": 20, "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 }, "paint": { "text-color": "#1d5b91", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
    { "id": "road-labels", "type": "symbol", "source": "bee-field", "source-layer": "transportation", "minzoom": 12, "maxzoom": 20, "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 }, "paint": { "text-color": "#5b4130", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }"""

/**
 * Layer order is what makes the hybrid work: background, imagery, vector lines, vector labels,
 * then the developer test-area markers. Both tile sources stay independent of each other.
 */
private fun devHybridStyle(sentinelUrl: String, vectorUrl: String): String = """
{
  "version": 8,
  "name": "Sentinel-2 with offline vector overlay DEV",
  "glyphs": "asset://map-poc/glyphs/{fontstack}/{range}.pbf",
  "sources": {
    "sentinel-area": {
      "type": "raster",
      "url": "$sentinelUrl",
      "tileSize": 256,
      "minzoom": 10,
      "maxzoom": 13,
      "attribution": "Contains modified Copernicus Sentinel data 2026"
    },
    "bee-field": {
      "type": "vector",
      "url": "$vectorUrl",
      "attribution": "© OpenStreetMap contributors"
    },
    "test-areas": {
      "type": "geojson",
      "data": $TEST_AREA_GEOJSON
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f0f0f0" } },
    { "id": "sentinel-area-raster", "type": "raster", "source": "sentinel-area", "paint": { "raster-resampling": "linear" } },
$VECTOR_OVERLAY_LINE_LAYERS,
$VECTOR_OVERLAY_LABEL_LAYERS,
    { "id": "test-areas-fill", "type": "fill", "source": "test-areas", "paint": { "fill-color": ["get", "color"], "fill-opacity": 0.08 } },
    { "id": "test-areas-outline", "type": "line", "source": "test-areas", "paint": { "line-color": ["get", "color"], "line-width": 2.5, "line-opacity": 0.95 } },
    { "id": "test-areas-label", "type": "symbol", "source": "test-areas", "layout": { "text-field": ["get", "label"], "text-size": 14, "text-font": ["Open Sans Semibold"], "text-allow-overlap": true }, "paint": { "text-color": ["get", "color"], "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()

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
    $VECTOR_OVERLAY_LINE_LAYERS,
    { "id": "power-lines", "type": "line", "source": "bee-field", "source-layer": "field_infrastructure", "filter": ["==", ["get", "class"], "power"], "paint": { "line-color": "#8f5bb5", "line-width": 1.5, "line-dasharray": [3, 2] } },
    { "id": "cutlines", "type": "line", "source": "bee-field", "source-layer": "field_infrastructure", "filter": ["==", ["get", "class"], "cutline"], "paint": { "line-color": "#d85d3c", "line-width": 2.0, "line-dasharray": [1, 1] } },
    { "id": "buildings", "type": "fill", "source": "bee-field", "source-layer": "building", "paint": { "fill-color": "#c49a78", "fill-opacity": 0.72 } },
    $VECTOR_OVERLAY_LABEL_LAYERS
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
