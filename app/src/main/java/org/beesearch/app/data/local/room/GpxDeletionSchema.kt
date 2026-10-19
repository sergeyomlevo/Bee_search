package org.beesearch.app.data.local.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

internal object GpxDeletionSchema {
    val triggers = buildMap<String, String> {
        for (event in listOf("INSERT", "UPDATE")) {
            fun guard(suffix: String, table: String, predicate: String) {
                val name = "gpx_deletion_${suffix}_${event.lowercase()}"
                put(name, "CREATE TRIGGER $name BEFORE $event ON $table WHEN $predicate BEGIN SELECT RAISE(ABORT, 'GPX_DELETED'); END")
            }
            guard("request", "gpx_import_jobs", "EXISTS(SELECT 1 FROM gpx_deletions WHERE request_uuid = NEW.request_uuid)")
            guard("import", "gpx_imports", "EXISTS(SELECT 1 FROM gpx_deletions WHERE import_id = NEW.id OR original_object_id = NEW.original_object_id OR request_uuid = NEW.request_uuid)")
            guard("state", "gpx_deletions", "(NEW.files_cleaned IN (0,1) AND NEW.settings_cleaned IN (0,1) AND NEW.original_byte_length > 0 AND length(NEW.original_sha256)=64 AND (NEW.proof IS NULL OR length(NEW.proof)<=1024)) IS NOT 1")
        }
    }
    fun install(db: SupportSQLiteDatabase) { triggers.values.forEach(db::execSQL) }
    fun verify(db: SupportSQLiteDatabase) {
        val actual = linkedMapOf<String,String>()
        db.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' AND name LIKE 'gpx_deletion_%'").use { c ->
            while(c.moveToNext()) actual[c.getString(0)] = c.getString(1)
        }
        if (actual != triggers) throw GpxPersistenceIntegrityException("DELETION_TRIGGERS")
    }
}
internal val MIGRATION_15_16 = object : Migration(15,16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS gpx_deletions (import_id TEXT NOT NULL PRIMARY KEY, territory_id TEXT NOT NULL, request_uuid TEXT NOT NULL, original_object_id TEXT NOT NULL, original_sha256 TEXT NOT NULL, original_byte_length INTEGER NOT NULL, proof TEXT, files_cleaned INTEGER NOT NULL, settings_cleaned INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_gpx_deletions_request_uuid ON gpx_deletions(request_uuid)")
        GpxDeletionSchema.install(db)
    }
}
