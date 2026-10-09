package org.beesearch.app.ui.media

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.ui.properties.AttachmentRow
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationPointPhotoViewerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val files = mutableListOf<File>()

    @After
    fun removeFixtures() {
        files.forEach { it.delete() }
        files.mapNotNull { it.parentFile }.distinct().forEach { it.delete() }
    }

    @Test
    fun attachmentPhotoClickSendsReadableGrantedViewIntent() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ObservationAttachmentFileStore(base.filesDir, base.cacheDir)
        val pointId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val relativePath = ObservationAttachmentFileStore.relativePath(pointId, attachmentId)
        val bytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(0xFF2E7D32.toInt())
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        val file = store.resolve(relativePath).also {
            it.parentFile!!.mkdirs()
            it.writeBytes(bytes)
            files += it
        }
        val attachment = ObservationPointAttachment(
            id = attachmentId,
            observationPointId = pointId,
            type = AttachmentType.PHOTO,
            relativePath = relativePath,
            originalFileName = "stored.bin",
            mimeType = null,
            byteSize = bytes.size.toLong(),
            sha256 = "unused",
            createdAt = Instant.EPOCH,
        )
        val recorder = RecordingContext(base)
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalContext provides recorder) {
                Bee_searchTheme {
                    AttachmentRow(attachment = attachment, fileStore = store)
                }
            }
        }

        val photoNode = composeRule.onNodeWithContentDescription("Фотография точки")
        composeRule.waitUntil(5_000) {
            runCatching {
                photoNode.assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("point-photo-preview-$attachmentId").performClick()

        val intent = recorder.startedIntent
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent!!.action)
        assertEquals("content", intent.data!!.scheme)
        assertEquals("${base.packageName}.fileprovider", intent.data!!.authority)
        assertEquals("image/*", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(intent.data, intent.clipData!!.getItemAt(0).uri)
        val read = base.contentResolver.openInputStream(intent.data!!).use { it!!.readBytes() }
        assertArrayEquals(bytes, read)
        assertArrayEquals(bytes, file.readBytes())
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }
}
