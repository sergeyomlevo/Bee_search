package org.beesearch.app.data.exchange

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.ui.map.MapArea

/** The attachment handed to the share sheet: a byte-identical copy of the managed Area file. */
internal data class AreaShareDocument(
    val file: File,
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
)

/**
 * Sends the Ареал through the standard Android share sheet.
 *
 * The user does not pick a file: the app prepares the document itself and only asks *where* to send
 * it. The attachment is a cached copy under the app's own cache directory, written with exactly the
 * bytes of the managed Area file, so the receiving application gets the current Ареал even when the
 * public exchange copy could not be written, and so a normal, human-readable file name can be
 * exposed without asking for any storage permission.
 *
 * `content://` with a temporary read grant is used instead of `file://`, which modern Android
 * rejects with `FileUriExposedException` and which would not grant the receiver any access.
 */
internal class AndroidAreaShareTransport(
    private val context: Context,
    private val mirror: AreaExchangeMirror,
) : AreaTransport {
    override suspend fun send(area: MapArea): AreaSendResult {
        val document = try {
            prepare(area)
        } catch (error: Exception) {
            return AreaSendResult.Failed(
                "Не удалось подготовить файл ареала: ${error.message ?: error::class.java.simpleName}",
            )
        }
        return try {
            context.startActivity(shareIntent(document))
            AreaSendResult.HandedOff
        } catch (_: android.content.ActivityNotFoundException) {
            AreaSendResult.Failed("Не найдено приложение, которому можно отправить ареал")
        } catch (error: Exception) {
            AreaSendResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    /**
     * The share attachment of this Ареал. Kept separate from launching the chooser so the prepared
     * document, its content URI and its metadata can be verified without sending anything anywhere.
     */
    internal suspend fun prepare(area: MapArea): AreaShareDocument = withContext(Dispatchers.IO) {
        // Best effort: the public exchange copy is part of the normal lifecycle, but the user's
        // action must not depend on it, so a mirror failure is not reported here.
        mirror.sync(area)
        val fileName = AreaExchangeFileName.of(area)
        val directory = File(context.cacheDir, SHARE_DIRECTORY_NAME).apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(AreaExchangeCodec.encode(area))
        AreaShareDocument(
            file = file,
            uri = FileProvider.getUriForFile(context, authority(), file),
            fileName = fileName,
            mimeType = MIME_TYPE,
        )
    }

    /** The chooser intent for a prepared document. */
    internal fun shareIntent(document: AreaShareDocument): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = document.mimeType
            putExtra(Intent.EXTRA_STREAM, document.uri)
            // The clip description is what several receivers show as the attachment name, so the
            // user sees "Лух--7e82a310.json" rather than an opaque cache file name.
            clipData = ClipData.newUri(context.contentResolver, document.fileName, document.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, CHOOSER_TITLE).apply {
            // The grant must sit on the intent the system actually starts, not only on the nested one.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun authority(): String = "${context.packageName}$FILE_PROVIDER_SUFFIX"

    internal companion object {
        /** Cache subdirectory declared in `res/xml/file_paths.xml`. */
        const val SHARE_DIRECTORY_NAME = "area-share"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"

        /** A JSON document: the Area file is a document, not an image or a text message. */
        const val MIME_TYPE = "application/json"
        const val CHOOSER_TITLE = "Отправить ареал"
    }
}
