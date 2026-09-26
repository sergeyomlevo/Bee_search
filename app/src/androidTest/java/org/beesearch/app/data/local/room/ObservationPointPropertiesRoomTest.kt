package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.WeatherStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ObservationPointPropertiesRoomTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomObservationRepository
    private lateinit var pointId: UUID
    private lateinit var territoryId: UUID
    private lateinit var observerId: UUID

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).allowMainThreadQueries().build()
        val clock = Clock.fixed(Instant.parse("2026-09-12T10:15:00Z"), ZoneOffset.UTC)
        val territory = RoomTerritoryRepository(database, database.territoryDao(), clock).createTerritory("T", "Territory", "R", "D")
        val observer = RoomObserverRepository(database.observerDao(), clock).createObserver("O", "Last", "First", null, null)
        territoryId = territory.id
        observerId = observer.id
        repository = RoomObservationRepository(
            database, database.territoryDao(), database.observationPointDao(), database.observerDao(),
            database.beeDao(), database.flightCycleDao(), database.observationPointAttachmentDao(),
            database.observationPointWeatherDao(), clock, { ZoneOffset.UTC },
        )
        pointId = repository.createObservationPoint(NewObservationPoint(territory.id, observer.id, latitude = 56.1, longitude = 42.7)).id
    }

    @Test
    fun creationPersistsDescriptionAndAttachmentsInTheSamePointTransaction() = runBlocking {
        repository.recordNoBeesFound(pointId)
        val newPointId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val createdAttachment = attachment(
            newPointId,
            "observation-attachments/$newPointId/$attachmentId",
            attachmentId,
        )
        val created = repository.createObservationPoint(
            NewObservationPoint(
                territoryId = territoryId,
                observerId = observerId,
                latitude = 56.2,
                longitude = 42.8,
                id = newPointId,
                description = "Описание из создания",
                attachments = listOf(createdAttachment),
            ),
        )

        val detail = repository.getObservationPointDetail(created.id)!!
        assertEquals("Описание из создания", detail.point.description)
        assertEquals(listOf(createdAttachment), detail.attachments)
    }

    @Test
    fun noBeesCreationPersistsTheSameDraftProperties() = runBlocking {
        repository.recordNoBeesFound(pointId)
        val newPointId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val createdAttachment = attachment(
            newPointId,
            "observation-attachments/$newPointId/$attachmentId",
            attachmentId,
        )
        val created = repository.createObservationPointWithNoBeesFound(
            NewObservationPoint(
                territoryId = territoryId,
                observerId = observerId,
                latitude = 56.2,
                longitude = 42.8,
                id = newPointId,
                description = "Точка без пчёл",
                attachments = listOf(createdAttachment),
            ),
        )

        val detail = repository.getObservationPointDetail(created.id)!!
        assertEquals(org.beesearch.app.domain.model.BeePresenceResult.NO_BEES_FOUND, detail.point.beePresenceResult)
        assertEquals("Точка без пчёл", detail.point.description)
        assertEquals(listOf(createdAttachment), detail.attachments)
    }

    @After fun tearDown() = database.close()

    @Test
    fun descriptionCanBeSavedUpdatedAndCleared() = runBlocking {
        assertNull(repository.getObservationPointDetail(pointId)!!.point.description)
        repository.updateObservationPointDescription(pointId, "Первое описание")
        assertEquals("Первое описание", repository.getObservationPointDetail(pointId)!!.point.description)
        repository.updateObservationPointDescription(pointId, "Обновлено")
        assertEquals("Обновлено", repository.getObservationPointDetail(pointId)!!.point.description)
        repository.updateObservationPointDescription(pointId, "")
        assertNull(repository.getObservationPointDetail(pointId)!!.point.description)
    }

    @Test
    fun attachmentsKeepOwnerAndCanBeRemovedIndividually() = runBlocking {
        val first = attachment(pointId, "points/$pointId/1.jpg")
        val second = attachment(pointId, "points/$pointId/2.jpg")
        repository.insertObservationPointAttachment(first)
        repository.insertObservationPointAttachment(second)
        assertEquals(
            listOf(first, second).sortedWith(compareBy({ it.createdAt }, { it.id.toString() })),
            repository.listObservationPointAttachments(pointId),
        )
        assertEquals(first, repository.deleteObservationPointAttachment(first.id))
        assertEquals(listOf(second), repository.listObservationPointAttachments(pointId))
        assertFalse(database.observationPointAttachmentDao().getById(first.id) != null)
    }

    @Test
    fun weatherPersistsUnitsMetadataAndLoadedIsIdempotent() = runBlocking {
        val sample = Instant.parse("2026-09-12T10:00:00Z")
        val fetched = Instant.parse("2026-09-12T11:00:00Z")
        val loaded = ObservationPointWeather(pointId, WeatherStatus.LOADED, 18.4, 2.1, 247.0, sample, fetched, "Open-Meteo")
        assertTrue(repository.storeLoadedWeather(pointId, loaded))
        assertFalse(repository.storeLoadedWeather(pointId, loaded.copy(temperatureC = 99.0)))
        assertEquals(loaded, repository.getObservationPointWeather(pointId))
        assertTrue(repository.getPendingWeatherRequests().none { it.observationPointId == pointId })
    }

    @Test
    fun selectiveDeleteRemovesAttachmentAndWeatherMetadata() = runBlocking {
        val point = repository.getObservationPointDetail(pointId)!!.point
        repository.insertObservationPointAttachment(attachment(pointId, "points/$pointId/photo.jpg"))
        repository.recordNoBeesFound(pointId)
        repository.deleteCompletedObservationPoint(pointId)
        assertNull(database.observationPointWeatherDao().getByPointId(pointId))
        assertTrue(database.observationPointAttachmentDao().getForPoint(pointId).isEmpty())
        assertNull(database.observationPointDao().getById(point.id))
    }

    @Test
    fun clearObservationDataRemovesPropertyMetadata() = runBlocking {
        repository.insertObservationPointAttachment(attachment(pointId, "points/$pointId/photo.jpg"))
        repository.clearObservationData()
        assertTrue(database.observationPointAttachmentDao().getForPoint(pointId).isEmpty())
        assertNull(database.observationPointWeatherDao().getByPointId(pointId))
    }

    private fun attachment(pointId: UUID, path: String, id: UUID = UUID.randomUUID()) = ObservationPointAttachment(
        id, pointId, AttachmentType.PHOTO, path, "photo.jpg", "image/jpeg", 12L,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Instant.parse("2026-09-12T10:16:00Z"),
    )
}
