package org.beesearch.app.ui.map

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.maplibre.android.geometry.LatLngBounds

/**
 * Mean Earth radius in kilometres. One constant for every area and distance calculation, so a
 * single участок cannot show two slightly different areas in two places of the interface.
 */
internal const val EARTH_RADIUS_KM = 6_371.0088

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

private fun Double.outwardCoordinate(roundingMode: RoundingMode): Double =
    BigDecimal.valueOf(this).setScale(7, roundingMode).toDouble()

internal fun undoLastCoverageFragment(
    fragments: List<MapCoverageFragment>,
): List<MapCoverageFragment> = fragments.dropLast(1)

internal fun clearCoverageFragments(): List<MapCoverageFragment> = emptyList()

/**
 * A coverage-editing session is dirty only while its working draft differs from the last
 * persisted selection.
 *
 * Opening the editor is not a change: the draft starts as a copy of the persisted selection, so an
 * untouched editor is not dirty and may be left without any confirmation. Every draft operation
 * (add, undo last, clear all) is a pure function of the previous list and never touches the
 * persisted selection, which is what makes "leave without saving" a plain restore.
 */
internal fun isCoverageSelectionDirty(
    persisted: List<MapCoverageFragment>,
    working: List<MapCoverageFragment>,
): Boolean = working != persisted

/**
 * Bounds used only to frame the camera. The fragments themselves stay unchanged.
 */
internal fun coverageBoundsForShowAll(
    fragments: List<MapCoverageFragment>,
): MapGeoBounds? = unionBounds(fragments.map(MapCoverageFragment::bounds))

/**
 * Outer extent of участки: the smallest rectangle containing all of them.
 *
 * This is camera framing only. It is never persisted and never replaces the участки themselves, so
 * an Ареал with a gap between two участки keeps showing both of them.
 */
internal fun unionBounds(bounds: List<MapGeoBounds>): MapGeoBounds? {
    if (bounds.isEmpty()) return null
    return MapGeoBounds(
        north = bounds.maxOf { it.north },
        east = bounds.maxOf { it.east },
        south = bounds.minOf { it.south },
        west = bounds.minOf { it.west },
    )
}

/**
 * Area of the union of участки in square kilometres, computed without any GIS dependency.
 *
 * All latitude and longitude edges are compressed into a grid, every grid cell is either fully
 * inside one of the участки or fully outside it, and a covered cell contributes exactly once. An
 * overlap between two участки is therefore counted once, a участок nested inside another adds
 * nothing, and the empty space between separate участки is not part of the area.
 *
 * A lat/lon cell is measured on the sphere:
 *
 * ```text
 * A = R² × Δλ × (sin φnorth − sin φsouth)
 * ```
 *
 * The result is derived data: it is never stored in DataStore and never written into the Area file.
 */
internal fun areaUnionKm2(bounds: List<MapGeoBounds>): Double {
    if (bounds.isEmpty()) return 0.0
    val latitudes = bounds.flatMap { listOf(it.south, it.north) }.distinct().sorted()
    val longitudes = bounds.flatMap { listOf(it.west, it.east) }.distinct().sorted()
    var total = 0.0
    for (latitudeIndex in 0 until latitudes.size - 1) {
        val south = latitudes[latitudeIndex]
        val north = latitudes[latitudeIndex + 1]
        if (north <= south) continue
        val latitudeBandKm2 = EARTH_RADIUS_KM * EARTH_RADIUS_KM *
            (sin(Math.toRadians(north)) - sin(Math.toRadians(south)))
        for (longitudeIndex in 0 until longitudes.size - 1) {
            val west = longitudes[longitudeIndex]
            val east = longitudes[longitudeIndex + 1]
            if (east <= west) continue
            val covered = bounds.any { bound ->
                bound.south <= south && bound.north >= north &&
                    bound.west <= west && bound.east >= east
            }
            if (covered) total += latitudeBandKm2 * Math.toRadians(east - west)
        }
    }
    return total
}

/** One decimal place, locale-independent: the precision the map screen already uses. */
internal fun formatSquareKilometers(areaKm2: Double): String = "%.1f".format(java.util.Locale.ROOT, areaKm2)

/** What the map shows for the Ареал of the current Territory. */
internal data class MapAreaPresentation(
    /** The участки the map draws, in stored order. */
    val fragments: List<MapCoverageFragment>,

    /** Whether [fragments] are drawn at all in this mode. */
    val drawFragments: Boolean,

    /** The editor's orange frame around the next visible участок: editor only. */
    val showViewportFrame: Boolean,

    /** Whether the участки editor is open. */
    val editorOpen: Boolean,

    /**
     * Whether the camera should frame the whole Ареал. True in the view mode, where the user came to
     * see the area rather than to pan around it.
     */
    val frameWholeArea: Boolean,
)

/**
 * Mode decides what the Ареал looks like on the map.
 *
 * The editor works on the draft and marks the viewport it will add next; the view mode shows exactly
 * the stored участки and frames them; the field map keeps them loaded but draws nothing, because the
 * field workflow is about the current observation, not about the coverage outline.
 */
internal fun mapAreaPresentation(
    mode: BeeMapMode,
    editorOpen: Boolean,
    working: List<MapCoverageFragment>,
    persisted: List<MapCoverageFragment>,
    persistedLoaded: Boolean,
): MapAreaPresentation = when {
    editorOpen -> MapAreaPresentation(
        fragments = working,
        drawFragments = true,
        showViewportFrame = true,
        editorOpen = true,
        frameWholeArea = false,
    )

    mode == BeeMapMode.AREA_VIEW -> MapAreaPresentation(
        fragments = persisted.takeIf { persistedLoaded }.orEmpty(),
        drawFragments = persistedLoaded,
        showViewportFrame = false,
        editorOpen = false,
        frameWholeArea = true,
    )

    else -> MapAreaPresentation(
        fragments = persisted.takeIf { persistedLoaded }.orEmpty(),
        drawFragments = false,
        showViewportFrame = false,
        editorOpen = false,
        frameWholeArea = false,
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
        // The same union helper the Ареал card uses, so one участок never shows two areas.
        areaKm2 = areaUnionKm2(listOf(bounds)),
    )
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
    return EARTH_RADIUS_KM * 2.0 * asin(sqrt(haversine))
}
