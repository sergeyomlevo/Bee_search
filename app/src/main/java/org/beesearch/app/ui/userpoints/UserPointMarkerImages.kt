package org.beesearch.app.ui.userpoints

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt
import org.beesearch.app.ui.map.MapStyleImage

internal const val USER_POINT_TRIANGLE_IMAGE = "beesearch-user-point-triangle"

private val GRAPHITE = 0xFF2B2B2B.toInt()
private val LIGHT_OUTLINE = 0xFFF2F2F2.toInt()

/**
 * One fixed marker image for every user point: a graphite upward triangle with a light outline.
 * Selected state is internal navigation state only and never changes this graphic.
 */
internal fun userPointMarkerStyleImages(density: Float): List<MapStyleImage> {
    val size = (24f * density).roundToInt().coerceAtLeast(1)
    return listOf(
        MapStyleImage(USER_POINT_TRIANGLE_IMAGE) {
            triangleBitmap(size, density)
        },
    )
}

private fun triangleBitmap(size: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val extent = size.toFloat()
    val pad = 2.2f * density
    val triangle = trianglePath(extent, pad)

    paint.style = Paint.Style.FILL
    paint.color = GRAPHITE
    canvas.drawPath(triangle, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.4f * density
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = LIGHT_OUTLINE
    canvas.drawPath(triangle, paint)
    return bitmap
}

private fun trianglePath(extent: Float, pad: Float): Path = Path().apply {
    moveTo(extent / 2f, pad)
    lineTo(extent - pad, extent - pad)
    lineTo(pad, extent - pad)
    close()
}
