package org.beesearch.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * The browser map draws a list of [MapObjectMarker]. ObservationPoint is the only object kind with a
 * production map surface today, so [MapObjectType] has exactly one entry: adding Hollow, LogHive,
 * Apiary or Trap means adding an enum entry, a mapper and nothing else — the overlay, the map wiring
 * and the marker rendering already work on the marker list. Hollow and LogHive still have no map
 * screen, and Trap/Apiary have no product capability, so they stay reserved D102 vocabulary.
 *
 * Presentation only: not a domain entity, never persisted, no Room representation. Appearance comes
 * from the approved [ResearchMarkerType] catalogue, so shape, colour and size are never duplicated
 * here; the object's own state travels in [MapObjectMarker.label].
 */
internal enum class MapObjectType {
    OBSERVATION_POINT,
}

/** The approved D102 presentation of this object kind: single source of shape, colour and size. */
internal fun MapObjectType.researchMarkerType(): ResearchMarkerType = when (this) {
    MapObjectType.OBSERVATION_POINT -> ResearchMarkerType.OBSERVATION_POINT
}

internal data class MapObjectMarker(
    val type: MapObjectType,
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    /** Accessibility label of this marker, including the object's own state. */
    val label: String,
)

internal fun observationPointMarkers(points: List<ObservationPointSummary>): List<MapObjectMarker> =
    points.map { point ->
        MapObjectMarker(
            type = MapObjectType.OBSERVATION_POINT,
            id = point.id,
            latitude = point.latitude,
            longitude = point.longitude,
            label = "Точка ${point.pointNumber}, ${presenceLabel(point.beePresenceResult)}",
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
    selectedObjectId: UUID? = null,
) {
    // Read to intentionally reproject markers after every camera update.
    @Suppress("UNUSED_EXPRESSION")
    cameraRevision
    val mapInstance = map ?: return
    val view = mapView ?: return
    if (view.width <= 0 || view.height <= 0) return
    // The approved pin is anchored by its tip, so the recorded position stays under the tip rather
    // than under the middle of the glyph.
    val boxSize = ResearchMarkerCatalog.TOUCH_TARGET_SIZE_DP.dp
    val visualSize = ResearchMarkerCatalog.NORMAL_SIZE_DP.dp
    val tipFromBoxTop = (boxSize - visualSize) / 2 + visualSize * ResearchMarkerCatalog.PIN_TIP_FRACTION

    Box(modifier) {
        markers.forEach { marker ->
            val projected = mapInstance.projection.toScreenLocation(
                LatLng(marker.latitude, marker.longitude),
            )
            if (projected.x in 0f..view.width.toFloat() && projected.y in 0f..view.height.toFloat()) {
                SavedObjectMarker(
                    marker = marker,
                    selected = marker.id == selectedObjectId,
                    onClick = { onSelectMarker(marker) },
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (projected.x - (boxSize / 2).toPx()).roundToInt(),
                            y = (projected.y - tipFromBoxTop.toPx()).roundToInt(),
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
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(ResearchMarkerCatalog.TOUCH_TARGET_SIZE_DP.dp)
            .semantics {
                role = Role.Button
                contentDescription = marker.label
                this.selected = selected
                stateDescription = if (selected) "Выбрано" else "Обычное состояние"
            }
            .testTag(markerTestTag(marker))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The approved D102 marker owns the drawing and the size; this box owns the label, the
        // selection state and the 48 dp target, so the component's own type description is cleared
        // instead of being announced twice for one object.
        Box(Modifier.clearAndSetSemantics {}) {
            ResearchObjectMarker(
                type = marker.type.researchMarkerType(),
                visualSize = ResearchMarkerCatalog.NORMAL_SIZE_DP.dp,
                selected = selected,
            )
        }
    }
}

/** Stable test tag per object kind; new kinds add their own prefix here. */
internal fun markerTestTag(marker: MapObjectMarker): String = when (marker.type) {
    MapObjectType.OBSERVATION_POINT -> "point-marker-${marker.id}"
}
