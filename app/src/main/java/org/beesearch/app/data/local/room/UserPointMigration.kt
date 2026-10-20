package org.beesearch.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only. Existing tables, GPX triggers and rows are untouched. */
internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `user_points` (
            `id` TEXT NOT NULL, `territory_id` TEXT NOT NULL, `title` TEXT NOT NULL,
            `note` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL,
            `is_visible` INTEGER NOT NULL DEFAULT 1, `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`territory_id`) REFERENCES `territories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_points_territory_id` ON `user_points` (`territory_id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `user_point_photos` (
            `id` TEXT NOT NULL, `user_point_id` TEXT NOT NULL, `relative_path` TEXT NOT NULL,
            `original_file_name` TEXT, `mime_type` TEXT, `byte_size` INTEGER NOT NULL,
            `sha256` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`user_point_id`) REFERENCES `user_points`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_point_photos_user_point_id` ON `user_point_photos` (`user_point_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_point_photos_relative_path` ON `user_point_photos` (`relative_path`)")
    }
}
