package org.beesearch.app.ui.media

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import org.beesearch.app.ui.physicalobjects.PhysicalObjectMediaPreview
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPreviewUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixtures = mutableListOf<File>()

    @After
    fun removeFixtures() {
        fixtures.forEach { it.delete() }
        fixtures.map { it.parentFile }.distinct().forEach { it?.delete() }
    }

    @Test
    fun portraitExifRotationIsCorrectInThumbnailAndEnlargedPreview() {
        val file = createFixture(40, 20, orientation = 6)
        composeRule.setContent {
            Bee_searchTheme {
                Column {
                    PhysicalObjectMediaPreview(
                        file = file,
                        isVideo = false,
                        contentDescription = "portrait thumbnail",
                        modifier = Modifier.size(60.dp, 120.dp),
                        testTag = "portrait-thumbnail",
                    )
                    PhysicalObjectMediaPreview(
                        file = file,
                        isVideo = false,
                        contentDescription = "portrait enlarged",
                        modifier = Modifier.size(120.dp, 240.dp),
                        testTag = "portrait-enlarged",
                    )
                }
            }
        }

        assertPreviewColors("portrait-thumbnail", PORTRAIT_COLORS)
        assertPreviewColors("portrait-enlarged", PORTRAIT_COLORS)
    }

    @Test
    fun landscapeExifRotationRemainsCorrectInThumbnailAndEnlargedPreview() {
        val file = createFixture(40, 20, orientation = 1)
        composeRule.setContent {
            Bee_searchTheme {
                Column {
                    PhysicalObjectMediaPreview(
                        file = file,
                        isVideo = false,
                        contentDescription = "landscape thumbnail",
                        modifier = Modifier.size(120.dp, 60.dp),
                        testTag = "landscape-thumbnail",
                    )
                    PhysicalObjectMediaPreview(
                        file = file,
                        isVideo = false,
                        contentDescription = "landscape enlarged",
                        modifier = Modifier.size(240.dp, 120.dp),
                        testTag = "landscape-enlarged",
                    )
                }
            }
        }

        assertPreviewColors("landscape-thumbnail", LANDSCAPE_COLORS)
        assertPreviewColors("landscape-enlarged", LANDSCAPE_COLORS)
    }

    private fun assertPreviewColors(tag: String, expected: IntArray) {
        val node = composeRule.onNodeWithTag(tag)
        composeRule.waitUntil(5_000) {
            runCatching {
                val image = node.captureToImage().asAndroidBitmap()
                val points = listOf(
                    image.width / 4 to image.height / 4,
                    image.width * 3 / 4 to image.height / 4,
                    image.width / 4 to image.height * 3 / 4,
                    image.width * 3 / 4 to image.height * 3 / 4,
                )
                points.zip(expected.asIterable()).all { (point, color) ->
                    colorDistance(image.getPixel(point.first, point.second), color) < 220
                }
            }.getOrDefault(false)
        }
        val image = node.captureToImage().asAndroidBitmap()
        val points = listOf(
            image.width / 4 to image.height / 4,
            image.width * 3 / 4 to image.height / 4,
            image.width / 4 to image.height * 3 / 4,
            image.width * 3 / 4 to image.height * 3 / 4,
        )
        points.zip(expected.asIterable()).forEach { (point, color) ->
            assertTrue("$tag point=$point", colorDistance(image.getPixel(point.first, point.second), color) < 220)
        }
    }

    private fun createFixture(width: Int, height: Int, orientation: Int): File {
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
        val directory = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.cacheDir
            .resolve("photo-preview-ui-${System.nanoTime()}")
            .also { check(it.mkdirs()) }
        val file = directory.resolve("fixture.jpg")
        file.writeBytes(withExifOrientation(jpeg, orientation))
        fixtures += file
        return file
    }

    private fun quadrantColor(width: Int, height: Int, x: Int, y: Int): Int = when {
        x < width / 2 && y < height / 2 -> RED
        x >= width / 2 && y < height / 2 -> GREEN
        x < width / 2 -> BLUE
        else -> YELLOW
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

    private fun colorDistance(left: Int, right: Int): Int =
        abs((left shr 16 and 0xFF) - (right shr 16 and 0xFF)) +
            abs((left shr 8 and 0xFF) - (right shr 8 and 0xFF)) +
            abs((left and 0xFF) - (right and 0xFF))

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        val PORTRAIT_COLORS = intArrayOf(BLUE, RED, YELLOW, GREEN)
        val LANDSCAPE_COLORS = intArrayOf(RED, GREEN, BLUE, YELLOW)
    }
}
