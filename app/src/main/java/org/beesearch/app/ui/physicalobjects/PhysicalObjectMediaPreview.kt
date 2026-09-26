package org.beesearch.app.ui.physicalobjects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PhysicalObjectMediaPreview(
    file: File?,
    isVideo: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file, isVideo) {
        value = null
        value = file?.let { previewFile ->
            withContext(Dispatchers.IO) { loadMediaPreview(previewFile, isVideo) }
        }
    }
    val taggedModifier = if (testTag == null) modifier else modifier.testTag(testTag)
    Box(
        modifier = taggedModifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { preview ->
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Text(if (isVideo) "Видео" else "Фото")
        if (isVideo) {
            Text(
                text = "▶",
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)),
                color = Color.White,
            )
        }
    }
}

private fun loadMediaPreview(file: File, isVideo: Boolean): Bitmap? = runCatching {
    if (!file.isFile) return null
    if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                1024,
                1024,
            )
        } finally {
            retriever.release()
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
            sample *= 2
        }
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()
