package org.beesearch.app.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.beesearch.app.ui.properties.decodePhotoThumbnail
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPreviewDecoderTest {
    private val fixtures = mutableListOf<File>()
    private var fixtureDirectory: File? = null

    @After
    fun removeFixtures() {
        fixtures.forEach { it.delete() }
        fixtureDirectory?.delete()
    }

    @Test
    fun decodesAllExifOrientationsWithExpectedQuadrantMapping() {
        val sourceWidth = 40
        val sourceHeight = 20

        for (maxEdge in listOf(512, 1024)) {
            for (orientation in 1..8) {
                val file = createFixture(sourceWidth, sourceHeight, orientation)
                val decoded = decodePhotoPreview(file, maxEdge = maxEdge)
                assertNotNull("orientation=$orientation", decoded)
                val bitmap = decoded!!

                val expectedWidth = if (orientation >= 5) sourceHeight else sourceWidth
                val expectedHeight = if (orientation >= 5) sourceWidth else sourceHeight
                assertEquals("width orientation=$orientation", expectedWidth, bitmap.width)
                assertEquals("height orientation=$orientation", expectedHeight, bitmap.height)

                val samplePoints = listOf(
                    1 to 1,
                    bitmap.width - 2 to 1,
                    1 to bitmap.height - 2,
                    bitmap.width - 2 to bitmap.height - 2,
                )
                samplePoints.forEach { (x, y) ->
                    val expected = sourceColor(sourceWidth, sourceHeight, orientation, x, y)
                    assertColorNear(
                        expected,
                        bitmap.getPixel(x, y),
                        "orientation=$orientation x=$x y=$y",
                    )
                }
            }
        }
    }

    @Test
    fun invalidOrMissingFilesReturnNoPreviewAndPointThumbnailUsesSameDecoder() {
        val missing = File(targetDirectory(), "missing.jpg")
        assertEquals(null, decodePhotoPreview(missing, maxEdge = 512))

        val corrupt = File(targetDirectory(), "corrupt.jpg").also {
            it.writeText("not-a-jpeg")
            fixtures += it
        }
        assertEquals(null, decodePhotoPreview(corrupt, maxEdge = 512))

        val portrait = createFixture(40, 20, orientation = 6)
        val thumbnail = decodePhotoThumbnail(portrait)
        assertNotNull(thumbnail)
        assertEquals(20, thumbnail!!.width)
        assertEquals(40, thumbnail.height)
    }

    @Test
    fun downsamplingRemainsBoundedAndOrientationAware() {
        val normal = createFixture(1600, 800, orientation = 1)
        val rotated = createFixture(1600, 800, orientation = 6)

        val normalBitmap = decodePhotoPreview(normal, maxEdge = 128)
        val rotatedBitmap = decodePhotoPreview(rotated, maxEdge = 128)

        assertNotNull(normalBitmap)
        assertNotNull(rotatedBitmap)
        assertTrue(normalBitmap!!.width <= 128)
        assertTrue(normalBitmap.height <= 128)
        assertTrue(rotatedBitmap!!.width <= 128)
        assertTrue(rotatedBitmap.height <= 128)
        assertTrue("orientation must swap the bounded dimensions", rotatedBitmap.height > rotatedBitmap.width)
        assertTrue("normal image must retain landscape geometry", normalBitmap.width > normalBitmap.height)
    }

    @Test
    fun sourceBytesRemainUnchangedAndLegacyBitmapFactoryIgnoresExifRotation() {
        val file = createFixture(40, 20, orientation = 6)
        val before = sha256(file)

        val legacy = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(legacy)
        assertEquals(40, legacy!!.width)
        assertEquals(20, legacy.height)

        val decoded = decodePhotoPreview(file, maxEdge = 512)
        assertNotNull(decoded)
        assertEquals(20, decoded!!.width)
        assertEquals(40, decoded.height)
        assertEquals("decoder must not rewrite the managed source", before, sha256(file))
    }

    private fun createFixture(width: Int, height: Int, orientation: Int): File {
        require(orientation in 1..8)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, quadrantColor(width, height, x, y))
            }
        }
        val jpeg = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
        val file = File(targetDirectory(), "photo-orientation-$orientation-${System.nanoTime()}.jpg")
        file.writeBytes(withExifOrientation(jpeg, orientation))
        fixtures += file
        return file
    }

    private fun targetDirectory(): File =
        (fixtureDirectory ?: Files.createTempDirectory(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(),
            "photo-orientation-",
        ).toFile().also { fixtureDirectory = it })

    private fun quadrantColor(width: Int, height: Int, x: Int, y: Int): Int = when {
        x < width / 2 && y < height / 2 -> RED
        x >= width / 2 && y < height / 2 -> GREEN
        x < width / 2 -> BLUE
        else -> YELLOW
    }

    private fun sourceColor(width: Int, height: Int, orientation: Int, outputX: Int, outputY: Int): Int {
        val (x, y) = when (orientation) {
            1 -> outputX to outputY
            2 -> width - 1 - outputX to outputY
            3 -> width - 1 - outputX to height - 1 - outputY
            4 -> outputX to height - 1 - outputY
            5 -> outputY to outputX
            6 -> outputY to height - 1 - outputX
            7 -> width - 1 - outputY to height - 1 - outputX
            8 -> width - 1 - outputY to outputX
            else -> error("orientation=$orientation")
        }
        return quadrantColor(width, height, x, y)
    }

    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
            0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        val payload = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(),
            (length ushr 8).toByte(), length.toByte(),
        ) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun assertColorNear(expected: Int, actual: Int, message: String) {
        assertTrue(
            "$message expected=${Integer.toHexString(expected)} actual=${Integer.toHexString(actual)}",
            colorDistance(expected, actual) < 180,
        )
    }

    private fun colorDistance(left: Int, right: Int): Int =
        abs((left shr 16 and 0xFF) - (right shr 16 and 0xFF)) +
            abs((left shr 8 and 0xFF) - (right shr 8 and 0xFF)) +
            abs((left and 0xFF) - (right and 0xFF))

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
    }
}
