package org.beesearch.app.data.backupsnapshot

import androidx.room.withTransaction
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.PortableSettingsStore
import org.beesearch.app.data.backup.validateGraph
import org.beesearch.app.data.backup.validateSettings
import org.beesearch.app.data.backup.snapshot
import org.beesearch.app.data.local.room.BeeSearchDatabase

/** Captures one immutable Room graph before snapshot serialization. */
internal class SnapshotCapture(
    private val database: BeeSearchDatabase,
    private val settings: PortableSettingsStore,
    private val insideTransaction: suspend () -> Unit = {},
) {
    suspend fun capture(): SnapshotDomainEntries {
        val graph: Graph = database.withTransaction { insideTransaction(); database.backupDao().snapshot() }
        val portable: PortableSettingsSnapshot = settings.snapshot()
        return SnapshotDomainCodec.encode(graph, portable)
    }
}
