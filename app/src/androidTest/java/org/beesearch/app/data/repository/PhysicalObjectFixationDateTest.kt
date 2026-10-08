package org.beesearch.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhysicalObjectFixationDateTest {
    private lateinit var db: BeeSearchDatabase
    private lateinit var repo: RoomPhysicalObjectRepository
    private val territory = UUID.randomUUID()
    private val observer = UUID.randomUUID()
    private val clock = CountingClock(Instant.parse("2026-12-31T22:30:00Z"))

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java).build()
        val at = clock.value
        db.backupDao().insertTerritories(listOf(TerritoryEntity(territory, "T", "Territory", "R", "D", at, at)))
        db.backupDao().insertObservers(listOf(ObserverEntity(observer, "O", "Last", "First", null, null, at, at)))
        repo = repository(ZoneOffset.ofHours(3))
    }

    @After fun tearDown() = db.close()

    @Test fun hollowAndLogHiveUseOneInstantAndNumberingIgnoresDate() = runBlocking {
        val firstId = UUID.randomUUID()
        val hollow = repo.createHollow(newHollow(firstId))
        assertEquals(1, clock.calls)
        assertEquals(firstId, hollow.id)
        assertEquals(clock.value, hollow.createdAt)
        assertEquals(LocalDate.of(2027, 1, 1), hollow.fixationDate)
        val logId = UUID.randomUUID()
        val log = repo.createLogHive(newLogHive(logId))
        assertEquals(2, clock.calls)
        assertEquals(logId, log.id)
        assertEquals(clock.value, log.createdAt)
        assertEquals(hollow.fixationDate, log.fixationDate)
        assertEquals(1, hollow.sequenceNumber)
        assertEquals(1, log.sequenceNumber)
        clock.value = Instant.parse("2028-01-02T12:00:00Z")
        assertEquals(2, repo.createHollow(newHollow(UUID.randomUUID())).sequenceNumber)
        assertEquals(2, repo.createLogHive(newLogHive(UUID.randomUUID())).sequenceNumber)
        assertEquals(4, clock.calls)
        assertEquals(hollow, repo.getHollow(hollow.id))
        assertEquals(log, repo.getLogHive(log.id))
    }

    @Test fun negativeOffsetMonthBoundaryUsesLocalDateWithoutChangingInstant() = runBlocking {
        clock.value = Instant.parse("2026-07-01T00:30:00Z")
        val other = repository(ZoneOffset.ofHours(-7))
        val hollow = other.createHollow(newHollow(UUID.randomUUID()))
        val log = other.createLogHive(newLogHive(UUID.randomUUID()))
        assertEquals(LocalDate.of(2026, 6, 30), hollow.fixationDate)
        assertEquals(hollow.fixationDate, log.fixationDate)
        assertEquals(clock.value, hollow.createdAt)
        assertEquals(clock.value, log.createdAt)
        assertEquals(2, clock.calls)
    }

    @Test fun storedDateAndNullRoundTripWithoutTimezoneReinterpretation() = runBlocking {
        val hollow = repo.createHollow(newHollow(UUID.randomUUID()))
        val log = repo.createLogHive(newLogHive(UUID.randomUUID()))
        val apiary = repo.createApiary(territory, 56.1, 42.7, "technical")
        assertNull(apiary.fixationDate)
        assertEquals(apiary, repo.getApiary(apiary.id))
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(hollow, repo.getHollow(hollow.id))
            assertEquals(log, repo.getLogHive(log.id))
            db.openHelper.readableDatabase.query("SELECT fixation_date FROM physical_objects WHERE id = ?", arrayOf(hollow.id.toString())).use {
                assertTrue(it.moveToFirst())
                assertEquals("2027-01-01", it.getString(0))
            }
        } finally { TimeZone.setDefault(oldZone) }
        // Persist actual nullable entities, rather than relying on a decoder fallback.
        for (id in listOf(hollow.id, log.id, apiary.id)) {
            val entity = db.physicalObjectDao().getById(id)!!
            db.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_date = NULL WHERE id = ?", arrayOf(entity.id.toString()))
        }
        assertNull(repo.getHollow(hollow.id)!!.fixationDate)
        assertNull(repo.getLogHive(log.id)!!.fixationDate)
        assertNull(repo.getApiary(apiary.id)!!.fixationDate)
        val all = repo.listForTerritory(territory)
        assertNull(all.hollows.single().fixationDate)
        assertNull(all.logHives.single().fixationDate)
        assertNull(all.apiaries.single().fixationDate)
    }

    private fun repository(zone: ZoneId) = RoomPhysicalObjectRepository(db, db.physicalObjectDao(), db.physicalObjectSequenceDao(),
        db.territoryDao(), db.observerDao(), db.beeDao(), clock, fixationZoneIdProvider = { zone })
    private fun newHollow(id: UUID) = NewHollow(id, territory, observer, 56.1, 42.7, HollowProperties("oak", 1.0, 0, 1.0, null, null))
    private fun newLogHive(id: UUID) = NewLogHive(id, territory, observer, 56.1, 42.7, LogHiveProperties("pine", 1.0, 0, 1.0, "wood", 1.0, 1.0, null))
    private class CountingClock(var value: Instant) : Clock() {
        var calls = 0
        override fun instant(): Instant { calls++; return value }
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
