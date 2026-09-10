package org.beesearch.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

internal fun interface BackupDocumentExporter {
    suspend fun export(destination: Uri): UUID
}

internal class SafBackupDocumentExporter(
    private val backupService: BackupService,
    private val cacheDirectory: File,
    private val openDestination: (Uri) -> OutputStream?,
) : BackupDocumentExporter {
    constructor(
        backupService: BackupService,
        contentResolver: ContentResolver,
        cacheDirectory: File,
    ) : this(
        backupService = backupService,
        cacheDirectory = cacheDirectory,
        openDestination = { uri -> contentResolver.openOutputStream(uri, "w") },
    )

    override suspend fun export(destination: Uri): UUID = withContext(Dispatchers.IO) {
        val temporaryArchive = File.createTempFile("bee-search-backup-", ".zip", cacheDirectory)
        try {
            val archiveId = backupService.export(temporaryArchive)
            val output = openDestination(destination)
                ?: throw IOException("The selected document cannot be opened for writing")
            output.use { destinationStream ->
                temporaryArchive.inputStream().buffered().use { source ->
                    source.copyTo(destinationStream)
                }
            }
            archiveId
        } finally {
            temporaryArchive.delete()
        }
    }
}
