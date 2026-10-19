package org.beesearch.app.data.local.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** The five-table v15 DDL and its feature-local, versioned structural gate. */
internal object GpxPersistenceSchema {
    private const val PREFIX = "gpx_validate_"
    private const val OWNER = "FOREIGN KEY(territory_id) REFERENCES territories(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(importing_observer_id) REFERENCES observers(id) ON UPDATE NO ACTION ON DELETE RESTRICT"
    private val warningColumns = listOf("unsupported_content", "unsupported_time").flatMap { p -> listOf(
        "${p}_count INTEGER NOT NULL", "${p}_first_scope TEXT", "${p}_first_track_ordinal INTEGER",
        "${p}_first_segment_ordinal INTEGER", "${p}_first_sample_ordinal INTEGER",
    ) }
    val tables: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS gpx_imports (id TEXT NOT NULL PRIMARY KEY, territory_id TEXT NOT NULL, " +
            "importing_observer_id TEXT, original_object_id TEXT NOT NULL, original_sha256 TEXT NOT NULL, " +
            "original_byte_length INTEGER NOT NULL, display_name TEXT, gpx_version TEXT NOT NULL, " +
            "parser_profile_version TEXT NOT NULL, imported_at_epoch_ms INTEGER NOT NULL, creator TEXT NOT NULL, " +
            "source_name TEXT, source_description TEXT, author_name TEXT, publication_state TEXT NOT NULL, request_uuid TEXT, " +
            warningColumns.joinToString(", ") + ", " + OWNER + ")",
        "CREATE TABLE IF NOT EXISTS gpx_source_tracks (id TEXT NOT NULL PRIMARY KEY, import_id TEXT NOT NULL, " +
            "ordinal INTEGER NOT NULL, name TEXT, description TEXT, FOREIGN KEY(import_id) REFERENCES gpx_imports(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS gpx_track_segments (id TEXT NOT NULL PRIMARY KEY, track_id TEXT NOT NULL, " +
            "ordinal INTEGER NOT NULL, FOREIGN KEY(track_id) REFERENCES gpx_source_tracks(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS gpx_track_samples (segment_id TEXT NOT NULL, ordinal INTEGER NOT NULL, " +
            "latitude TEXT NOT NULL, longitude TEXT NOT NULL, elevation TEXT, time_raw TEXT, time_status TEXT NOT NULL, " +
            "time_epoch_seconds INTEGER, time_nanos INTEGER, PRIMARY KEY(segment_id, ordinal), " +
            "FOREIGN KEY(segment_id) REFERENCES gpx_track_segments(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS gpx_import_jobs (request_uuid TEXT NOT NULL PRIMARY KEY, territory_id TEXT NOT NULL, " +
            "importing_observer_id TEXT, phase TEXT NOT NULL, intent TEXT NOT NULL, original_object_id TEXT, " +
            "original_sha256 TEXT, original_byte_length INTEGER, import_id TEXT, " + OWNER + ", " +
            "FOREIGN KEY(import_id) REFERENCES gpx_imports(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    )
    private val indexSpecs = listOf(
        Triple("gpx_imports", "original_object_id", true), Triple("gpx_imports", "request_uuid", true),
        Triple("gpx_imports", "territory_id, publication_state", false), Triple("gpx_imports", "importing_observer_id", false),
        Triple("gpx_imports", "original_sha256", false), Triple("gpx_source_tracks", "import_id, ordinal", true),
        Triple("gpx_track_segments", "track_id, ordinal", true), Triple("gpx_import_jobs", "territory_id", false),
        Triple("gpx_import_jobs", "importing_observer_id", false), Triple("gpx_import_jobs", "original_object_id", true),
        Triple("gpx_import_jobs", "import_id", true),
    )
    val indices = indexSpecs.map { (table, columns, unique) ->
        "CREATE ${if (unique) "UNIQUE " else ""}INDEX IF NOT EXISTS index_${table}_${columns.replace(", ", "_")} ON $table ($columns)"
    }
    private fun integer(field: String) = "typeof(NEW.$field) = 'integer'"
    private fun nonnegative(field: String) = "(${integer(field)} AND NEW.$field >= 0)"
    private fun optionalOrdinal(field: String) = "(NEW.$field IS NULL OR ${nonnegative(field)})"
    private fun warning(p: String): String {
        val fields = listOf("scope", "track_ordinal", "segment_ordinal", "sample_ordinal").map { "${p}_first_$it" }
        return "(${nonnegative("${p}_count")} AND ((NEW.${p}_count = 0 AND " +
            fields.joinToString(" AND ") { "NEW.$it IS NULL" } + ") OR (NEW.${p}_count > 0 AND " +
            "NEW.${p}_first_scope IN ('ROOT','METADATA','AUTHOR','TRACK','SEGMENT','SAMPLE') AND " +
            fields.drop(1).joinToString(" AND ", transform = ::optionalOrdinal) + ")))"
    }
    private val predicates = linkedMapOf(
        "gpx_imports" to "NEW.publication_state IN ('PENDING','READY') AND " +
            "(NEW.publication_state = 'READY' OR NEW.request_uuid IS NULL) AND " +
            "${integer("original_byte_length")} AND NEW.original_byte_length > 0 AND " +
            warning("unsupported_content") + " AND " + warning("unsupported_time"),
        "gpx_source_tracks" to nonnegative("ordinal"),
        "gpx_track_segments" to nonnegative("ordinal"),
        "gpx_track_samples" to nonnegative("ordinal") + " AND ((NEW.time_status = 'MISSING' AND NEW.time_raw IS NULL " +
            "AND NEW.time_epoch_seconds IS NULL AND NEW.time_nanos IS NULL) OR " +
            "(NEW.time_status IN ('NO_ZONE','UNSUPPORTED') AND NEW.time_raw IS NOT NULL " +
            "AND NEW.time_epoch_seconds IS NULL AND NEW.time_nanos IS NULL) OR " +
            "(NEW.time_status = 'UTC' AND NEW.time_raw IS NOT NULL AND ${integer("time_epoch_seconds")} " +
            "AND NEW.time_epoch_seconds BETWEEN -62135596800 AND 253402300799 AND ${integer("time_nanos")} " +
            "AND NEW.time_nanos BETWEEN 0 AND 999999999))",
        "gpx_import_jobs" to "NEW.intent IN ('CONTINUE','ABORT') AND " +
            "NEW.phase IN ('SELECTED','COPYING','COPIED','PARSING','PARSED','FILE_PUBLISHED','READY') AND " +
            "((NEW.phase = 'SELECTED' AND NEW.original_object_id IS NULL AND NEW.original_sha256 IS NULL AND NEW.original_byte_length IS NULL) OR " +
            "(NEW.phase = 'COPYING' AND NEW.original_object_id IS NOT NULL AND NEW.original_sha256 IS NULL AND NEW.original_byte_length IS NULL) OR " +
            "(NEW.phase IN ('COPIED','PARSING','PARSED','FILE_PUBLISHED','READY') AND NEW.original_object_id IS NOT NULL " +
            "AND NEW.original_sha256 IS NOT NULL AND ${integer("original_byte_length")} AND NEW.original_byte_length > 0)) AND " +
            "(NEW.intent = 'ABORT' OR (NEW.phase IN ('SELECTED','COPYING','COPIED') AND NEW.import_id IS NULL) OR " +
            "NEW.phase = 'PARSING' OR (NEW.phase IN ('PARSED','FILE_PUBLISHED','READY') AND NEW.import_id IS NOT NULL)) AND " +
            "(NEW.phase != 'READY' OR NEW.intent = 'CONTINUE')",
    )
    // Keep this exact SQL source shared by fresh creation, migration and verification.
    val triggers: Map<String, String> = buildMap {
        predicates.forEach { (table, predicate) ->
            listOf("INSERT", "UPDATE").forEach { event ->
                val name = PREFIX + table + "_" + event.lowercase(java.util.Locale.ROOT)
                put(name, "CREATE TRIGGER $name BEFORE $event ON $table WHEN ($predicate) IS NOT 1 " +
                    "BEGIN SELECT RAISE(ABORT, 'GPX_STRUCTURE'); END")
            }
        }
    }
    fun migrate(db: SupportSQLiteDatabase) {
        tables.forEach(db::execSQL)
        indices.forEach(db::execSQL)
        installTriggers(db)
    }
    fun installTriggers(db: SupportSQLiteDatabase) { triggers.values.forEach(db::execSQL) }
    fun verify(db: SupportSQLiteDatabase) {
        val actual = linkedMapOf<String, String>()
        db.query("SELECT name, sql FROM sqlite_master WHERE type='trigger'").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name.startsWith(PREFIX)) actual[name] = cursor.getString(1)
            }
        }
        if (db.version >= 16) GpxDeletionSchema.verify(db)
        if (actual != triggers) throw GpxPersistenceIntegrityException("STRUCTURAL_TRIGGERS")
    }
    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) { installTriggers(db); GpxDeletionSchema.install(db) }
    }
}

/** Bounded integrity category: do not expose stored text or platform exceptions. */
internal class GpxPersistenceIntegrityException(reason: String) : IllegalStateException("GPX_PERSISTENCE:$reason")
