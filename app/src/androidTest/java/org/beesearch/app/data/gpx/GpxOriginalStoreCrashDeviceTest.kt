package org.beesearch.app.data.gpx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Host-controlled crash-boundary qualification for exclusive final publication. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpxOriginalStoreCrashDeviceTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val boundary get() = arguments.getString("gpxCrashBoundary") ?: "DIRECTORY_SYNC"
    private val kill get() = arguments.getString("gpxCrashKill") == "true"
    private val root: File get() {
        val name = arguments.getString("gpxCrashSandbox") ?: "gpx-store-crash-normal"
        require(name.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return File(ApplicationProvider.getApplicationContext<Context>().filesDir, name)
    }
    private val request = UUID(0, 7171)
    private val original = UUID(0, 7172)
    private val bytes = buildString {
        append("<?xml version=\"1.0\"?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"test\"><trk><trkseg>")
        repeat(2048) { index -> append("<trkpt lat=\"1.$index\" lon=\"2.$index\"/>") }
        append("</trkseg></trk></gpx>")
    }.toByteArray()
    private val expected get() = GpxOriginalIdentity(bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    @Test fun a_stageAndPublishAtRequestedBoundary() {
        check(bytes.size > 32 * 1024) { "Fixture must exceed two copy blocks" }
        check(root.mkdir()) { "Crash sandbox already exists; do not adopt it" }
        val store = GpxOriginalStore(root) { stage ->
            if (stage.name == boundary && kill) {
                File(root, "publication-boundary").writeText(boundary)
                // Host records publication-boundary and force-stops only this DEV process.
                repeat(120) { Thread.sleep(500) }
                throw AssertionError("Host did not stop the process at the requested boundary")
            }
        }
        assertEquals(expected, store.copyToStaging(request, original, { ChunkedInputStream(bytes) }))
        assertEquals(expected, store.publish(request, original, expected, { true }))
    }

    @Test fun b_recoverAfterBoundaryWithoutAdoptingUnknownFiles() {
        val store = GpxOriginalStore(root)
        assertTrue(store.inspectOperation(request, original).owned)
        val final = File(root, "gpx-originals/$original/original.gpx")
        if (boundary == "FINAL_COPY") {
            assertEquals(GpxOriginalPresence.INCOMPLETE, store.inspectFinal(original, expected))
            val before = final.readBytes()
            assertTrue(before.isNotEmpty() && before.size.toLong() < expected.length)
            assertThrows(GpxOriginalStoreException::class.java) {
                store.publish(request, original, expected, { true })
            }
            assertArrayEquals(before, final.readBytes())
            store.cleanupAbortedOriginal(request, original, expected,
                confirmDurablePublicationOwnership = { false },
                authorizeCurrentAbortWithoutReady = { true })
            assertFalse(final.exists())
            // cleanupAbortedOriginal retains the bound staging operation; publish retries it.
            assertEquals(expected, store.publish(request, original, expected, { true }))
        } else {
            // A copy completed before DIRECTORY_SYNC can be adopted and finished by retry.
            assertEquals(expected, store.publish(request, original, expected, { true }))
        }
        assertEquals(GpxOriginalPresence.PRESENT_VERIFIED, store.inspectFinal(original, expected))
        assertArrayEquals(bytes, store.openFinal(original, expected).use { it.readBytes() })
        assertTrue(root.deleteRecursively())
    }

    private class ChunkedInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        override fun read(): Int = delegate.read()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, minOf(length, 97))
        override fun close() = delegate.close()
    }
}
