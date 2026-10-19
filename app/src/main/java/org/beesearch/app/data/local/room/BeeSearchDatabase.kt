package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GpxImportEntity::class,
        GpxDeletionEntity::class,
        GpxSourceTrackEntity::class,
        GpxTrackSegmentEntity::class,
        GpxTrackSampleEntity::class,
        GpxImportJobEntity::class,
        TerritoryEntity::class,
        ObserverEntity::class,
        ObservationPointEntity::class,
        PhysicalObjectEntity::class,
        HollowEntity::class,
        LogHiveEntity::class,
        PhysicalObjectMediaEntity::class,
        PhysicalObjectSequenceEntity::class,
        ApiaryEntity::class,
        BeeEntity::class,
        FlightCycleEntity::class,
        ObservationPointAttachmentEntity::class,
        ObservationPointWeatherEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
internal abstract class BeeSearchDatabase : RoomDatabase() {
    abstract fun gpxDao(): GpxDao
    abstract fun territoryDao(): TerritoryDao
    abstract fun observerDao(): ObserverDao
    abstract fun observationPointDao(): ObservationPointDao
    abstract fun physicalObjectDao(): PhysicalObjectDao
    abstract fun physicalObjectSequenceDao(): PhysicalObjectSequenceDao
    abstract fun beeDao(): BeeDao
    abstract fun flightCycleDao(): FlightCycleDao
    abstract fun observationPointAttachmentDao(): ObservationPointAttachmentDao
    abstract fun observationPointWeatherDao(): ObservationPointWeatherDao
    abstract fun backupDao(): BackupDao

    companion object {
        private const val DATABASE_NAME = "bee_search.db"

        fun create(context: Context): BeeSearchDatabase = Room.databaseBuilder(
            context.applicationContext,
            BeeSearchDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
            )
            .addCallback(GpxPersistenceSchema.callback)
            .build()
    }
}
