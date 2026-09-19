package org.beesearch.app.ui.area

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.local.settings.DataStoreMapAreaStore
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting an Ареал is scoped to the Ареал alone.
 *
 * The Territory, its observations and the installed map package must all survive, so the device-local
 * map intent can be redefined without touching research data or re-importing a map.
 */
@RunWith(AndroidJUnit4::class)
class AreaDeletionScopeTest {
    private lateinit var scope: CoroutineScope
    private lateinit var database: BeeSearchDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferenceFile: File
    private lateinit var packagesRoot: File

    private val territoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()
    private val pointId = UUID.randomUUID()
    private val packageKey = stringPreferencesKey("map_package_active_$territoryId")

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
        preferenceFile = File.createTempFile("area-scope-", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferenceFile })
        packagesRoot = File(context.cacheDir, "area-scope-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        database.close()
        scope.cancel()
        preferenceFile.delete()
        packagesRoot.deleteRecursively()
    }

    private suspend fun seedEverything(): File {
        val clock = Clock.systemUTC()
        database.backupDao().insertTerritories(
            listOf(
                TerritoryEntity(
                    id = territoryId,
                    code = "DEV-BENCH2",
                    name = "DEV Territory",
                    region = "Регион",
                    district = "Район",
                    createdAt = clock.instant(),
                    updatedAt = clock.instant(),
                ),
            ),
        )
        database.backupDao().insertObservers(
            listOf(
                ObserverEntity(
                    id = observerId,
                    code = "OBS1",
                    lastName = "Тестов",
                    firstName = "Наблюдатель",
                    middleName = null,
                    contact = null,
                    createdAt = clock.instant(),
                    updatedAt = clock.instant(),
                ),
            ),
        )
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    id = pointId,
                    territoryId = territoryId,
                    observerId = observerId,
                    observationYear = 2026,
                    pointNumber = 1,
                    beePresenceResult = BeePresenceResult.BEES_FOUND,
                    code = "P1",
                    latitude = 56.5,
                    longitude = 38.5,
                    gpsLatitude = 56.5,
                    gpsLongitude = 38.5,
                    gpsAccuracyM = 5.0,
                    createdAt = clock.instant(),
                    initialGroupReleaseAt = null,
                    completedAt = null,
                ),
            ),
        )
        // An installed map package lives in app storage, pointed at by a device-local key.
        val packageDirectory = File(packagesRoot, "package-${UUID.randomUUID()}").apply { mkdirs() }
        File(packageDirectory, "tester.pmtiles").writeText("installed map bytes")
        dataStore.edit { it[packageKey] = packageDirectory.name }
        return packageDirectory
    }

    @Test fun deletingAnAreaKeepsTerritoryObservationsAndInstalledMap() = runBlocking {
        val packageDirectory = seedEverything()
        val store = DataStoreMapAreaStore(dataStore)
        store.create(territoryId, "Лух", listOf(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)))

        assertEquals(MapAreaChangeResult.Deleted, store.delete(territoryId))

        // The Ареал is gone, so nothing is desired for the offline map any more.
        assertEquals(MapAreaReadResult.Absent, store.load(territoryId, "DEV Territory"))

        // Research data and the installed package are untouched.
        assertEquals(1, database.backupDao().territoryCount())
        assertEquals(1, database.backupDao().observerCount())
        assertEquals(1, database.backupDao().observationPointCount())
        assertEquals(territoryId, database.backupDao().territories().single().id)
        val pointer = dataStore.data.first()[packageKey]
        assertEquals(packageDirectory.name, pointer)
        assertTrue(File(packagesRoot, pointer!!).isDirectory)
        assertTrue(File(packageDirectory, "tester.pmtiles").isFile)
    }

    @Test fun creatingANewAreaAfterDeletionUsesAFreshIdentity() = runBlocking {
        seedEverything()
        val store = DataStoreMapAreaStore(dataStore)
        val firstArea = (store.create(territoryId, "Лух", listOf(MapGeoBounds(57.0, 39.0, 56.0, 38.0))) as MapAreaChangeResult.Saved).area

        store.delete(territoryId)
        val secondArea = (store.create(territoryId, "Клязьма", listOf(MapGeoBounds(57.1, 39.1, 56.1, 38.1))) as MapAreaChangeResult.Saved).area

        assertTrue(firstArea.id != secondArea.id)
        assertEquals("Клязьма", secondArea.name)
        assertEquals(1, database.backupDao().territoryCount())
    }
}
