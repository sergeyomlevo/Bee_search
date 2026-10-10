package org.beesearch.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.backup.BackupDomainInvariantViolation
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhysicalObjectLegacyBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val at = Instant.parse("2026-08-20T12:00:00Z")

    @Test fun legacyNullPhysicalObjectsRoundTripThroughV6WithoutFabricatedDates() = runBlocking {
        val source = database(); val target = database()
        val output = File(context.cacheDir, UUID.randomUUID().toString() + ".zip")
        try {
            seed(source)
            service(source).export(output, format = 6)
            service(target).restore(output)
            assertEquals(source.backupDao().physicalObjects(), target.backupDao().physicalObjects())
            assertEquals(3, target.backupDao().physicalObjects().size)
            assertTrue(target.backupDao().physicalObjects().all { it.fixationDate == null })
        } finally { source.close(); target.close(); output.delete() }
    }


    @Test fun v8RestorePreservesPhysicalMomentsAndV7RefusesInstantLoss() = runBlocking {
        val source = database(); val target = database()
        val output = File(context.cacheDir, UUID.randomUUID().toString() + ".zip")
        try {
            seed(source)
            val objects = source.backupDao().physicalObjects()
            for (row in objects.filter { it.objectType != PhysicalObjectType.APIARY }) {
                source.openHelper.writableDatabase.execSQL(
                    "UPDATE physical_objects SET fixation_date = ?, fixation_at = ?, updated_at = ? WHERE id = ?",
                    arrayOf("2026-08-20", at.toEpochMilli(), at.plusSeconds(60).toEpochMilli(), row.id.toString()))
            }
            service(source).export(output)
            service(target).restore(output)
            assertEquals(source.backupDao().physicalObjects(), target.backupDao().physicalObjects())
            assertEquals(2, target.backupDao().physicalObjects().count { it.fixationAt == at })
            val previous = output.readBytes()
            assertThrows(BackupDomainInvariantViolation::class.java) { runBlocking { service(source).export(output, format = 7) } }
            assertTrue(previous.contentEquals(output.readBytes()))
            // Date-only V7 remains valid and imports new instant fields as unknown.
            source.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_at = NULL, updated_at = NULL")
            service(source).export(output, format = 7)
            val legacyTarget = database()
            try {
                service(legacyTarget).restore(output)
                assertEquals(source.backupDao().physicalObjects(), legacyTarget.backupDao().physicalObjects())
            } finally { legacyTarget.close() }
        } finally { source.close(); target.close(); output.delete() }
    }

    @Test fun fixationDateRejectsV6BeforeOutputCreationOrOverwrite() = runBlocking {
        val source = database()
        val output = File(context.cacheDir, UUID.randomUUID().toString() + ".zip")
        try {
            seed(source)
            for (point in source.backupDao().physicalObjects()) {
                source.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_date = '2026-08-20' WHERE id = ?", arrayOf(point.id.toString()))
                val error = assertThrows(BackupDomainInvariantViolation::class.java) { runBlocking { service(source).export(output, format = 6) } }
                assertTrue(error.message!!.contains("fixationDate"))
                assertFalse(output.exists())
                val previous = "existing correct output".toByteArray()
                output.writeBytes(previous)
                assertThrows(BackupDomainInvariantViolation::class.java) { runBlocking { service(source).export(output, format = 6) } }
                assertTrue(previous.contentEquals(output.readBytes()))
                output.delete()
                source.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_date = NULL WHERE id = ?", arrayOf(point.id.toString()))
            }
        } finally { source.close(); output.delete() }
    }

    private suspend fun seed(db: BeeSearchDatabase) {
        val t = UUID.randomUUID(); val o = UUID.randomUUID()
        val h = UUID.randomUUID(); val l = UUID.randomUUID(); val a = UUID.randomUUID()
        val dao = db.backupDao()
        dao.insertTerritories(listOf(TerritoryEntity(t, "T", "Territory", "R", "D", at, at)))
        dao.insertObservers(listOf(ObserverEntity(o, "O", "Last", "First", null, null, at, at)))
        dao.insertPhysicalObjects(listOf(PhysicalObjectEntity(h, t, PhysicalObjectType.HOLLOW, 1, 56.0, 42.0, at, o),
            PhysicalObjectEntity(l, t, PhysicalObjectType.LOG_HIVE, 1, 56.0, 42.0, at, o), PhysicalObjectEntity(a, t, PhysicalObjectType.APIARY, 1, 56.0, 42.0, at, o)))
        dao.insertHollows(listOf(HollowEntity(h, null, null, null, null, null, null)))
        dao.insertLogHives(listOf(LogHiveEntity(l, null, null, null, null, null, null, null, null)))
        dao.insertApiaries(listOf(ApiaryEntity(a, "Apiary")))
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
    private fun service(db: BeeSearchDatabase) = BackupService(db, Settings, Clock.systemUTC(), "test")
    private object Settings : PortableSettingsStore {
        override suspend fun snapshot() = PortableSettingsSnapshot(null, null, emptyMap())
        override suspend fun replace(snapshot: PortableSettingsSnapshot) = Unit
    }
}
