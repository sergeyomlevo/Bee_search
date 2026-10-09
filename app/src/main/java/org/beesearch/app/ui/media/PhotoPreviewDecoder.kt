package org.beesearch.app.ui.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import kotlin.math.max

/** Decodes an EXIF-oriented preview without rewriting the original photo. */
internal fun decodePhotoPreview(file: File, maxEdge: Int): Bitmap? {
    require(maxEdge > 0)
    if (!file.isFile) return null
    return try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            // ImageDecoder reports oriented dimensions and applies all EXIF transforms itself.
            // Choose the output size before decoding, including for rotated/mirrored JPEGs.
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > maxEdge) {
                decoder.setTargetSize(
                    max(1, (width.toLong() * maxEdge / longest).toInt()),
                    max(1, (height.toLong() * maxEdge / longest).toInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: java.io.IOException) {
        null
    }
}
