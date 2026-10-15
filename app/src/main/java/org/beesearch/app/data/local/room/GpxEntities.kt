package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "gpx_imports",
    foreignKeys = [
        ForeignKey(TerritoryEntity::class, ["id"], ["territory_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION),
        ForeignKey(ObserverEntity::class, ["id"], ["importing_observer_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION),
    ],
    indices = [
        Index(value = ["original_object_id"], unique = true),
        Index(value = ["request_uuid"], unique = true),
        Index(value = ["territory_id", "publication_state"]),
        Index(value = ["importing_observer_id"]),
        Index(value = ["original_sha256"]),
    ],
)
internal data class GpxImportEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "importing_observer_id") val importingObserverId: UUID? = null,
    @ColumnInfo(name = "original_object_id") val originalObjectId: UUID,
    @ColumnInfo(name = "original_sha256") val originalSha256: String,
    @ColumnInfo(name = "original_byte_length") val originalByteLength: Long,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "gpx_version") val gpxVersion: String,
    @ColumnInfo(name = "parser_profile_version") val parserProfileVersion: String,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
    val creator: String,
    @ColumnInfo(name = "source_name") val sourceName: String? = null,
    @ColumnInfo(name = "source_description") val sourceDescription: String? = null,
    @ColumnInfo(name = "author_name") val authorName: String? = null,
    @ColumnInfo(name = "publication_state") val publicationState: String = "PENDING",
    @ColumnInfo(name = "request_uuid") val requestUuid: UUID? = null,
    @ColumnInfo(name = "unsupported_content_count") val unsupportedContentCount: Long = 0,
    @ColumnInfo(name = "unsupported_content_first_scope") val unsupportedContentFirstScope: String? = null,
    @ColumnInfo(name = "unsupported_content_first_track_ordinal") val unsupportedContentFirstTrackOrdinal: Int? = null,
    @ColumnInfo(name = "unsupported_content_first_segment_ordinal") val unsupportedContentFirstSegmentOrdinal: Int? = null,
    @ColumnInfo(name = "unsupported_content_first_sample_ordinal") val unsupportedContentFirstSampleOrdinal: Int? = null,
    @ColumnInfo(name = "unsupported_time_count") val unsupportedTimeCount: Long = 0,
    @ColumnInfo(name = "unsupported_time_first_scope") val unsupportedTimeFirstScope: String? = null,
    @ColumnInfo(name = "unsupported_time_first_track_ordinal") val unsupportedTimeFirstTrackOrdinal: Int? = null,
    @ColumnInfo(name = "unsupported_time_first_segment_ordinal") val unsupportedTimeFirstSegmentOrdinal: Int? = null,
    @ColumnInfo(name = "unsupported_time_first_sample_ordinal") val unsupportedTimeFirstSampleOrdinal: Int? = null,
)

@Entity(
    tableName = "gpx_source_tracks",
    foreignKeys = [ForeignKey(GpxImportEntity::class, ["id"], ["import_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION)],
    indices = [Index(value = ["import_id", "ordinal"], unique = true)],
)
internal data class GpxSourceTrackEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "import_id") val importId: UUID,
    val ordinal: Int,
    val name: String? = null,
    val description: String? = null,
)

@Entity(
    tableName = "gpx_track_segments",
    foreignKeys = [ForeignKey(GpxSourceTrackEntity::class, ["id"], ["track_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION)],
    indices = [Index(value = ["track_id", "ordinal"], unique = true)],
)
internal data class GpxTrackSegmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "track_id") val trackId: UUID,
    val ordinal: Int,
)

@Entity(
    tableName = "gpx_track_samples",
    foreignKeys = [ForeignKey(GpxTrackSegmentEntity::class, ["id"], ["segment_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION)],
    primaryKeys = ["segment_id", "ordinal"],
)
internal data class GpxTrackSampleEntity(
    @ColumnInfo(name = "segment_id") val segmentId: UUID,
    val ordinal: Int,
    val latitude: String,
    val longitude: String,
    val elevation: String? = null,
    @ColumnInfo(name = "time_raw") val timeRaw: String? = null,
    @ColumnInfo(name = "time_status") val timeStatus: String,
    @ColumnInfo(name = "time_epoch_seconds") val timeEpochSeconds: Long? = null,
    @ColumnInfo(name = "time_nanos") val timeNanos: Int? = null,
)

@Entity(
    tableName = "gpx_import_jobs",
    foreignKeys = [
        ForeignKey(TerritoryEntity::class, ["id"], ["territory_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION),
        ForeignKey(ObserverEntity::class, ["id"], ["importing_observer_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION),
        ForeignKey(GpxImportEntity::class, ["id"], ["import_id"], ForeignKey.RESTRICT, ForeignKey.NO_ACTION),
    ],
    indices = [
        Index(value = ["territory_id"]),
        Index(value = ["importing_observer_id"]),
        Index(value = ["original_object_id"], unique = true),
        Index(value = ["import_id"], unique = true),
    ],
)
internal data class GpxImportJobEntity(
    @PrimaryKey @ColumnInfo(name = "request_uuid") val requestUuid: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    @ColumnInfo(name = "importing_observer_id") val importingObserverId: UUID? = null,
    val phase: String,
    val intent: String,
    @ColumnInfo(name = "original_object_id") val originalObjectId: UUID? = null,
    @ColumnInfo(name = "original_sha256") val originalSha256: String? = null,
    @ColumnInfo(name = "original_byte_length") val originalByteLength: Long? = null,
    @ColumnInfo(name = "import_id") val importId: UUID? = null,
)
