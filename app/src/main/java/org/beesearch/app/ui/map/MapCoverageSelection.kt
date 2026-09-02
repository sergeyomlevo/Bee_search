package org.beesearch.app.ui.map

import org.maplibre.android.geometry.LatLngBounds

/**
 * Geographic bounds used only by the current map-screen coverage-selection prototype.
 * They are intentionally not a Territory or Room model.
 */
internal data class MapGeoBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
) {
    init {
        require(north.isFinite() && north in -90.0..90.0)
        require(south.isFinite() && south in -90.0..90.0)
        require(east.isFinite() && east in -180.0..180.0)
        require(west.isFinite() && west in -180.0..180.0)
        require(north >= south)
    }

    fun toLatLngBounds(): LatLngBounds = LatLngBounds.from(north, east, south, west)

    companion object {
        fun fromMapLibre(bounds: LatLngBounds): MapGeoBounds = MapGeoBounds(
            north = bounds.latitudeNorth,
            east = bounds.longitudeEast,
            south = bounds.latitudeSouth,
            west = bounds.longitudeWest,
        )
    }
}

/** A selected rectangle remains an independent geographic fragment. */
internal data class MapCoverageFragment(
    val bounds: MapGeoBounds,
)

/**
 * Camera padding is map-renderer state, not part of the selected geographic coverage.
 * A coverage review may reserve space for its controls; normal map mode must not retain it.
 */
internal data class MapCameraPadding(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

internal fun coverageReviewCameraPadding(
    controlsHeightPx: Int,
    edgePaddingPx: Int,
): MapCameraPadding {
    require(controlsHeightPx >= 0)
    require(edgePaddingPx >= 0)
    return MapCameraPadding(
        left = edgePaddingPx,
        top = edgePaddingPx,
        right = edgePaddingPx,
        bottom = controlsHeightPx + edgePaddingPx,
    )
}

internal fun normalMapCameraPadding(): MapCameraPadding = MapCameraPadding()

internal fun addCoverageFragment(
    fragments: List<MapCoverageFragment>,
    bounds: MapGeoBounds,
): List<MapCoverageFragment> = fragments + MapCoverageFragment(bounds)

internal fun undoLastCoverageFragment(
    fragments: List<MapCoverageFragment>,
): List<MapCoverageFragment> = fragments.dropLast(1)

internal fun clearCoverageFragments(): List<MapCoverageFragment> = emptyList()

/**
 * Bounds used only to frame the camera for review. The selected fragments stay unchanged.
 */
internal fun coverageBoundsForShowAll(
    fragments: List<MapCoverageFragment>,
): MapGeoBounds? {
    if (fragments.isEmpty()) return null
    return MapGeoBounds(
        north = fragments.maxOf { it.bounds.north },
        east = fragments.maxOf { it.bounds.east },
        south = fragments.minOf { it.bounds.south },
        west = fragments.minOf { it.bounds.west },
    )
}
