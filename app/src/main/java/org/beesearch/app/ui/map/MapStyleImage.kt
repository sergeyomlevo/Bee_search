package org.beesearch.app.ui.map

import android.graphics.Bitmap

/**
 * A style-level image shared by overlay layers.
 *
 * The overlay Registry deliberately owns only sources/layers; images are MapLibre Style resources
 * and are installed by [MapLibreOverlaySession] on every loaded style. No generic image manager is
 * introduced.
 */
internal data class MapStyleImage(
    val id: String,
    val create: () -> Bitmap,
)
