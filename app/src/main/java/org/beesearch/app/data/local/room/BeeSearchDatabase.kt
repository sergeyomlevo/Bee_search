package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TerritoryEntity::class,
        ObservationPointEntity::class,
        BeeEntity::class,
        FlightCycleEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
internal abstract class BeeSearchDatabase : RoomDatabase() {
    abstract fun territoryDao(): TerritoryDao
    abstract fun observationPointDao(): ObservationPointDao
    abstract fun beeDao(): BeeDao
    abstract fun flightCycleDao(): FlightCycleDao

    companion object {
        private const val DATABASE_NAME = "bee_search.db"

        fun create(context: Context): BeeSearchDatabase = Room.databaseBuilder(
            context.applicationContext,
            BeeSearchDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
}
