package org.beesearch.app.data.objectexport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalObjectCollectionExportSourceTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var objects: RoomPhysicalObjectRepository
    private lateinit var source: RepositoryPhysicalObjectCollectionExportSource

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries().build()
        database.backupDao().insertTerritories(
            listOf(
                TerritoryEntity(TERRITORY_A, "TA", "A", "R", "D", NOW, NOW),
                TerritoryEntity(TERRITORY_B, "TB", "B", "R", "D", NOW, NOW),
            ),
        )
        database.backupDao().insertObservers(
            listOf(
                ObserverEntity(OBSERVER_A, "OA", "A", "A", null, null, NOW, NOW),
                ObserverEntity(OBSERVER_B, "OB", "B", "B", null, null, NOW, NOW),
            ),
        )
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        objects = RoomPhysicalObjectRepository(
            database, database.physicalObjectDao(), database.physicalObjectSequenceDao(),
            database.territoryDao(), database.observerDao(), database.beeDao(), clock,
        )
        source = RepositoryPhysicalObjectCollectionExportSource(
            objects,
            RoomTerritoryRepository(database, database.territoryDao(), clock),
            RoomObserverRepository(database.observerDao(), clock),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun hollowAndLogHiveSourcesStayInsideCurrentTerritoryAndConcreteType() = runBlocking {
        val a1 = objects.createHollow(newHollow(TERRITORY_A, OBSERVER_A))
        val a2 = objects.createHollow(newHollow(TERRITORY_A, OBSERVER_B))
        val otherTerritory = objects.createHollow(newHollow(TERRITORY_B, OBSERVER_A))
        val logHive = objects.createLogHive(newLogHive(TERRITORY_A, OBSERVER_A))

        val hollows = source.load(TERRITORY_A, PhysicalObjectType.HOLLOW)
        val logHives = source.load(TERRITORY_A, PhysicalObjectType.LOG_HIVE)

        assertEquals(setOf(a1.id, a2.id), hollows.objects.map { it.id }.toSet())
        assertTrue(hollows.objects.all { it.type == PhysicalObjectType.HOLLOW && it.territoryId == TERRITORY_A })
        assertTrue(otherTerritory.id !in hollows.objects.map { it.id })
        assertEquals(listOf(logHive.id), logHives.objects.map { it.id })
        assertTrue(hollows.objects.all { it.fixationDate == NOW.atZone(ZoneOffset.UTC).toLocalDate() })
        assertTrue(logHives.objects.all { it.fixationDate == NOW.atZone(ZoneOffset.UTC).toLocalDate() })
        assertTrue(logHives.objects.none { it.id == a1.id || it.id == a2.id })
        assertEquals(setOf(OBSERVER_A, OBSERVER_B), hollows.objects.mapNotNull { it.creatorObserverId }.toSet())

        val unchanged = objects.listForTerritory(TERRITORY_A)
        assertEquals(2, unchanged.hollows.size)
        assertEquals(1, unchanged.logHives.size)
    }

    private fun newHollow(territory: UUID, observer: UUID) = NewHollow(
        UUID.randomUUID(), territory, observer, 56.0, 42.0,
        HollowProperties("дуб", 200.0, 90, 40.0, null, null),
    )

    private fun newLogHive(territory: UUID, observer: UUID) = NewLogHive(
        UUID.randomUUID(), territory, observer, 56.0, 42.0,
        LogHiveProperties("сосна", 100.0, 180, 50.0, "сосна", 30.0, 120.0, null),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-02T10:00:00Z")
        val TERRITORY_A: UUID = UUID.randomUUID()
        val TERRITORY_B: UUID = UUID.randomUUID()
        val OBSERVER_A: UUID = UUID.randomUUID()
        val OBSERVER_B: UUID = UUID.randomUUID()
    }
}
