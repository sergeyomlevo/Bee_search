package org.beesearch.app.data.repository

import android.content.Context
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
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.media.FileAwarePhysicalObjectDeletion
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectInUseException
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectReference
import org.beesearch.app.domain.model.PhysicalObjectReferenceKind
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
 * The structured reason a Physical Object cannot be deleted.
 *
 * Blocking is not a Boolean: the user has to learn which data is involved and how much of it, while a
 * refused deletion must leave every owned row, every owned byte and every referencing record exactly
 * as it was.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalObjectDeletionBlockerTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomPhysicalObjectRepository
    private lateinit var deletion: FileAwarePhysicalObjectDeletion
    private lateinit var store: PhysicalObjectMediaFileStore
    private val filesRoot = File(System.getProperty("java.io.tmpdir"), "blocker-media-${UUID.randomUUID()}")
    private val cacheRoot = File(System.getProperty("java.io.tmpdir"), "blocker-cache-${UUID.randomUUID()}")
    private val territoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        filesRoot.mkdirs()
        cacheRoot.mkdirs()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(TerritoryEntity(territoryId, "T", "Territory", "R", "D", NOW, NOW)),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)),
        )
        store = PhysicalObjectMediaFileStore(filesRoot, cacheRoot)
        repository = newRepository()
        deletion = FileAwarePhysicalObjectDeletion(repository, store)
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun unreferencedObjectDeletesItsOwnedRowsAndMediaAndKeepsTheNumberLine() = runBlocking {
        val (hollow, mediaFile) = createHollowWithMedia()
        assertTrue(mediaFile.isFile)
        val issued = database.physicalObjectSequenceDao().getLastIssued(territoryId, PhysicalObjectType.HOLLOW)

        val outcome = deletion.deleteHollow(hollow.id)

        assertTrue(outcome.fileCleanupComplete)
        assertNull(repository.getHollow(hollow.id))
        assertTrue(database.physicalObjectDao().getMedia(hollow.id).isEmpty())
        assertFalse(mediaFile.exists())
        assertEquals(
            "an ordinary deletion never frees a number",
            issued,
            database.physicalObjectSequenceDao().getLastIssued(territoryId, PhysicalObjectType.HOLLOW),
        )
    }

    @Test
    fun oneReferencingBeeReportsOneBlockerAndRemovesNothing() = runBlocking {
        val (hollow, mediaFile) = createHollowWithMedia()
        val beeId = seedBee(1)
        repository.setBeeSourceObject(beeId, hollow.id)

        val error = assertThrows(PhysicalObjectInUseException::class.java) {
            runBlocking { deletion.deleteHollow(hollow.id) }
        }

        assertEquals(
            listOf(PhysicalObjectReference(PhysicalObjectReferenceKind.BEE, 1)),
            error.references,
        )
        assertNotNull(repository.getHollow(hollow.id))
        assertTrue(database.physicalObjectDao().getHollow(hollow.id) != null)
        assertEquals(1, database.physicalObjectDao().getMedia(hollow.id).size)
        assertTrue(mediaFile.isFile)
        assertEquals(hollow.id, repository.getBeeSourceObjectId(beeId))
    }

    @Test
    fun severalReferencingBeesReportTheirExactCount() = runBlocking {
        val logHive = createLogHive()
        val bees = (1..3).map { pointNumber ->
            seedBee(pointNumber).also { beeId -> repository.setBeeSourceObject(beeId, logHive.id) }
        }

        val error = assertThrows(PhysicalObjectInUseException::class.java) {
            runBlocking { repository.deleteLogHive(logHive.id) }
        }

        assertEquals(1, error.references.size)
        assertEquals(PhysicalObjectReferenceKind.BEE, error.references.single().kind)
        assertEquals(3, error.references.single().count)
        assertEquals(3, database.backupDao().bees().count { it.sourceObjectId == logHive.id })
        assertEquals(3, database.physicalObjectDao().countBeeReferences(logHive.id))
        assertNotNull(repository.getLogHive(logHive.id))
        // The referencing records themselves are untouched, including their flight history.
        bees.forEach { beeId ->
            assertEquals(1, database.flightCycleDao().countForBee(beeId))
            assertEquals(logHive.id, repository.getBeeSourceObjectId(beeId))
        }
    }

    @Test
    fun objectBecomesDeletableAfterTheReferenceIsRemovedThroughTheModel() = runBlocking {
        val (hollow, mediaFile) = createHollowWithMedia()
        val beeId = seedBee(1)
        repository.setBeeSourceObject(beeId, hollow.id)
        assertThrows(PhysicalObjectInUseException::class.java) {
            runBlocking { repository.deleteHollow(hollow.id) }
        }

        repository.setBeeSourceObject(beeId, null)

        assertTrue(deletion.deleteHollow(hollow.id).fileCleanupComplete)
        assertNull(repository.getHollow(hollow.id))
        assertFalse(mediaFile.exists())
        assertNull(repository.getBeeSourceObjectId(beeId))
    }

    private fun newRepository() = RoomPhysicalObjectRepository(
        database,
        database.physicalObjectDao(),
        database.physicalObjectSequenceDao(),
        database.territoryDao(),
        database.observerDao(),
        database.beeDao(),
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private suspend fun createHollowWithMedia(): Pair<Hollow, File> {
        val hollowId = UUID.randomUUID()
        val mediaId = UUID.randomUUID()
        val relativePath = PhysicalObjectMediaFileStore.relativePath(hollowId, mediaId)
        val managed = store.resolve(relativePath)
        managed.parentFile?.mkdirs()
        managed.writeText("media-bytes")
        val media = PhysicalObjectMedia(
            mediaId, hollowId, PhysicalObjectMediaType.IMAGE, relativePath, "photo.jpg",
            "image/jpeg", managed.length(), "a".repeat(64), NOW,
        )
        val hollow = repository.createHollow(
            NewHollow(
                hollowId, territoryId, observerId, 56.1, 42.7,
                HollowProperties("дуб", 180.0, 123, 40.0, 25.0, null), listOf(media),
            ),
        )
        return hollow to managed
    }

    private suspend fun createLogHive() = repository.createLogHive(
        NewLogHive(
            UUID.randomUUID(), territoryId, observerId, 56.2, 42.8,
            LogHiveProperties("сосна", 250.0, 90, 45.0, "сосна", 30.0, 120.0, null),
        ),
    )

    private suspend fun seedBee(pointNumber: Int): UUID {
        val pointId = UUID.randomUUID()
        val beeId = UUID.randomUUID()
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    java.time.LocalDate.of(2026, 9, 24),
                    pointId, territoryId, observerId, 2026, pointNumber, BeePresenceResult.BEES_FOUND,
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
}
