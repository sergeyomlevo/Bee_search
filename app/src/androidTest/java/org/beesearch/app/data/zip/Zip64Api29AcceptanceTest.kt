package org.beesearch.app.data.zip

import android.os.Build
import android.os.StatFs
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Explicit API-29 ZIP64 harness. Scenario A tests a DEFLATED entry whose uncompressed size
 * exceeds UInt32; B tests STORED data followed by another entry, making the next local-header
 * offset exceed UInt32 (STORED requires a CRC32 pre-pass). Both are skipped by default.
 *
 * API-29 evidence: libcore android10-release, immutable 2b72e0b8486db652721095e55f1f068b41989ce0.
 * https://android.googlesource.com/platform/libcore/+/2b72e0b8486db652721095e55f1f068b41989ce0/ojluni/src/main/java/java/util/zip/ZipOutputStream.java
 * https://android.googlesource.com/platform/libcore/+/2b72e0b8486db652721095e55f1f068b41989ce0/ojluni/src/main/java/java/util/zip/ZipInputStream.java
 * https://android.googlesource.com/platform/libcore/+/2b72e0b8486db652721095e55f1f068b41989ce0/ojluni/src/main/java/java/util/zip/ZipFile.java
 * https://android.googlesource.com/platform/libcore/+/2b72e0b8486db652721095e55f1f068b41989ce0/ojluni/src/main/native/zip_util.c
 * Future owner-approved run: API29 internal goldfish/ranchu emulator (32GiB), Samsung disconnected,
 * Future builds/installation require separate approval, then install DEV and test APKs with
 * `adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk` and
 * `adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
 * `adb -s emulator-5554 shell am instrument -w -e class org.beesearch.app.data.zip.Zip64Api29AcceptanceTest -e zip64Acceptance true -e zip64LargeOffset true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner`.
 * Require two executed/pass tests and zero skips. None of these device commands ran in M1a.
 * Scenario A != B; Android runtime remains unverified until that procedure is run.
 */
class Zip64Api29AcceptanceTest {
    @Test fun scenarioA_deflatedLargeEntry() {
        gate(large = false)
        val file = fixture("deflated.zip")
        try { writeDeflated(file); assertReadable(file, false); negativeCases(file) } finally { file.delete(); file.parentFile?.delete() }
    }

    @Test fun scenarioB_storedLargeOffsetAndMarker() {
        gate(large = true)
        val file = fixture("stored.zip")
        try { writeStored(file); assertTrue(file.length() > SIZE); assertReadable(file, true); negativeCases(file) } finally { file.delete(); file.parentFile?.delete() }
    }

    private fun gate(large: Boolean) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("pass zip64Acceptance=true explicitly", args.getString("zip64Acceptance") == "true")
        if (large) assumeTrue("pass zip64LargeOffset=true explicitly", args.getString("zip64LargeOffset") == "true")
        assertEquals("API 29 required", 29, Build.VERSION.SDK_INT)
        assertTrue("not a physical device", Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu")
        val context = InstrumentationRegistry.getInstrumentation().context
        assertEquals("test APK sandbox only", "org.beesearch.app.dev.test", context.packageName)
        val needed = if (large) 10L * 1024 * 1024 * 1024 else 128L * 1024 * 1024
        val root = context.dataDir
        val usableSpace = root.usableSpace
        val filesystem = runCatching { StatFs(root.absolutePath) }
        val diagnostic = "root.absolutePath=${root.absolutePath}, root.exists=${root.exists()}, " +
            "root.isDirectory=${root.isDirectory}, root.usableSpace=$usableSpace, " +
            "root.freeSpace=${root.freeSpace}, root.totalSpace=${root.totalSpace}, " +
            filesystem.fold(
                onSuccess = { "StatFs.availableBytes=${it.availableBytes}, StatFs.totalBytes=${it.totalBytes}" },
                onFailure = { "StatFs.error=$it" }
            ) + ", requiredBytes=$needed"
        println("ZIP64 storage: $diagnostic")
        assertTrue("fixture root must exist and be a directory: $diagnostic", root.exists() && root.isDirectory)
        assertTrue("cannot inspect fixture filesystem: $diagnostic", filesystem.isSuccess)
        assertTrue("insufficient storage: $diagnostic", usableSpace >= needed)
    }

    private fun fixture(name: String): File {
        val root = InstrumentationRegistry.getInstrumentation().context.dataDir
        check(root.exists() && root.isDirectory) { "fixture root must exist and be a directory: ${root.absolutePath}" }
        val operationDir = root.resolve("zip64-${java.util.UUID.randomUUID()}")
        check(operationDir.mkdir()) { "cannot create fixture directory: ${operationDir.absolutePath}" }
        return operationDir.resolve(name)
    }

    private fun writeDeflated(file: File) = ZipOutputStream(FileOutputStream(file)).use { zip ->
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(DATA).apply { method = ZipEntry.DEFLATED }); generate(false) { bytes, count -> zip.write(bytes, 0, count); digest.update(bytes, 0, count) }; zip.closeEntry()
        expected = digest.digest()
    }

    private fun writeStored(file: File) {
        val crc = CRC32(); val digest = MessageDigest.getInstance("SHA-256")
        generate(true) { bytes, count -> crc.update(bytes, 0, count); digest.update(bytes, 0, count) }
        expected = digest.digest()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry(DATA).apply { method = ZipEntry.STORED; size = SIZE; compressedSize = SIZE; this.crc = crc.value })
            generate(true) { bytes, count -> zip.write(bytes, 0, count) }; zip.closeEntry(); zip.putNextEntry(ZipEntry(MARKER)); zip.write(MARKER_BYTES); zip.closeEntry()
        }
    }

    private fun assertReadable(file: File, markerExpected: Boolean) {
        val wanted = requireNotNull(expected)
        val wantedNames = if (markerExpected) listOf(DATA, MARKER) else listOf(DATA)
        ZipInputStream(FileInputStream(file)).use { zis ->
            val names = mutableListOf<String>()
            val entry = requireNotNull(zis.nextEntry); names += entry.name; assertEquals(DATA, entry.name); verify(zis, wanted); assertEquals(SIZE, entry.size)
            if (markerExpected) { assertEquals(SIZE, entry.compressedSize); val marker = requireNotNull(zis.nextEntry); names += marker.name; assertEquals(MARKER, marker.name); val sink = java.io.ByteArrayOutputStream(); copyZipBytes(zis, sink, MARKER_BYTES.size.toLong(), MARKER_BYTES.size.toLong()); assertEquals(MARKER_BYTES.toList(), sink.toByteArray().toList()) }
            assertTrue(zis.nextEntry == null); assertEquals(wantedNames, names)
        }
        ZipFile(file).use { zip ->
            assertEquals(wantedNames, zip.entries().toList().map { it.name })
            val entry = requireNotNull(zip.getEntry(DATA)); assertEquals(SIZE, entry.size); zip.getInputStream(entry).use { verify(it, wanted) }
            if (markerExpected) { assertEquals(SIZE, entry.compressedSize); zip.getInputStream(zip.getEntry(MARKER)).use { stream -> val sink = java.io.ByteArrayOutputStream(); copyZipBytes(stream, sink, MARKER_BYTES.size.toLong(), MARKER_BYTES.size.toLong()); assertEquals(MARKER_BYTES.toList(), sink.toByteArray().toList()) } }
        }
    }

    private fun verify(input: InputStream, wanted: ByteArray) {
        val digest = MessageDigest.getInstance("SHA-256"); var count = 0L; val buffer = ByteArray(BUFFER)
        while (true) { val n = input.read(buffer); if (n < 0) break; count = Math.addExact(count, n.toLong()); assertTrue("excess data", count <= SIZE); digest.update(buffer, 0, n) }
        assertEquals(SIZE, count); assertEquals(wanted.toList(), digest.digest().toList())
    }

    private fun negativeCases(file: File) {
        RandomAccessFile(file, "rw").use { access ->
            // Fixed header inspection of our generated fixture ONLY, not a production ZIP parser.
            access.seek(26)
            fun littleShort(): Int = access.readUnsignedByte() or (access.readUnsignedByte() shl 8)
            val payload = 30L + littleShort() + littleShort()
            access.seek(payload); val original = access.readUnsignedByte()
            access.seek(payload); access.writeByte(original xor 1)
            try {
                assertDamaged { assertStreamIntegrity(file) }
                assertDamaged { ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(DATA)).use { verify(it, requireNotNull(expected)) } } }
            } finally { access.seek(payload); access.writeByte(original) }
            access.setLength(payload + (access.length() - payload) / 2)
        }
        assertDamaged { assertStreamIntegrity(file) }
        assertDamaged { ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(DATA)).use { verify(it, requireNotNull(expected)) } } }
    }
    private fun assertStreamIntegrity(file: File) = ZipInputStream(file.inputStream()).use { zip -> assertEquals(DATA, requireNotNull(zip.nextEntry).name); verify(zip, requireNotNull(expected)) }
    private fun assertDamaged(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("damaged ZIP unexpectedly accepted: $error", error is java.io.IOException || error is AssertionError)
    }
    private fun generate(randomData: Boolean, sink: (ByteArray, Int) -> Unit) { val bytes = ByteArray(BUFFER); val random = java.util.Random(731); var left = SIZE; while (left > 0) { val n = minOf(left, bytes.size.toLong()).toInt(); if (randomData) random.nextBytes(bytes); sink(bytes, n); left -= n } }
    private var expected: ByteArray? = null

    private companion object {
        const val DATA = "large-zeroes.bin"; const val MARKER = "marker.txt"; const val BUFFER = 65536
        const val SIZE = (1L shl 32) + 65536L
        val MARKER_BYTES = "zip64-marker\n".toByteArray()
    }
}
