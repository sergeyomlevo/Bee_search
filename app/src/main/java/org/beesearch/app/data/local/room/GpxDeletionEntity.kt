package org.beesearch.app.data.local.room

import androidx.room.*
import java.util.UUID

/** Permanent replay tombstone; two independent, retryable cleanup obligations. No parent FK. */
@Entity(tableName = "gpx_deletions", indices = [Index(value = ["request_uuid"], unique = true)])
internal data class GpxDeletionEntity(
    @PrimaryKey @ColumnInfo(name = "import_id") val importId: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "request_uuid") val requestUuid: UUID,
    @ColumnInfo(name = "original_object_id") val originalObjectId: UUID,
    @ColumnInfo(name = "original_sha256") val originalSha256: String,
    @ColumnInfo(name = "original_byte_length") val originalByteLength: Long,
    val proof: String?,
    @ColumnInfo(name = "files_cleaned") val filesCleaned: Boolean = false,
    @ColumnInfo(name = "settings_cleaned") val settingsCleaned: Boolean = false,
)
