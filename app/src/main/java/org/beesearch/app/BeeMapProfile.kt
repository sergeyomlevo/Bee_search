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

/** Developer-only local raster profile used by the temporary device PoC. */
internal fun beeSearchLocalCyclOSMMapProfile(context: Context): BeeMapProfile {
    val tileRoot = File(context.filesDir, "map-poc/cyclosm-v1")
        .absolutePath
        .replace('\\', '/')
    val tileTemplate = "file://$tileRoot/{z}/{x}/{y}.png"
    return BeeMapProfile(
        profileId = "cyclosm-local-poc",
        profileVersion = "temporary-v1",
        datasetVersion = "CyclOSM local raster PoC",
        styleVersion = "raster-v1",
        styleJson = localRasterStyle(tileTemplate),
        sourceMaxZoom = 18.0,
        uiMaxZoom = 20.0,
    )
}

/** Developer-only local vector PMTiles profile for the forest-cutlines PoC. */
internal fun beeSearchLocalForestPmtilesMapProfile(context: Context): BeeMapProfile {
    val archivePath = File(context.filesDir, "map-poc/forest-cutlines-v1.pmtiles")
        .absolutePath
        .replace('\\', '/')
    return BeeMapProfile(
        profileId = "forest-cutlines-pmtiles-poc",
        profileVersion = "temporary-v1",
        datasetVersion = "forest-cutlines-v1",
        styleVersion = "vector-pmtiles-v1",
        styleJson = localVectorPmtilesStyle("pmtiles://file://$archivePath", "Forest cutlines local vector PMTiles PoC"),
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}

/** Developer-only local vector PMTiles profile for the Sapunovo fields/water PoC. */
internal fun beeSearchLocalSapunovoPmtilesMapProfile(context: Context): BeeMapProfile {
    val archivePath = File(context.filesDir, "map-poc/sapunovo-fields-water-v1.pmtiles")
        .absolutePath
        .replace('\\', '/')
    return BeeMapProfile(
        profileId = "sapunovo-fields-water-pmtiles-poc",
        profileVersion = "temporary-v1",
        datasetVersion = "sapunovo-fields-water-v1",
        styleVersion = "vector-pmtiles-v1",
        styleJson = localVectorPmtilesStyle("pmtiles://file://$archivePath", "Sapunovo fields and water local vector PMTiles PoC"),
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}

/** Developer-only production-size local vector PMTiles benchmark. */
internal fun beeSearchLocalTerritoryBenchmarkPmtilesMapProfile(context: Context): BeeMapProfile {
    val archivePath = File(context.filesDir, "map-poc/territory-benchmark-v1.pmtiles")
        .absolutePath
        .replace('\\', '/')
    return BeeMapProfile(
        profileId = "territory-benchmark-pmtiles-poc",
        profileVersion = "temporary-v1",
        datasetVersion = "territory-benchmark-v1",
        styleVersion = "vector-pmtiles-v1",
        styleJson = localVectorPmtilesStyle("pmtiles://file://$archivePath", "Territory benchmark local vector PMTiles PoC"),
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}

/**
 * The only normal-user local-vector profile: a D065-validated package that is
 * active for the current Territory. Fixture and diagnostic profiles remain
 * development-only and are never selected by this function.
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

/** Developer-only minimal source diagnostic, intentionally omitting labels and glyphs. */
internal fun beeSearchLocalPmtilesDiagnosticProfile(context: Context, area: String): BeeMapProfile {
    val fileName = when (area) {
        "forest" -> "forest-cutlines-v1.pmtiles"
        "sapunovo" -> "sapunovo-fields-water-v1.pmtiles"
        else -> error("Unknown diagnostic area: $area")
    }
    val archivePath = File(context.filesDir, "map-poc/$fileName")
        .absolutePath
        .replace('\\', '/')
    return BeeMapProfile(
        profileId = "$area-pmtiles-diagnostic",
        profileVersion = "diagnostic-v1",
        datasetVersion = fileName.removeSuffix(".pmtiles"),
        styleVersion = "minimal-vector-source-v1",
        styleJson = minimalPmtilesDiagnosticStyle("pmtiles://file://$archivePath", "${area.uppercase()} PMTiles diagnostic"),
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}

internal fun beeSearchLocalSapunovoDiagnosticProfile(context: Context): BeeMapProfile =
    beeSearchLocalPmtilesDiagnosticProfile(context, "sapunovo")

internal fun beeSearchLocalSapunovoGlyphDiagnosticProfile(context: Context, glyphsUrl: String): BeeMapProfile {
    val base = beeSearchLocalPmtilesDiagnosticProfile(context, "sapunovo")
    val style = base.styleJson.replace(
        "\"name\":\"SAPUNOVO PMTiles diagnostic\",",
        "\"name\":\"SAPUNOVO glyph diagnostic\",\"glyphs\":\"$glyphsUrl\",",
    )
    return base.copy(profileId = "sapunovo-glyph-diagnostic", styleVersion = "glyph-diagnostic-v1", styleJson = style)
}

internal fun beeSearchLocalSapunovoLabelDiagnosticProfile(
    context: Context,
    fontStack: String = "Noto Sans Regular",
): BeeMapProfile {
    val archivePath = File(context.filesDir, "map-poc/sapunovo-fields-water-v1.pmtiles")
        .absolutePath
        .replace('\\', '/')
    // A GeoJSON point makes TEST independent from PMTiles feature selection,
    // line placement, and collision with real map labels.
    val diagnosticPoint = """
        {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[42.6653,56.1068]}}
    """.trimIndent()
    return BeeMapProfile(
        profileId = "sapunovo-label-diagnostic",
        profileVersion = "diagnostic-v1",
        datasetVersion = "sapunovo-fields-water-v1",
        styleVersion = "label-diagnostic-v2",
        styleJson = """
            {
              "version": 8,
              "name": "SAPUNOVO label diagnostic",
              "glyphs": "asset://map-poc/glyphs/{fontstack}/{range}.pbf",
              "sources": {
                "bee-field": { "type": "vector", "url": "pmtiles://file://$archivePath" },
                "diagnostic-point": { "type": "geojson", "data": $diagnosticPoint }
              },
              "layers": [
                { "id": "background", "type": "background", "paint": { "background-color": "#f3efdf" } },
                { "id": "diagnostic-landcover", "type": "fill", "source": "bee-field", "source-layer": "landcover", "paint": { "fill-color": "#78a86b", "fill-opacity": 0.8 } },
                { "id": "diagnostic-transportation", "type": "line", "source": "bee-field", "source-layer": "transportation", "paint": { "line-color": "#9a5531", "line-width": 2.0 } },
                { "id": "diagnostic-place", "type": "symbol", "source": "bee-field", "source-layer": "place", "minzoom": 8, "maxzoom": 20, "layout": { "visibility": "visible", "text-field": ["get", "name"], "text-font": ["$fontStack"], "text-size": 14 }, "paint": { "text-color": "#3b3028", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } },
                { "id": "diagnostic-place-fixed", "type": "symbol", "source": "bee-field", "source-layer": "place", "minzoom": 8, "maxzoom": 20, "layout": { "visibility": "visible", "text-field": "PLACE", "text-font": ["$fontStack"], "text-size": 18, "text-allow-overlap": true, "text-ignore-placement": true }, "paint": { "text-color": "#2a6f39", "text-halo-color": "#ffffff", "text-halo-width": 2.0 } },
                { "id": "diagnostic-test", "type": "symbol", "source": "diagnostic-point", "minzoom": 0, "maxzoom": 20, "layout": { "visibility": "visible", "text-field": "TEST", "text-font": ["$fontStack"], "text-size": 20, "text-anchor": "center", "text-allow-overlap": true, "text-ignore-placement": true }, "paint": { "text-color": "#d02020", "text-opacity": 1.0, "text-halo-color": "#ffffff", "text-halo-width": 2.0 } }
              ]
            }
        """.trimIndent(),
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}

private fun localRasterStyle(tileTemplate: String): String = """
{
  "version": 8,
  "name": "CyclOSM local raster PoC",
  "sources": {
    "cyclosm-local": {
      "type": "raster",
      "tiles": ["$tileTemplate"],
      "tileSize": 256,
      "scheme": "xyz",
      "minzoom": 16,
      "maxzoom": 18,
      "attribution": "CyclOSM / OpenStreetMap contributors"
    },
    "test-areas": {
      "type": "geojson",
      "data": $TEST_AREA_GEOJSON
    },
  },
  "layers": [
    {
      "id": "cyclosm-local",
      "type": "raster",
      "source": "cyclosm-local"
    },
    { "id": "test-areas-fill", "type": "fill", "source": "test-areas", "paint": { "fill-color": ["get", "color"], "fill-opacity": 0.08 } },
    { "id": "test-areas-outline", "type": "line", "source": "test-areas", "paint": { "line-color": ["get", "color"], "line-width": 2.5, "line-opacity": 0.95 } },
    { "id": "test-areas-label", "type": "symbol", "source": "test-areas", "layout": { "text-field": ["get", "label"], "text-size": 14, "text-font": ["Open Sans Semibold"], "text-allow-overlap": true }, "paint": { "text-color": ["get", "color"], "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()

private fun localVectorPmtilesStyle(archiveUrl: String, styleName: String): String = """
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

private fun minimalPmtilesDiagnosticStyle(archiveUrl: String, styleName: String): String = """
{
  "version": 8,
  "name": "$styleName",
  "sources": { "bee-field": { "type": "vector", "url": "$archiveUrl" } },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f3efdf" } },
    { "id": "diagnostic-landcover", "type": "fill", "source": "bee-field", "source-layer": "landcover", "paint": { "fill-color": "#78a86b", "fill-opacity": 0.8 } },
    { "id": "diagnostic-transportation", "type": "line", "source": "bee-field", "source-layer": "transportation", "paint": { "line-color": "#9a5531", "line-width": 2.0 } }
  ]
}
""".trimIndent()

internal fun beeSearchLocalPmtilesDiagnosticStageProfile(context: Context, area: String, stage: Int): BeeMapProfile {
    val base = beeSearchLocalPmtilesDiagnosticProfile(context, area)
    val additions = listOf(
        "{\"id\":\"water\",\"type\":\"fill\",\"source\":\"bee-field\",\"source-layer\":\"water\",\"paint\":{\"fill-color\":\"#68aee8\"}}",
        "{\"id\":\"waterway\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"waterway\",\"paint\":{\"line-color\":\"#277fc0\",\"line-width\":2}}",
        "{\"id\":\"forest\",\"type\":\"fill\",\"source\":\"bee-field\",\"source-layer\":\"landcover\",\"filter\":[\"in\",[\"get\",\"class\"],[\"literal\",[\"forest\",\"wood\"]]],\"paint\":{\"fill-color\":\"#78a86b\"}}",
        "{\"id\":\"wetland\",\"type\":\"fill\",\"source\":\"bee-field\",\"source-layer\":\"landcover\",\"filter\":[\"==\",[\"get\",\"class\"],\"wetland\"],\"paint\":{\"fill-color\":\"#79b9b0\"}}",
        "{\"id\":\"roads\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"transportation\",\"filter\":[\"in\",[\"get\",\"class\"],[\"literal\",[\"motorway\",\"trunk\",\"primary\",\"secondary\",\"tertiary\",\"unclassified\",\"residential\",\"service\"]]],\"paint\":{\"line-color\":\"#b36b3e\",\"line-width\":2}}",
        "{\"id\":\"tracks\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"transportation\",\"filter\":[\"in\",[\"get\",\"class\"],[\"literal\",[\"track\",\"path\",\"footway\",\"steps\"]]],\"paint\":{\"line-color\":\"#8d6d4f\",\"line-width\":2}}",
        "{\"id\":\"railway\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"transportation\",\"filter\":[\"==\",[\"get\",\"class\"],\"rail\"],\"paint\":{\"line-color\":\"#493e3a\",\"line-width\":2}}",
        "{\"id\":\"power\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"field_infrastructure\",\"filter\":[\"==\",[\"get\",\"class\"],\"power\"],\"paint\":{\"line-color\":\"#8f5bb5\",\"line-width\":2}}",
        "{\"id\":\"cutlines\",\"type\":\"line\",\"source\":\"bee-field\",\"source-layer\":\"field_infrastructure\",\"filter\":[\"==\",[\"get\",\"class\"],\"cutline\"],\"paint\":{\"line-color\":\"#d85d3c\",\"line-width\":2}}",
        "{\"id\":\"buildings\",\"type\":\"fill\",\"source\":\"bee-field\",\"source-layer\":\"building\",\"paint\":{\"fill-color\":\"#c49a78\"}}"
    )
    val selected = additions.take(stage.coerceIn(0, additions.size))
    val selectedJson = if (selected.isEmpty()) "" else ",${selected.joinToString(",")}"
    val fileName = if (area == "forest") "forest-cutlines-v1.pmtiles" else "sapunovo-fields-water-v1.pmtiles"
    val json = """
    {"version":8,"name":"PMTiles diagnostic stage $stage","sources":{"bee-field":{"type":"vector","url":"pmtiles://file://${File(context.filesDir, "map-poc/$fileName").absolutePath.replace('\\','/')}"}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#f3efdf"}},{"id":"landcover","type":"fill","source":"bee-field","source-layer":"landcover","paint":{"fill-color":"#78a86b"}},{"id":"transportation","type":"line","source":"bee-field","source-layer":"transportation","paint":{"line-color":"#9a5531","line-width":2}}$selectedJson]}
    """.trimIndent()
    return base.copy(styleJson = json)
}

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
