package pcsnapshot

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CliTest {
    @get:Rule val temp = TemporaryFolder()
    private fun run(vararg arguments: String): Pair<Int, String> {
        val classes = listOf(Class.forName("pcsnapshot.MainKt"), Gson::class.java, Unit::class.java)
        val classpath = classes.map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
            .distinct().joinToString(File.pathSeparator)
        val java = File(System.getProperty("java.home"), "bin/java.exe")
        val process = ProcessBuilder(listOf(java.absolutePath, "-cp", classpath, "pcsnapshot.MainKt") + arguments)
            .redirectError(temp.newFile()).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }
    @Test fun cliPassAndNonzeroFailureLeaveInputUnchanged() {
        val file = Fixture.canonicalCopy(temp.root.toPath())
        val before = java.nio.file.Files.readAllBytes(file)
        val (code, output) = run(file.toString())
        assertEquals(0, code)
        val report = JsonParser.parseString(output).asJsonObject
        assertEquals("PASS", report["verdict"].asString)
        assertEquals(17, report["verifiedEntries"].asInt)
        assertTrue(report.keySet().containsAll(listOf("inputPath", "fileSize", "actualWholeSha256",
            "filenameSnapshotId", "filenameWholeSha256", "verdict", "issues")))
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(file))
        assertNotEquals(0, run(temp.root.toPath().resolve("missing.zip").toString()).first)
    }
}
