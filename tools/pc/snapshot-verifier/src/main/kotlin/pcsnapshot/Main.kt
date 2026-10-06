package pcsnapshot

import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.system.exitProcess

/** Human summary to stderr; exactly one JSON report to stdout. Never writes to the input. */
fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage: verify-snapshot <snapshot-file.zip>")
        println(GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(
            mapOf("verdict" to "FAIL", "issues" to listOf(Issue("INVALID_ARGUMENTS")))))
        exitProcess(2)
    }
    val report = try { Verifier().verify(Path.of(args[0])) }
    catch (_: Exception) { VerificationReport(args[0], issues = mutableListOf(Issue("UNREADABLE_INPUT"))) }
    System.err.println("${report.verdict}: ${report.verifiedEntries}/17 entries; ${report.issues.size} issues")
    report.issues.forEach { System.err.println("- ${it.code} ${it.scope}") }
    val output = linkedMapOf("inputPath" to report.inputPath, "fileSize" to report.fileSize,
        "actualWholeSha256" to report.actualWholeSha256, "filenameSnapshotId" to report.filenameSnapshotId,
        "filenameWholeSha256" to report.filenameWholeSha256, "verdict" to report.verdict,
        "verifiedEntries" to report.verifiedEntries, "issues" to report.issues)
    println(GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(output))
    exitProcess(if (report.issues.isEmpty()) 0 else 1)
}
