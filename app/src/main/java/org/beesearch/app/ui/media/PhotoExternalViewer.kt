package org.beesearch.app.ui.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPointAttachment

/** Opens a persisted ObservationPoint photo in an installed external image viewer. */
internal fun openObservationPointPhoto(
    context: Context,
    attachment: ObservationPointAttachment,
    fileStore: ObservationAttachmentFileStore?,
) {
    val file = try {
        fileStore?.resolve(attachment.relativePath)
    } catch (_: IllegalArgumentException) {
        null
    }
    if (file == null || !file.isFile) {
        Toast.makeText(context, "Фотография недоступна", Toast.LENGTH_LONG).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = attachment.mimeType
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: "image/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Фотография", uri) }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для просмотра фотографий", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось открыть фотографию", Toast.LENGTH_LONG).show()
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, "Не удалось открыть фотографию", Toast.LENGTH_LONG).show()
    }
}
