package org.beesearch.app

import java.net.URI

internal data class BeeMapProfile(
    val profileId: String,
    val profileVersion: String,
    val datasetVersion: String,
    val styleVersion: String,
    val styleUrl: String,
    val sourceMaxZoom: Double,
    val uiMaxZoom: Double,
)

internal fun beeSearchFieldMapProfile(styleUrl: String = BuildConfig.BEE_MAP_STYLE_URL): BeeMapProfile {
    val uri = URI(styleUrl)
    require(uri.scheme == "https" || uri.scheme == "http") { "Map style URL must use HTTP(S)" }
    require(!uri.host.isNullOrBlank()) { "Map style URL must have a host" }
    require(uri.path.endsWith("/style.json")) { "Map style URL must identify a versioned style.json" }

    return BeeMapProfile(
        profileId = "bee-search-field",
        profileVersion = "v1",
        datasetVersion = "central-russia-poc-20260830",
        styleVersion = "style-v3",
        styleUrl = styleUrl,
        sourceMaxZoom = 15.0,
        uiMaxZoom = 20.0,
    )
}
