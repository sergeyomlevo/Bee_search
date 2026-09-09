package org.beesearch.app.ui.map

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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
): List<MapCoverageFragment> = fragments + MapCoverageFragment(normalizeMapPackageBounds(bounds))

/** PMTiles v3 stores its header bbox in signed integers at 10^-7 degree precision. */
internal fun normalizeMapPackageBounds(bounds: MapGeoBounds): MapGeoBounds = MapGeoBounds(
    north = bounds.north.outwardCoordinate(RoundingMode.CEILING),
    east = bounds.east.outwardCoordinate(RoundingMode.CEILING),
    south = bounds.south.outwardCoordinate(RoundingMode.FLOOR),
    west = bounds.west.outwardCoordinate(RoundingMode.FLOOR),
)

internal fun formatMapPackageBuilderBounds(bounds: MapGeoBounds): String {
    fun coordinate(value: Double): String = "%.7f".format(java.util.Locale.ROOT, value)
    return "-West ${coordinate(bounds.west)} -South ${coordinate(bounds.south)} " +
        "-East ${coordinate(bounds.east)} -North ${coordinate(bounds.north)}"
}

private fun Double.outwardCoordinate(roundingMode: RoundingMode): Double =
    BigDecimal.valueOf(this).setScale(7, roundingMode).toDouble()

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

/** Geographic metrics for a map rectangle; this is not a Territory or persistence identity. */
internal data class MapAreaBoundsSummary(
    val bounds: MapGeoBounds,
    val widthKm: Double,
    val heightKm: Double,
    val areaKm2: Double,
)

/**
 * Metrics for one geographic rectangle. This is map-selection UI data only;
 * it does not turn a coverage fragment into a Territory or Room entity.
 */
internal fun coverageBoundsSummary(bounds: MapGeoBounds): MapAreaBoundsSummary {
    val centerLatitude = (bounds.north + bounds.south) / 2.0
    val centerLongitude = (bounds.east + bounds.west) / 2.0
    val widthKm = haversineKm(
        latitudeA = centerLatitude,
        longitudeA = bounds.west,
        latitudeB = centerLatitude,
        longitudeB = bounds.east,
    )
    val heightKm = haversineKm(
        latitudeA = bounds.south,
        longitudeA = centerLongitude,
        latitudeB = bounds.north,
        longitudeB = centerLongitude,
    )
    return MapAreaBoundsSummary(
        bounds = bounds,
        widthKm = widthKm,
        heightKm = heightKm,
        areaKm2 = widthKm * heightKm,
    )
}

internal fun benchmarkBoundsSummary(
    fragments: List<MapCoverageFragment>,
): MapAreaBoundsSummary? {
    val bounds = coverageBoundsForShowAll(fragments) ?: return null
    return coverageBoundsSummary(bounds)
}

private fun haversineKm(
    latitudeA: Double,
    longitudeA: Double,
    latitudeB: Double,
    longitudeB: Double,
): Double {
    val latitudeDelta = Math.toRadians(latitudeB - latitudeA)
    val longitudeDelta = Math.toRadians(longitudeB - longitudeA)
    val latitudeARadians = Math.toRadians(latitudeA)
    val latitudeBRadians = Math.toRadians(latitudeB)
    val haversine = sin(latitudeDelta / 2.0).let { it * it } +
        cos(latitudeARadians) * cos(latitudeBRadians) * sin(longitudeDelta / 2.0).let { it * it }
    return 6_371.0088 * 2.0 * asin(sqrt(haversine))
}
