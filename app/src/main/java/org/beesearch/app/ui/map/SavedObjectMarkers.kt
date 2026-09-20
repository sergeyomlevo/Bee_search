package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Saved-object map presentation.
 *
 * The browser map draws a list of [MapObjectMarker]. ObservationPoint is the only object kind that
 * exists in the product today, so [MapObjectType] has exactly one entry: adding Hollow, LogHive,
 * Apiary or Trap later means adding an enum entry, a mapper and nothing else — the overlay, the map
 * wiring and the marker rendering already work on the marker list.
 *
 * This is deliberately a presentation type. It is not a domain entity, it is never persisted, and it
 * has no Room representation.
 */
internal enum class MapObjectType {
    OBSERVATION_POINT,
}

/** Marker colour family, derived from the object's own state. */
internal enum class MapObjectTone {
    POSITIVE,
    NEGATIVE,
    UNRESOLVED,
}

internal data class MapObjectMarker(
    val type: MapObjectType,
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    /** Accessibility label of this marker. */
    val label: String,
    val tone: MapObjectTone,
)

internal fun observationPointMarkers(points: List<ObservationPointSummary>): List<MapObjectMarker> =
    points.map { point ->
        MapObjectMarker(
            type = MapObjectType.OBSERVATION_POINT,
            id = point.id,
            latitude = point.latitude,
            longitude = point.longitude,
            label = "Точка ${point.pointNumber}, ${presenceLabel(point.beePresenceResult)}",
            tone = when (point.beePresenceResult) {
                BeePresenceResult.BEES_FOUND -> MapObjectTone.POSITIVE
                BeePresenceResult.NO_BEES_FOUND -> MapObjectTone.NEGATIVE
                null -> MapObjectTone.UNRESOLVED
            },
        )
    }

private fun presenceLabel(result: BeePresenceResult?): String = when (result) {
    BeePresenceResult.BEES_FOUND -> "пчёлы найдены"
    BeePresenceResult.NO_BEES_FOUND -> "пчёлы отсутствуют"
    null -> "результат не зафиксирован"
}

@Composable
internal fun SavedObjectMarkersOverlay(
    markers: List<MapObjectMarker>,
    map: MapLibreMap?,
    mapView: MapView?,
    cameraRevision: Int,
    onSelectMarker: (MapObjectMarker) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read to intentionally reproject markers after every camera update.
    @Suppress("UNUSED_EXPRESSION")
    cameraRevision
    val mapInstance = map ?: return
    val view = mapView ?: return
    if (view.width <= 0 || view.height <= 0) return

    Box(modifier) {
        markers.forEach { marker ->
            val projected = mapInstance.projection.toScreenLocation(
                LatLng(marker.latitude, marker.longitude),
            )
            if (projected.x in 0f..view.width.toFloat() && projected.y in 0f..view.height.toFloat()) {
                SavedObjectMarker(
                    marker = marker,
                    onClick = { onSelectMarker(marker) },
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (projected.x - 24.dp.toPx()).roundToInt(),
                            y = (projected.y - 24.dp.toPx()).roundToInt(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SavedObjectMarker(
    marker: MapObjectMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when (marker.tone) {
        MapObjectTone.POSITIVE -> Color(0xFF1B5E20)
        MapObjectTone.NEGATIVE -> Color(0xFFC62828)
        MapObjectTone.UNRESOLVED -> Color(0xFFF9A825)
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = marker.label
            }
            .testTag(markerTestTag(marker))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            drawCircle(color = Color.White, radius = size.minDimension / 2f)
            drawCircle(color = color, radius = size.minDimension * 0.38f)
            drawCircle(
                color = Color(0xFF212121),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

/** Stable test tag per object kind; new kinds add their own prefix here. */
internal fun markerTestTag(marker: MapObjectMarker): String = when (marker.type) {
    MapObjectType.OBSERVATION_POINT -> "point-marker-${marker.id}"
}
