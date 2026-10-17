package org.beesearch.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.util.UUID

@Dao
internal abstract class GpxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPendingRow(row: GpxImportEntity)

    suspend fun insertImport(row: GpxImportEntity) {
        require(row.publicationState == "PENDING" && row.requestUuid == null)
        insertPendingRow(row)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTracks(rows: List<GpxSourceTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSegments(rows: List<GpxTrackSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSamples(rows: List<GpxTrackSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertJob(row: GpxImportJobEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun updateJob(row: GpxImportJobEntity): Int

    @Query("SELECT * FROM gpx_imports WHERE id = :id")
    abstract suspend fun importById(id: UUID): GpxImportEntity?

    @Query("SELECT * FROM gpx_imports WHERE id = :id AND publication_state = 'READY'")
    abstract suspend fun readyImportById(id: UUID): GpxImportEntity?

    @Query("SELECT * FROM gpx_imports WHERE request_uuid = :requestUuid AND publication_state = 'READY'")
    abstract suspend fun completedByRequest(requestUuid: UUID): GpxImportEntity?

    @Query("SELECT COUNT(*) FROM gpx_imports WHERE territory_id = :territoryId AND publication_state = 'READY' AND original_sha256 = :sha COLLATE NOCASE AND original_byte_length = :length")
    abstract suspend fun countRepeatedOriginal(territoryId: UUID, sha: String, length: Long): Long

    @Query("UPDATE gpx_imports SET publication_state = 'READY', request_uuid = :request WHERE id = :id AND publication_state = 'PENDING' AND request_uuid IS NULL")
    abstract suspend fun publishReady(id: UUID, request: UUID): Int

    @Query("DELETE FROM gpx_import_jobs WHERE request_uuid = :request AND import_id = :id AND phase = 'READY' AND intent = 'CONTINUE'")
    abstract suspend fun deleteReadyJob(request: UUID, id: UUID): Int

    @Query("SELECT * FROM gpx_import_jobs WHERE request_uuid = :requestUuid")
    abstract suspend fun jobByRequest(requestUuid: UUID): GpxImportJobEntity?

    @Query("SELECT * FROM gpx_import_jobs ORDER BY request_uuid")
    abstract suspend fun jobs(): List<GpxImportJobEntity>

    @Query("DELETE FROM gpx_import_jobs WHERE request_uuid = :requestUuid AND intent = 'ABORT' AND import_id IS NULL AND phase != 'READY'")
    abstract suspend fun deleteAbortedJob(requestUuid: UUID): Int

    @Query("SELECT * FROM gpx_source_tracks WHERE import_id = :importId ORDER BY ordinal")
    abstract suspend fun tracks(importId: UUID): List<GpxSourceTrackEntity>

    @Query("SELECT * FROM gpx_track_segments WHERE track_id = :trackId ORDER BY ordinal")
    abstract suspend fun segments(trackId: UUID): List<GpxTrackSegmentEntity>

    @Query("SELECT * FROM gpx_track_samples WHERE segment_id = :segmentId ORDER BY ordinal")
    abstract suspend fun samples(segmentId: UUID): List<GpxTrackSampleEntity>

    @Query("SELECT COUNT(*) FROM gpx_imports WHERE territory_id = :territoryId AND publication_state = :state")
    abstract suspend fun countImports(territoryId: UUID, state: String): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM gpx_track_samples AS s
        INNER JOIN gpx_track_segments AS g ON g.id = s.segment_id
        INNER JOIN gpx_source_tracks AS t ON t.id = g.track_id
        INNER JOIN gpx_imports AS i ON i.id = t.import_id
        WHERE i.territory_id = :territoryId AND i.publication_state = :state
        """,
    )
    abstract suspend fun countSamples(territoryId: UUID, state: String): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM gpx_track_samples AS s
        INNER JOIN gpx_track_segments AS g ON g.id = s.segment_id
        INNER JOIN gpx_source_tracks AS t ON t.id = g.track_id
        WHERE t.import_id = :importId
        """,
    )
    abstract suspend fun countImportSamples(importId: UUID): Long

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM gpx_imports WHERE territory_id = :territoryId)
             + (SELECT COUNT(*) FROM gpx_import_jobs WHERE territory_id = :territoryId)
        """,
    )
    abstract suspend fun countTerritoryReferences(territoryId: UUID): Long

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM gpx_imports WHERE importing_observer_id = :observerId)
             + (SELECT COUNT(*) FROM gpx_import_jobs WHERE importing_observer_id = :observerId)
        """,
    )
    abstract suspend fun countObserverReferences(observerId: UUID): Long

    @Query(
        """
        DELETE FROM gpx_track_samples
        WHERE segment_id IN (
            SELECT s.id
            FROM gpx_track_segments AS s
            INNER JOIN gpx_source_tracks AS t ON t.id = s.track_id
            WHERE t.import_id = :importId
        )
        """,
    )
    abstract suspend fun deleteSamples(importId: UUID): Int

    @Query(
        """
        DELETE FROM gpx_track_segments
        WHERE track_id IN (SELECT id FROM gpx_source_tracks WHERE import_id = :importId)
        """,
    )
    abstract suspend fun deleteSegments(importId: UUID): Int

    @Query("DELETE FROM gpx_source_tracks WHERE import_id = :importId")
    abstract suspend fun deleteTracks(importId: UUID): Int

    @Query("DELETE FROM gpx_imports WHERE id = :importId AND publication_state = 'PENDING'")
    abstract suspend fun deletePendingImport(importId: UUID): Int
}
