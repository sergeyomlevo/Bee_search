package org.beesearch.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.min

internal const val MAP_CENTER_TARGET_DESCRIPTION =
    "Красная точка — выбранный центр карты"
internal const val MAP_CENTER_TARGET_TAG = "map-center-target"
internal const val MAP_GPS_MARKER_DESCRIPTION =
    "Синяя точка — текущая GPS-позиция"
internal const val MAP_GPS_MARKER_TAG = "map-gps-marker"
internal const val MAP_GPS_TARGET_GUIDE_TAG = "map-gps-target-guide"

@Composable
internal fun MapGpsToTargetGuide(
    gpsScreenPosition: Offset,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize().testTag(MAP_GPS_TARGET_GUIDE_TAG)) {
        val target = center
        val endpoint = clipDirectionEndpointToViewport(
            target = target,
            gps = gpsScreenPosition,
            viewportWidth = size.width,
            viewportHeight = size.height,
        )
        if (endpoint != target) {
            val dashEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(7.dp.toPx(), 6.dp.toPx()),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.82f),
                start = target,
                end = endpoint,
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = dashEffect,
            )
            drawLine(
                color = Color(0xFF18212B).copy(alpha = 0.92f),
                start = target,
                end = endpoint,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = dashEffect,
            )
        }
    }
}

/**
 * Keeps the direction guide visible when the GPS marker is outside the viewport.
 *
 * [gps] is the MapLibre projection in the same screen coordinate system as the map canvas.
 * The returned point is either the visible GPS position or the intersection of the target-to-GPS
 * ray with the viewport edge.
 */
internal fun clipDirectionEndpointToViewport(
    target: Offset,
    gps: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return target
    if (gps.x in 0f..viewportWidth && gps.y in 0f..viewportHeight) return gps

    val dx = gps.x - target.x
    val dy = gps.y - target.y
    if (dx == 0f && dy == 0f) return target

    var scale = 1f
    if (dx > 0f) scale = min(scale, (viewportWidth - target.x) / dx)
    if (dx < 0f) scale = min(scale, (0f - target.x) / dx)
    if (dy > 0f) scale = min(scale, (viewportHeight - target.y) / dy)
    if (dy < 0f) scale = min(scale, (0f - target.y) / dy)
    return Offset(
        x = (target.x + dx * scale).coerceIn(0f, viewportWidth),
        y = (target.y + dy * scale).coerceIn(0f, viewportHeight),
    )
}

@Composable
internal fun MapGpsMarker(
    screenPosition: Offset,
    modifier: Modifier = Modifier,
) {
    val halfSizePx = with(LocalDensity.current) { 9.dp.toPx() }
    Canvas(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (screenPosition.x - halfSizePx).roundToInt(),
                    y = (screenPosition.y - halfSizePx).roundToInt(),
                )
            }
            .size(18.dp)
            .testTag(MAP_GPS_MARKER_TAG)
            .semantics { contentDescription = MAP_GPS_MARKER_DESCRIPTION },
    ) {
        drawCircle(color = Color.White, radius = 8.5.dp.toPx())
        drawCircle(color = Color(0xFF1976D2), radius = 7.dp.toPx())
    }
}

@Composable
internal fun MapCenterTarget(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(12.dp)
            .testTag(MAP_CENTER_TARGET_TAG)
            .semantics { contentDescription = MAP_CENTER_TARGET_DESCRIPTION },
    ) {
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = 5.dp.toPx(),
        )
        drawCircle(
            color = Color(0xFFD32F2F),
            radius = 4.dp.toPx(),
        )
    }
}
