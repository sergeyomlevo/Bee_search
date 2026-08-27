package org.beesearch.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal const val OBSERVATION_POINT_CROSSHAIR_DESCRIPTION =
    "Прицел положения точки наблюдения"

@Composable
internal fun ObservationPointCrosshair(modifier: Modifier = Modifier) {
    val crosshairColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .size(72.dp)
            .semantics { contentDescription = OBSERVATION_POINT_CROSSHAIR_DESCRIPTION },
    ) {
        val center = this.center
        val arm = size.minDimension * 0.42f
        val gap = size.minDimension * 0.12f
        val haloWidth = 7.dp.toPx()
        val lineWidth = 3.dp.toPx()

        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.16f,
            center = center,
            style = Stroke(width = haloWidth),
        )
        drawLine(Color.White, center.copy(x = center.x - arm), center.copy(x = center.x - gap), haloWidth)
        drawLine(Color.White, center.copy(x = center.x + gap), center.copy(x = center.x + arm), haloWidth)
        drawLine(Color.White, center.copy(y = center.y - arm), center.copy(y = center.y - gap), haloWidth)
        drawLine(Color.White, center.copy(y = center.y + gap), center.copy(y = center.y + arm), haloWidth)

        drawCircle(
            color = crosshairColor,
            radius = size.minDimension * 0.16f,
            center = center,
            style = Stroke(width = lineWidth),
        )
        drawLine(crosshairColor, center.copy(x = center.x - arm), center.copy(x = center.x - gap), lineWidth)
        drawLine(crosshairColor, center.copy(x = center.x + gap), center.copy(x = center.x + arm), lineWidth)
        drawLine(crosshairColor, center.copy(y = center.y - arm), center.copy(y = center.y - gap), lineWidth)
        drawLine(crosshairColor, center.copy(y = center.y + gap), center.copy(y = center.y + arm), lineWidth)
    }
}
