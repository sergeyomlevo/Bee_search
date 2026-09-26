package org.beesearch.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.FlightCycleEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.ObserverInUseException
import org.beesearch.app.domain.model.MarkPosition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPhysicalObjectRepositoryTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomPhysicalObjectRepository
    private val territory1 = UUID.randomUUID()
    private val territory2 = UUID.randomUUID()
    private val creatorObserverId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(
                TerritoryEntity(territory1, "T1", "One", "R", "D", NOW, NOW),
                TerritoryEntity(territory2, "T2", "Two", "R", "D", NOW, NOW),
            ),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(creatorObserverId, "O", "Last", "First", null, null, NOW, NOW)),
        )
        repository = RoomPhysicalObjectRepository(
            database,
            database.physicalObjectDao(),
            database.physicalObjectSequenceDao(),
            database.territoryDao(),
            database.observerDao(),
            database.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun numberingIsIndependentByTypeAndTerritoryAndDesignationIsStable() = runBlocking {
        val firstHollow = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()))
        val secondHollow = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.2, 42.8, hollowProperties()))
        val logHive = repository.createLogHive(NewLogHive(UUID.randomUUID(), territory1, creatorObserverId, 56.3, 42.9, logHiveProperties()))
        val otherTerritoryHollow = repository.createHollow(NewHollow(UUID.randomUUID(), territory2, creatorObserverId, 56.4, 43.0, hollowProperties()))

        assertEquals(1, firstHollow.sequenceNumber)
        assertEquals(2, secondHollow.sequenceNumber)
        assertEquals(1, logHive.sequenceNumber)
        assertEquals(1, otherTerritoryHollow.sequenceNumber)
        assertEquals("Дупло 1", firstHollow.designation)
        assertEquals("Колода 1", logHive.designation)
    }

    @Test
    fun hollowPersistsCreatorSubtypeAndMultipleMedia() = runBlocking {
        val id = UUID.randomUUID()
        val media = listOf(
            PhysicalObjectMedia(UUID.randomUUID(), id, PhysicalObjectMediaType.IMAGE, "p/one.jpg", "one.jpg", "image/jpeg", 10, "a".repeat(64), NOW),
            PhysicalObjectMedia(UUID.randomUUID(), id, PhysicalObjectMediaType.VIDEO, "p/two.mp4", "two.mp4", "video/mp4", 20, "b".repeat(64), NOW),
        )
        val created = repository.createHollow(NewHollow(id, territory1, creatorObserverId, 56.1, 42.7, hollowProperties(), media))
        assertEquals(creatorObserverId, created.creatorObserverId)
        assertEquals(hollowProperties(), created.properties)
        assertEquals(media, created.media)
        assertEquals(media.toSet(), repository.getHollow(id)?.media?.toSet())
    }

    @Test
    fun editKeepsStableIdentityAndCreator() = runBlocking {
        val id = UUID.randomUUID()
        val before = repository.createHollow(NewHollow(id, territory1, creatorObserverId, 56.1, 42.7, hollowProperties()))
        val after = repository.updateHollow(id, HollowProperties("берёза", 181.0, 359, 41.0, null, null))
        assertEquals(id, after.id)
        assertEquals(before.sequenceNumber, after.sequenceNumber)
        assertEquals(before.territoryId, after.territoryId)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.creatorObserverId, after.creatorObserverId)
        assertEquals(359, after.properties?.entranceAzimuthDeg)
    }

    @Test
    fun coordinateEditChangesOnlyCoordinatesAndPreservesIdentityFacts() = runBlocking {
        val hollowId = UUID.randomUUID()
        val media = PhysicalObjectMedia(
            UUID.randomUUID(), hollowId, PhysicalObjectMediaType.IMAGE,
            "p/coordinate.jpg", "coordinate.jpg", "image/jpeg", 10, "c".repeat(64), NOW,
        )
        val hollow = repository.createHollow(
            NewHollow(hollowId, territory1, creatorObserverId, 56.1, 42.7, hollowProperties(), listOf(media)),
        )
        val logHive = repository.createLogHive(
            NewLogHive(UUID.randomUUID(), territory1, creatorObserverId, 56.2, 42.8, logHiveProperties()),
        )

        repository.updateCoordinates(hollow.id, 57.123456, 43.654321)
        repository.updateCoordinates(logHive.id, 57.223456, 43.754321)

        assertEquals(hollow.copy(latitude = 57.123456, longitude = 43.654321), repository.getHollow(hollow.id))
        assertEquals(logHive.copy(latitude = 57.223456, longitude = 43.754321), repository.getLogHive(logHive.id))
    }

    @Test
    fun coordinateEditRejectsInvalidCoordinatesAndNonEditableObjectTypes() = runBlocking {
        val hollow = repository.createHollow(
            NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()),
        )
        val apiary = repository.createApiary(territory1, 56.2, 42.8, "Пасека")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateCoordinates(hollow.id, 91.0, 42.7) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateCoordinates(hollow.id, 56.1, Double.NaN) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateCoordinates(apiary.id, 57.0, 43.0) }
        }

        assertEquals(hollow, repository.getHollow(hollow.id))
        assertEquals(apiary, repository.getApiary(apiary.id))
    }

    @Test
    fun creatorObserverCannotBeDeletedWhilePhysicalObjectKeepsProvenance() = runBlocking {
        repository.createHollow(
            NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()),
        )
        val observers = RoomObserverRepository(database.observerDao(), Clock.fixed(NOW, ZoneOffset.UTC))

        assertThrows(ObserverInUseException::class.java) {
            runBlocking { observers.deleteObserver(creatorObserverId) }
        }
        Unit
    }

    @Test
    fun invalidCoordinateDoesNotInsertOrConsumeSequence() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 91.0, 42.7, hollowProperties()))
            }
        }
        assertEquals(0, database.physicalObjectDao().getForTerritory(territory1).size)
        val valid = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()))
        assertEquals(1, valid.sequenceNumber)
    }

    @Test
    fun azimuthValidationAllowsZeroAndRejectsOutsideRange() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { HollowProperties("дуб", 1.0, -1, 1.0, null, null) }
        assertThrows(IllegalArgumentException::class.java) { HollowProperties("дуб", 1.0, 360, 1.0, null, null) }
        val created = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, HollowProperties("дуб", 1.0, 0, 1.0, null, null)))
        assertEquals(0, created.properties?.entranceAzimuthDeg)
    }

    @Test
    fun apiaryNameIsNullableNonUniqueAndDoesNotDefineIdentity() = runBlocking {
        val first = repository.createApiary(territory1, 56.1, 42.7, "Пасека Иванова")
        val second = repository.createApiary(territory1, 56.2, 42.8, "Пасека Иванова")
        val unnamed = repository.createApiary(territory1, 56.3, 42.9, null)

        assertEquals("Пасека Иванова", first.name)
        assertEquals(first.name, second.name)
        assertEquals("Пасека 1", first.designation)
        assertEquals("Пасека 2", second.designation)
        assertNull(unnamed.name)
        assertEquals(first, repository.getApiary(first.id))
    }

    @Test
    fun explicitBeeAssociationPersistsWithoutChangingFlightCycle() = runBlocking {
        val beeId = seedBeeAndCycle()
        val hollow = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()))
        val before = database.backupDao().flightCycles().single()

        assertNull(repository.getBeeSourceObjectId(beeId))
        val linked = repository.setBeeSourceObject(beeId, hollow.id)

        assertEquals(hollow.id, linked.sourceObjectId)
        assertEquals(hollow.id, repository.getBeeSourceObjectId(beeId))
        assertEquals(before, database.backupDao().flightCycles().single())
    }

    @Test
    fun foreignKeysRestrictLinkedObjectAndApiaryBaseDeletion() = runBlocking {
        val beeId = seedBeeAndCycle()
        val hollow = repository.createHollow(NewHollow(UUID.randomUUID(), territory1, creatorObserverId, 56.1, 42.7, hollowProperties()))
        repository.setBeeSourceObject(beeId, hollow.id)
        val apiary = repository.createApiary(territory1, 56.2, 42.8, null)
        val sqlite = database.openHelper.writableDatabase

        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.execSQL("DELETE FROM physical_objects WHERE id = ?", arrayOf(hollow.id.toString()))
        }
        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.execSQL("DELETE FROM physical_objects WHERE id = ?", arrayOf(apiary.id.toString()))
        }
        Unit
    }

    private suspend fun seedBeeAndCycle(): UUID {
        val observerId = UUID.randomUUID()
        val pointId = UUID.randomUUID()
        val beeId = UUID.randomUUID()
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "BEE", "Last", "First", null, null, NOW, NOW)),
        )
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    pointId, territory1, observerId, 2026, 1, BeePresenceResult.BEES_FOUND,
                    null, 56.0, 42.0, null, null, null, NOW, NOW, null,
                ),
            ),
        )
        database.backupDao().insertBees(
            listOf(BeeEntity(beeId, pointId, "WHITE", MarkPosition.THORAX, NOW)),
        )
        database.backupDao().insertFlightCycles(
            listOf(
                FlightCycleEntity(
                    UUID.randomUUID(), beeId, 1, NOW, NOW.plusSeconds(60), null, false,
                    true, false, NOW, NOW.plusSeconds(60),
                ),
            ),
        )
        return beeId
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-22T08:00:00Z")
    }

    private fun hollowProperties() = HollowProperties("дуб", 180.0, 123, 40.0, 25.0, "note")

    private fun logHiveProperties() = LogHiveProperties("сосна", 150.0, 90, 50.0, "липа", 30.0, 80.0, null)
}
