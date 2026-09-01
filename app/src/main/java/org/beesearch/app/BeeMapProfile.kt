package org.beesearch.app

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
    }
  },
  "layers": [
    {
      "id": "openstreetmap-standard",
      "type": "raster",
      "source": "openstreetmap-standard"
    }
  ]
}
"""
