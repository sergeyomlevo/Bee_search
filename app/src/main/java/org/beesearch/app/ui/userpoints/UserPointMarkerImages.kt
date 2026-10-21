package org.beesearch.app.ui.userpoints

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt
import org.beesearch.app.ui.map.MapStyleImage

internal const val USER_POINT_TRIANGLE_IMAGE = "beesearch-user-point-triangle"
internal const val USER_POINT_TRIANGLE_SELECTED_IMAGE = "beesearch-user-point-triangle-selected"

private val GRAPHITE = 0xFF2B2B2B.toInt()
private val LIGHT_OUTLINE = 0xFFF2F2F2.toInt()
private val SELECTED_HALO = 0xFFFFB91F.toInt()

/**
 * Two fixed marker images: a graphite upward triangle with a light outline, and the same marker
 * with a larger amber halo for the selected state. No user-selectable shape/colour/icon is added.
 */
internal fun userPointMarkerStyleImages(density: Float): List<MapStyleImage> {
    val normalSize = (24f * density).roundToInt().coerceAtLeast(1)
    val selectedSize = (40f * density).roundToInt().coerceAtLeast(1)
    return listOf(
        MapStyleImage(USER_POINT_TRIANGLE_IMAGE) {
            triangleBitmap(normalSize, density, selected = false)
        },
        MapStyleImage(USER_POINT_TRIANGLE_SELECTED_IMAGE) {
            triangleBitmap(selectedSize, density, selected = true)
        },
    )
}

private fun triangleBitmap(size: Int, density: Float, selected: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val extent = size.toFloat()

    if (selected) {
        val haloPad = 1.2f * density
        val halo = trianglePath(extent, haloPad)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(105, Color.red(SELECTED_HALO), Color.green(SELECTED_HALO), Color.blue(SELECTED_HALO))
        canvas.drawPath(halo, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(220, Color.red(SELECTED_HALO), Color.green(SELECTED_HALO), Color.blue(SELECTED_HALO))
        canvas.drawPath(halo, paint)
    }

    val darkPad = if (selected) 7f * density else 2.2f * density
    val triangle = trianglePath(extent, darkPad)
    paint.style = Paint.Style.FILL
    paint.color = GRAPHITE
    canvas.drawPath(triangle, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = (if (selected) 1.8f else 1.4f) * density
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
