package org.beesearch.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val MAP_CENTER_TARGET_DESCRIPTION =
    "Красная точка — выбранный центр карты"
internal const val MAP_CENTER_TARGET_TAG = "map-center-target"
internal const val MAP_GPS_MARKER_DESCRIPTION =
    "Синяя точка — текущая GPS-позиция"
internal const val MAP_GPS_MARKER_TAG = "map-gps-marker"

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
