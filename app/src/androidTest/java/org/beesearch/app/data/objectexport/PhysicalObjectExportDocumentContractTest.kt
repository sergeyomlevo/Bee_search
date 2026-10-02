package org.beesearch.app.data.objectexport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The document contract of one object package and the staged write behind it.
 *
 * The picker contract is the same one the ObservationPoint export uses, so the file name, MIME type
 * and initial exchange folder stay consistent. The write itself is staged: a package is assembled and
 * verified before the destination is opened at all, so a damaged media file cannot leave a truncated
 * archive at the location the user chose.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalObjectExportDocumentContractTest {
    @Test
    @Suppress("DEPRECATION")
    fun createDocumentUsesExpectedFilenameMimeAndExchangeDataInitialFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2FBeeSearch%2FDev%2FExchange%2FData",
        )
        val contract = CreateExchangeDocument("application/zip", initial)
        val expectedName = physicalObjectExportFileName(
            type = PhysicalObjectType.HOLLOW,
            sequenceNumber = 4,
            territoryCode = "DEV-BENCH2",
            createdAt = Instant.parse("2026-09-20T10:00:00Z"),
            objectId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        )

        val intent = contract.createIntent(context, expectedName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(expectedName, intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(initial, intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun completePackageIsCopiedIntoTheDestination() = withStore { store ->
        val graph = graph()
        val mediaFile = store.resolve(graph.media.single().relativePath)
        mediaFile.parentFile?.mkdirs()
        mediaFile.writeText("media-bytes")
        val destination = ByteArrayOutputStream()
        val exporter = SafPhysicalObjectDocumentExporter(
            service = PhysicalObjectExportService(PhysicalObjectExportSource { graph }, store),
            cacheDirectory = cacheRoot,
            openDestination = { destination },
        )

        runBlocking { exporter.export(graph.id, DOCUMENT) }

        val decoded = PhysicalObjectExportCodec.decode(destination.toByteArray().inputStream())
        assertEquals(graph, decoded.graph)
        assertArrayEquals(mediaFile.readBytes(), decoded.mediaBytes.getValue(graph.media.single().id))
    }

    @Test
    fun damagedMediaNeverTouchesTheDestination() = withStore { store ->
        val graph = graph()
        var destinationOpened = false
        val exporter = SafPhysicalObjectDocumentExporter(
            service = PhysicalObjectExportService(PhysicalObjectExportSource { graph }, store),
            cacheDirectory = cacheRoot,
            openDestination = {
                destinationOpened = true
                OutputStream.nullOutputStream()
            },
        )

        var thrown: Throwable? = null
        try {
            runBlocking { exporter.export(graph.id, DOCUMENT) }
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue("the missing media file must refuse the export", thrown is PhysicalObjectExportSourceMissing)
        assertFalse("the destination must not be opened for a package that cannot be built", destinationOpened)
    }

    @Test
    @Suppress("DEPRECATION")
    fun collectionCreateDocumentUsesOneTypedFilenameAndTheSameSafContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2FBeeSearch%2FDev%2FExchange%2FData",
        )
        val expectedName = physicalObjectCollectionExportFileName(
            PhysicalObjectType.HOLLOW,
            "DEV-BENCH2",
            Instant.parse("2026-10-02T10:00:00Z"),
        )

        val intent = CreateExchangeDocument("application/zip", initial).createIntent(context, expectedName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals("DEV-BENCH2--hollows--2026-10-02.zip", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(initial, intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun completeCollectionIsVerifiedThenCopiedIntoOneDestination() = withStore { store ->
        val graph = graph()
        val mediaFile = store.resolve(graph.media.single().relativePath)
        mediaFile.parentFile?.mkdirs()
        mediaFile.writeText("media-bytes")
        val collection = PhysicalObjectCollectionExportGraph(graph.territory, graph.type, listOf(graph))
        val destination = ByteArrayOutputStream()
        val exporter = SafPhysicalObjectCollectionDocumentExporter(
            service = PhysicalObjectCollectionExportService(
                PhysicalObjectCollectionExportSource { _, _ -> collection },
                store,
            ),
            cacheDirectory = cacheRoot,
            openDestination = { destination },
        )

        runBlocking { exporter.export(graph.territoryId, graph.type, DOCUMENT) }

        val decoded = PhysicalObjectCollectionExportCodec.decode(destination.toByteArray().inputStream())
        assertEquals(listOf(graph), decoded.graph.objects)
        assertArrayEquals(mediaFile.readBytes(), decoded.mediaBytes.getValue(graph.media.single().id))
    }

    @Test
    fun damagedCollectionMediaNeverOpensTheDestination() = withStore { store ->
        val graph = graph()
        val collection = PhysicalObjectCollectionExportGraph(graph.territory, graph.type, listOf(graph))
        var destinationOpened = false
        val exporter = SafPhysicalObjectCollectionDocumentExporter(
            service = PhysicalObjectCollectionExportService(
                PhysicalObjectCollectionExportSource { _, _ -> collection },
                store,
            ),
            cacheDirectory = cacheRoot,
            openDestination = {
                destinationOpened = true
                OutputStream.nullOutputStream()
            },
        )

        var thrown: Throwable? = null
        try {
            runBlocking { exporter.export(graph.territoryId, graph.type, DOCUMENT) }
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is PhysicalObjectExportSourceMissing)
        assertFalse(destinationOpened)
    }

    private fun withStore(block: (PhysicalObjectMediaFileStore) -> Unit) {
        val root = File(cacheRoot, "store").apply { mkdirs() }
        val files = File(cacheRoot, "files").apply { mkdirs() }
        try {
            block(PhysicalObjectMediaFileStore(files, root))
        } finally {
            root.deleteRecursively()
            files.deleteRecursively()
        }
    }

    private fun graph(): PhysicalObjectExportGraph {
        val objectId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val mediaId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val content = "media-bytes".toByteArray()
        return PhysicalObjectExportGraph(
            id = objectId,
            type = PhysicalObjectType.HOLLOW,
            territoryId = territoryId,
            sequenceNumber = 4,
            latitude = 56.1961784,
            longitude = 42.7480444,
            createdAt = Instant.parse("2026-09-20T10:00:00Z"),
            creatorObserverId = observerId,
            name = null,
            properties = PhysicalObjectExportProperties.Hollow(
                HollowProperties("ель", 450.0, 127, 32.0, null, null),
            ),
            media = listOf(
                PhysicalObjectMedia(
                    mediaId, objectId, PhysicalObjectMediaType.IMAGE,
                    PhysicalObjectMediaFileStore.relativePath(objectId, mediaId),
                    "дупло.jpg", "image/jpeg", content.size.toLong(),
                    java.security.MessageDigest.getInstance("SHA-256").digest(content)
                        .joinToString("") { "%02x".format(it) },
                    Instant.parse("2026-09-20T10:01:00Z"),
                ),
            ),
            territory = TerritoryExportSnapshot(territoryId, "DEV", "Территория"),
            observer = ObserverExportSnapshot(observerId, "O1", "Иванов", "Иван", null),
        )
    }

    private companion object {
        val DOCUMENT: Uri = Uri.parse("content://test/document/object.zip")
        val cacheRoot: File = File(
            System.getProperty("java.io.tmpdir"),
            "object-export-document-${UUID.randomUUID()}",
        ).apply { mkdirs() }
    }
}
