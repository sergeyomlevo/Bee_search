package org.beesearch.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
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
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectInUseException
import org.beesearch.app.domain.model.PhysicalObjectSequenceResetBlockedException
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The numbering guarantee, the physical deletion of an unused object and the explicit reset.
 *
 * The scope of every counter is one `Territory + object_type` pair, an ordinary deletion never frees
 * a number, and a reset is permitted only while its scope is provably empty.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalObjectNumberingAndDeletionTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomPhysicalObjectRepository
    private val territoryA = UUID.randomUUID()
    private val territoryB = UUID.randomUUID()
    private val observerId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(
                TerritoryEntity(territoryA, "TA", "A", "R", "D", NOW, NOW),
                TerritoryEntity(territoryB, "TB", "B", "R", "D", NOW, NOW),
            ),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)),
        )
        repository = newRepository(database)
    }

    @After
    fun tearDown() = database.close()

    // N1
    @Test
    fun freshScopeStartsWithNumberOne() = runBlocking {
        assertEquals(1, createHollow(territoryA).sequenceNumber)
        assertEquals(1, createLogHive(territoryA).sequenceNumber)
    }

    // N2
    @Test
    fun deletingTheLatestObjectDoesNotFreeItsNumber() = runBlocking {
        createHollow(territoryA)
        val second = createHollow(territoryA)

        repository.deleteHollow(second.id)

        assertEquals(3, createHollow(territoryA).sequenceNumber)
    }

    // N3
    @Test
    fun deletingTheHighestObjectKeepsTheCounterAboveIt() = runBlocking {
        val created = (1..4).map { createHollow(territoryA) }
        assertEquals(listOf(1, 2, 3, 4), created.map { it.sequenceNumber })

        repository.deleteHollow(created.last().id)

        assertEquals(5, createHollow(territoryA).sequenceNumber)
    }

    // N4
    @Test
    fun deletingEveryObjectWithoutResetKeepsTheCounter() = runBlocking {
        val created = (1..4).map { createHollow(territoryA) }
        created.forEach { repository.deleteHollow(it.id) }

        assertEquals(5, createHollow(territoryA).sequenceNumber)
    }

    // N5 + R11 + R12
    @Test
    fun explicitResetStartsANewLineFromNumberOne() = runBlocking {
        val created = (1..4).map { createHollow(territoryA) }
        created.forEach { repository.deleteHollow(it.id) }

        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)
        val reset = createHollow(territoryA)

        assertEquals(1, reset.sequenceNumber)
        assertEquals("Дупло 1", reset.designation)
        assertFalse(created.any { it.id == reset.id })
    }

    // N6
    @Test
    fun deletingAMiddleObjectKeepsTheCounterAboveTheHighestIssued() = runBlocking {
        val created = (1..4).map { createHollow(territoryA) }
        repository.deleteHollow(created[1].id)

        assertEquals(listOf(1, 3, 4), repository.listForTerritory(territoryA).hollows.map { it.sequenceNumber })
        assertEquals(5, createHollow(territoryA).sequenceNumber)
    }

    // N7 + R4
    @Test
    fun hollowAndLogHiveCountersAreIndependent() = runBlocking {
        repeat(3) { createHollow(territoryA) }
        repeat(2) { createLogHive(territoryA) }

        repository.listForTerritory(territoryA).hollows.forEach { repository.deleteHollow(it.id) }
        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)

        assertEquals(1, createHollow(territoryA).sequenceNumber)
        assertEquals(3, createLogHive(territoryA).sequenceNumber)
    }

    // N8 + R5
    @Test
    fun territoryCountersAreIndependent() = runBlocking {
        repeat(7) { createHollow(territoryB) }
        repeat(4) { createHollow(territoryA) }
        repository.listForTerritory(territoryA).hollows.forEach { repository.deleteHollow(it.id) }

        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)

        assertEquals(1, createHollow(territoryA).sequenceNumber)
        assertEquals(7, database.physicalObjectSequenceDao().getLastIssued(territoryB, PhysicalObjectType.HOLLOW))
        assertEquals(8, createHollow(territoryB).sequenceNumber)
    }

    // N9
    @Test
    fun failedCreationDoesNotConsumeANumber() = runBlocking {
        val first = createHollow(territoryA)
        // The same object id again violates the primary key inside the creation transaction, so the
        // allocation made earlier in that transaction must roll back with it.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.createHollow(NewHollow(first.id, territoryA, observerId, 56.2, 42.8, hollowProperties())) }
        }

        assertEquals(1, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
        assertEquals(2, createHollow(territoryA).sequenceNumber)
    }

    @Test
    fun rejectedMediaDoesNotConsumeANumber() = runBlocking {
        val hollowId = UUID.randomUUID()
        val invalidMedia = org.beesearch.app.domain.model.PhysicalObjectMedia(
            UUID.randomUUID(), hollowId, org.beesearch.app.domain.model.PhysicalObjectMediaType.IMAGE,
            "p/one.jpg", "one.jpg", "image/jpeg", 0, "a".repeat(64), NOW,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createHollow(
                    NewHollow(hollowId, territoryA, observerId, 56.1, 42.7, hollowProperties(), listOf(invalidMedia)),
                )
            }
        }

        assertNull(repository.getHollow(hollowId))
        assertEquals(1, createHollow(territoryA).sequenceNumber)
    }

    // N10
    @Test
    fun reopeningTheDatabaseKeepsTheCounter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "counter-${UUID.randomUUID()}.db")
        try {
            val first = Room.databaseBuilder(context, BeeSearchDatabase::class.java, file.absolutePath)
                .allowMainThreadQueries().build()
            first.backupDao().insertTerritories(listOf(TerritoryEntity(territoryB, "TB", "B", "R", "D", NOW, NOW)))
            first.backupDao().insertObservers(listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)))
            val beforeRestart = newRepository(first)
            repeat(3) { createHollowWith(beforeRestart, territoryB) }
            beforeRestart.deleteHollow(beforeRestart.listForTerritory(territoryB).hollows.last().id)
            first.close()

            val reopened = Room.databaseBuilder(context, BeeSearchDatabase::class.java, file.absolutePath)
                .allowMainThreadQueries().build()
            try {
                assertEquals(4, createHollowWith(newRepository(reopened), territoryB).sequenceNumber)
            } finally {
                reopened.close()
            }
        } finally {
            file.delete()
        }
    }

    // N11
    @Test
    fun apiaryAllocationUsesTheSameMechanism() = runBlocking {
        assertEquals(1, repository.createApiary(territoryA, 56.1, 42.7, null).sequenceNumber)
        assertEquals(2, repository.createApiary(territoryA, 56.2, 42.8, "Пасека").sequenceNumber)
        assertEquals(1, repository.createApiary(territoryB, 56.3, 42.9, null).sequenceNumber)
        assertEquals(2, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.APIARY))
    }

    // N12
    @Test
    fun duplicateDesignationIsStillRejectedByTheUniqueIndex() = runBlocking {
        val first = createHollow(territoryA)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.backupDao().insertPhysicalObjects(
                    listOf(
                        PhysicalObjectEntity(
                            UUID.randomUUID(), territoryA, PhysicalObjectType.HOLLOW, first.sequenceNumber,
                            56.1, 42.7, NOW, observerId,
                        ),
                    ),
                )
            }
        }
        Unit
    }

    // D1 + D6 + D7 + D13 + D15
    @Test
    fun deletingAnUnusedHollowRemovesOwnedRowsAndKeepsTheCounter() = runBlocking {
        val keep = createHollow(territoryA)
        val remove = createHollow(territoryA)
        val withMediaId = UUID.randomUUID()
        val media = org.beesearch.app.domain.model.PhysicalObjectMedia(
            UUID.randomUUID(), withMediaId, org.beesearch.app.domain.model.PhysicalObjectMediaType.IMAGE,
            "physical-object-media/$withMediaId/photo", "photo.jpg", "image/jpeg", 10, "d".repeat(64), NOW,
        )
        val withMedia = createHollow(territoryA, listOf(media), withMediaId)

        val deleted = repository.deleteHollow(withMedia.id)

        assertEquals(listOf(media.relativePath), deleted.mediaRelativePaths)
        assertNull(repository.getHollow(withMedia.id))
        assertNull(database.physicalObjectDao().getHollow(withMedia.id))
        assertTrue(database.physicalObjectDao().getMedia(withMedia.id).isEmpty())
        assertNotNull(repository.getHollow(keep.id))
        assertNotNull(repository.getHollow(remove.id))
        assertEquals(3, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
    }

    // D2
    @Test
    fun unusedLogHiveDeleteSucceeds() = runBlocking {
        val logHive = createLogHive(territoryA)
        repository.deleteLogHive(logHive.id)

        assertNull(repository.getLogHive(logHive.id))
        assertTrue(repository.listForTerritory(territoryA).logHives.isEmpty())
    }

    // D4 + D5
    @Test
    fun objectReferencedByABeeCannotBeDeleted() = runBlocking {
        val beeId = seedBee()
        val hollow = createHollow(territoryA)
        repository.setBeeSourceObject(beeId, hollow.id)
        val before = repository.getHollow(hollow.id)

        assertThrows(PhysicalObjectInUseException::class.java) {
            runBlocking { repository.deleteHollow(hollow.id) }
        }

        assertEquals(before, repository.getHollow(hollow.id))
        assertEquals(hollow.id, repository.getBeeSourceObjectId(beeId))
        assertEquals(1, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
    }

    @Test
    fun deletingAnUnknownOrMissTypedObjectReportsNotFound() = runBlocking {
        val logHive = createLogHive(territoryA)

        assertThrows(EntityNotFoundException::class.java) {
            runBlocking { repository.deleteHollow(logHive.id) }
        }
        assertThrows(EntityNotFoundException::class.java) {
            runBlocking { repository.deleteHollow(UUID.randomUUID()) }
        }
        assertNotNull(repository.getLogHive(logHive.id))
    }

    // D11 + D12 (durable effect on the stored state)
    @Test
    fun deletedObjectDisappearsFromTheStoredState() = runBlocking {
        val hollow = createHollow(territoryA)
        repository.deleteHollow(hollow.id)

        assertTrue(repository.listForTerritory(territoryA).hollows.isEmpty())
        assertEquals(0, database.physicalObjectDao().countInScope(territoryA, PhysicalObjectType.HOLLOW))
        assertTrue(database.backupDao().physicalObjects().isEmpty())
    }

    // R1 + R2 + R6
    @Test
    fun resetSucceedsOnAnEmptyScopeAndIsANoOpWithoutASequenceRow() = runBlocking {
        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)

        assertEquals(1, createHollow(territoryA).sequenceNumber)

        repository.deleteHollow(repository.listForTerritory(territoryA).hollows.single().id)
        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)
        repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW)

        assertEquals(1, createHollow(territoryA).sequenceNumber)
    }

    // R3
    @Test
    fun resetIsBlockedWhileTheScopeStillContainsAnObject() = runBlocking {
        (1..4).map { createHollow(territoryA) }
        val remaining = repository.listForTerritory(territoryA).hollows.first { it.sequenceNumber == 2 }
        repository.listForTerritory(territoryA).hollows.filter { it.id != remaining.id }
            .forEach { repository.deleteHollow(it.id) }

        assertThrows(PhysicalObjectSequenceResetBlockedException::class.java) {
            runBlocking { repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW) }
        }

        assertEquals(4, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
        assertEquals(5, createHollow(territoryA).sequenceNumber)
    }

    // R7
    @Test
    fun resetIsBlockedWhenTheCounterContradictsStoredData() = runBlocking {
        createHollow(territoryA)
        val second = createHollow(territoryA)
        // Hand-edited state: the counter is below a stored number, so the scope is not provably safe.
        database.physicalObjectSequenceDao().setLastIssued(territoryA, PhysicalObjectType.HOLLOW, 1)

        assertThrows(PhysicalObjectSequenceResetBlockedException::class.java) {
            runBlocking { repository.resetSequence(territoryA, PhysicalObjectType.HOLLOW) }
        }

        assertEquals(1, database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
        assertNotNull(repository.getHollow(second.id))
    }

    @Test
    fun resetOfAnUnknownTerritoryIsBlocked() = runBlocking {
        assertThrows(PhysicalObjectSequenceResetBlockedException::class.java) {
            runBlocking { repository.resetSequence(UUID.randomUUID(), PhysicalObjectType.HOLLOW) }
        }
        Unit
    }

    /** The sequence row of a Territory must not make an otherwise deletable Territory undeletable. */
    @Test
    fun deletingATerritoryRemovesItsSequenceRows() = runBlocking {
        val hollow = createHollow(territoryA)
        repository.deleteHollow(hollow.id)
        assertNotNull(database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
        val territories = RoomTerritoryRepository(database, database.territoryDao(), Clock.fixed(NOW, ZoneOffset.UTC))

        territories.deleteTerritory(territoryA)

        assertNull(database.territoryDao().getById(territoryA))
        assertNull(database.physicalObjectSequenceDao().getLastIssued(territoryA, PhysicalObjectType.HOLLOW))
    }

    private fun newRepository(target: BeeSearchDatabase) = RoomPhysicalObjectRepository(
        target,
        target.physicalObjectDao(),
        target.physicalObjectSequenceDao(),
        target.territoryDao(),
        target.observerDao(),
        target.beeDao(),
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private suspend fun createHollow(
        territoryId: UUID,
        media: List<org.beesearch.app.domain.model.PhysicalObjectMedia> = emptyList(),
        objectId: UUID = UUID.randomUUID(),
    ): Hollow = createHollowWith(repository, territoryId, media, objectId)

    private suspend fun createHollowWith(
        target: RoomPhysicalObjectRepository,
        territoryId: UUID,
        media: List<org.beesearch.app.domain.model.PhysicalObjectMedia> = emptyList(),
        objectId: UUID = UUID.randomUUID(),
    ): Hollow = target.createHollow(
        NewHollow(objectId, territoryId, observerId, 56.1, 42.7, hollowProperties(), media),
    )

    private suspend fun createLogHive(territoryId: UUID) = repository.createLogHive(
        NewLogHive(UUID.randomUUID(), territoryId, observerId, 56.2, 42.8, logHiveProperties()),
    )

    private suspend fun seedBee(): UUID {
        val pointId = UUID.randomUUID()
        val beeId = UUID.randomUUID()
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    pointId, territoryA, observerId, 2026, 1, BeePresenceResult.BEES_FOUND,
                    null, 56.0, 42.0, null, null, null, NOW, NOW, null,
                ),
            ),
        )
        database.backupDao().insertBees(listOf(BeeEntity(beeId, pointId, "WHITE", MarkPosition.THORAX, NOW)))
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
        val NOW: Instant = Instant.parse("2026-09-24T08:00:00Z")
    }

    private fun hollowProperties() = HollowProperties("дуб", 180.0, 123, 40.0, 25.0, "note")

    private fun logHiveProperties() = LogHiveProperties("сосна", 150.0, 90, 50.0, "липа", 30.0, 80.0, null)
}
