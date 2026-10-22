package org.beesearch.app.ui.userpoints

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.ui.map.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.Source

internal const val USER_POINTS_OVERLAY_KEY = "user-points"

/** Immutable, current-Territory snapshot; native resources are created afresh by the shared Registry. */
internal fun userPointOverlay(state: UserPointDisplayState): MapOverlay<Source, Layer>? {
    val visible = state.points.filter { it.territoryId == state.territoryId && it.isVisible }
    if (visible.isEmpty()) return null
    val json = visible.joinToString(prefix = "{\"type\":\"FeatureCollection\",\"features\":[", postfix = "]}") { point ->
        "{\"type\":\"Feature\",\"properties\":{\"id\":\"${point.id}\",\"selected\":${point.id == state.selectedId}}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[${point.longitude},${point.latitude}]}}"
    }
    return MapOverlay(key = USER_POINTS_OVERLAY_KEY, revision = state.revision, order = 10,
        sources = listOf(OverlaySourceFactory("points") { GeoJsonSource(it, json) }),
        layers = listOf(
            OverlayLayerFactory("markers", "points") { layer, source ->
                SymbolLayer(layer, source)
                    .withProperties(
                        iconImage(USER_POINT_TRIANGLE_IMAGE),
                        iconSize(1f),
                        iconAnchor(Property.ICON_ANCHOR_CENTER),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                        iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    )
            },
        ))
}

/** Same projected 48dp interaction boundary as SavedObjectMarkersOverlay; rendering belongs to Registry. */
@Composable
internal fun UserPointMarkerTargets(
    points: List<UserPoint>, map: MapLibreMap?, mapView: MapView?, cameraRevision: Int,
    selectedId: java.util.UUID?, onOpen: (UserPoint) -> Unit, modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") cameraRevision
    val nativeMap = map ?: return
    val view = mapView ?: return
    if (view.width <= 0 || view.height <= 0) return
    Box(modifier) {
        points.filter { it.isVisible }.forEach { point ->
            val screen = nativeMap.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
            if (screen.x in 0f..view.width.toFloat() && screen.y in 0f..view.height.toFloat()) {
                Box(Modifier.offset { IntOffset((screen.x - 24.dp.toPx()).roundToInt(), (screen.y - 24.dp.toPx()).roundToInt()) }
                    .size(48.dp).semantics { contentDescription = "Моя точка: ${point.title}"; selected = point.id == selectedId }
                    .testTag("user-point-marker-${point.id}").clickable(role = Role.Button) { onOpen(point) })
            }
        }
    }
}
