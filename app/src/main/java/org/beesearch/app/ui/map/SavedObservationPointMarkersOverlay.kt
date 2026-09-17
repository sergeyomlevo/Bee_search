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
import kotlin.math.roundToInt
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.ObservationPointSummary
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
internal fun SavedObservationPointMarkersOverlay(
    points: List<ObservationPointSummary>,
    map: MapLibreMap?,
    mapView: MapView?,
    cameraRevision: Int,
    onSelectPoint: (java.util.UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read to intentionally reproject markers after every camera update.
    @Suppress("UNUSED_EXPRESSION")
    cameraRevision
    val mapInstance = map ?: return
    val view = mapView ?: return
    if (view.width <= 0 || view.height <= 0) return

    Box(modifier) {
        points.forEach { point ->
            val projected = mapInstance.projection.toScreenLocation(
                LatLng(point.latitude, point.longitude),
            )
            if (projected.x in 0f..view.width.toFloat() && projected.y in 0f..view.height.toFloat()) {
                SavedObservationPointMarker(
                    point = point,
                    onClick = { onSelectPoint(point.id) },
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
internal fun SavedObservationPointMarker(
    point: ObservationPointSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resultLabel = when (point.beePresenceResult) {
        BeePresenceResult.BEES_FOUND -> "пчёлы найдены"
        BeePresenceResult.NO_BEES_FOUND -> "пчёлы отсутствуют"
        null -> "результат не зафиксирован"
    }
    val color = when (point.beePresenceResult) {
        BeePresenceResult.BEES_FOUND -> Color(0xFF1B5E20)
        BeePresenceResult.NO_BEES_FOUND -> Color(0xFFC62828)
        null -> Color(0xFFF9A825)
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Точка ${point.pointNumber}, $resultLabel"
            }
            .testTag("point-marker-${point.id}")
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
