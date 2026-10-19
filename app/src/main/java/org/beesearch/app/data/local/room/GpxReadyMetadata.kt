package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import java.util.UUID

/** Lightweight READY import metadata; deliberately excludes tracks, segments and samples. */
internal data class GpxReadyMetadata(
    val id: UUID,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    @ColumnInfo(name = "imported_at_epoch_ms")
    val importedAtEpochMs: Long,
)
