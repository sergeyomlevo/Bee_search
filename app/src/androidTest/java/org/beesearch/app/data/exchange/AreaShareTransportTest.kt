@file:Suppress("DEPRECATION")

package org.beesearch.app.data.exchange

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The document «Отправить ареал» hands to the system.
 *
 * The user never picks a file, so what the receiving application gets has to be exactly the current
 * Ареал, readable through a temporary `content://` grant, and named so a human recognises it. The
 * share sheet itself is system UI and is not tested here beyond the intent it is given; the device
 * check covers that the chooser appears with compatible applications.
 */
@RunWith(AndroidJUnit4::class)
class AreaShareTransportTest {
    private lateinit var context: Context
    private lateinit var root: File

    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val bounds = listOf(
        MapGeoBounds(north = 57.111673, east = 39.026918, south = 56.562186, west = 38.470994),
    )

    private val area = MapArea(id = areaId, name = "Лух", bounds = bounds)

    private fun storage() = BeeSearchExchangeStorage(publicRoot = root, variantName = "Test")

    private fun mirror() = AreaExchangeMirror(storage())

    private fun transport() = AndroidAreaShareTransport(context, mirror())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = Files.createTempDirectory("bee-search-share").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        File(context.cacheDir, AndroidAreaShareTransport.SHARE_DIRECTORY_NAME).deleteRecursively()
    }

    @Test
    fun theAttachmentCarriesTheCurrentAreaJson() = runBlocking {
        val document = transport().prepare(area)

        assertEquals(AreaExchangeCodec.encode(area), document.file.readText())
        assertEquals(area, (AreaExchangeCodec.decode(document.file.readText()) as AreaExchangeReadResult.Present).area)
    }

    @Test
    fun theAttachmentIsAContentUriAndNotAFilePath() = runBlocking {
        val document = transport().prepare(area)

        assertEquals("content", document.uri.scheme)
        assertTrue(document.uri.toString(), document.uri.toString().startsWith("content://"))
        assertFalse(document.uri.toString(), document.uri.toString().startsWith("file://"))
    }

    @Test
    fun anotherApplicationCanReadTheAttachmentThroughTheUri() = runBlocking {
        val document = transport().prepare(area)

        val read = context.contentResolver.openInputStream(document.uri)!!.use { it.readBytes() }

        // This is what a receiving app does: it reads the granted URI, not our file system.
        assertEquals(AreaExchangeCodec.encode(area), String(read, Charsets.UTF_8))
    }

    @Test
    fun theReceiverSeesAHumanReadableFileName() = runBlocking {
        val document = transport().prepare(area)

        assertEquals("Лух--7e82a310.json", document.fileName)
        assertEquals("Лух--7e82a310.json", document.file.name)
        // The provider reports the same name to a receiver asking for the document.
        val displayName = context.contentResolver.query(
            document.uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
        assertEquals("Лух--7e82a310.json", displayName)
    }

    @Test
    fun theShareIntentAsksForTheSystemChooserWithATemporaryReadGrant() = runBlocking {
        val document = transport().prepare(area)

        val intent = transport().shareIntent(document)

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val send = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(AndroidAreaShareTransport.MIME_TYPE, send.type)
        assertEquals(document.uri, send.getParcelableExtra(android.content.Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(document.fileName, send.clipData!!.description.label)
    }

    @Test
    fun sendingRegeneratesAMissingManagedFile() = runBlocking {
        val managedFile = mirror().managedFile(area)
        assertFalse(managedFile.exists())

        transport().prepare(area)

        // The user asked for the file: the public copy is restored even if it was deleted by hand.
        assertTrue(managedFile.isFile)
        assertEquals(AreaExchangeCodec.encode(area), managedFile.readText())
    }

    @Test
    fun sendingAnAreaWithoutAnExchangeFolderStillProducesTheAttachment() = runBlocking {
        // A regular file where the exchange tree would have to be created: the mirror cannot work.
        val blockedRoot = File(root, "blocked").apply { writeText("not a directory") }
        val blocked = AndroidAreaShareTransport(
            context = context,
            mirror = AreaExchangeMirror(BeeSearchExchangeStorage(blockedRoot, "Test")),
        )

        val document = blocked.prepare(area)

        // The user's action must not depend on the public mirror: the canonical Ареал is enough.
        assertEquals(AreaExchangeCodec.encode(area), document.file.readText())
        assertEquals("content", document.uri.scheme)
    }
}
