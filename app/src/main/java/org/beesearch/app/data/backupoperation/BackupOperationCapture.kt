package org.beesearch.app.data.backupoperation

import androidx.room.withTransaction
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.PortableSettingsStore
import org.beesearch.app.data.backup.snapshot
import org.beesearch.app.data.backuprepository.CapturedMediaState
import org.beesearch.app.data.backuprepository.MediaStateCapture
import org.beesearch.app.data.backupsnapshot.SnapshotCapture
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.local.room.BeeSearchDatabase

/** Values only: neither publication nor protection can consult live research state through this. */
internal data class CapturedBackup(val entries: SnapshotDomainEntries, val media: CapturedMediaState)

/** One Room transaction and one portable-settings read per attempt, matching standalone capture. */
internal class BackupOperationCapture(
    private val readGraph: suspend () -> Graph,
    private val readSettings: suspend () -> PortableSettingsSnapshot,
) {
    constructor(database: BeeSearchDatabase, settings: PortableSettingsStore) : this(
        { database.withTransaction { database.backupDao().snapshot() } },
        { settings.snapshot() },
    )

    suspend fun capture(): CapturedBackup {
        val graph = readGraph()
        val settings = readSettings()
        return CapturedBackup(SnapshotCapture.entriesOf(graph, settings), MediaStateCapture.rowsOf(graph))
    }
}
