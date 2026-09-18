package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.CORRUPT_AREA_MESSAGE
import org.beesearch.app.ui.map.DEFAULT_AREA_NAME
import org.beesearch.app.ui.map.EMPTY_AREA_MESSAGE
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaSaveResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Ареал store against a real Preferences DataStore, including the one-time migration of a
 * legacy selection. The DataStore file is a temporary one, never the app's own.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreMapAreaStoreTest {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File.createTempFile("area-", ".preferences_pb")
    private lateinit var dataStore: DataStore<Preferences>

    private val first = MapGeoBounds(north = 10.0, east = 20.0, south = 0.0, west = 0.0)
    private val second = MapGeoBounds(north = 11.0, east = 21.0, south = 1.0, west = 1.0)

    @Before fun setUp() { dataStore = createDataStore() }

    @After fun tearDown() { scope.cancel(); file.delete() }

    private fun createDataStore() = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun store() = DataStoreMapAreaStore(dataStore)

    private fun keyOf(territoryId: UUID) = stringPreferencesKey("map_coverage_$territoryId")

    private suspend fun seedLegacy(territoryId: UUID, bounds: List<MapGeoBounds>) {
        dataStore.edit { it[keyOf(territoryId)] = MapAreaCodec.encodeLegacy(bounds) }
    }

    private fun presentArea(result: MapAreaReadResult) = (result as MapAreaReadResult.Present).area

    @Test fun territoryAreasAreIndependentAndSurviveStoreRecreation() = runBlocking {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val areas = store()

        assertEquals(MapAreaSaveResult.SavedLegacySelection, areas.saveBounds(a, listOf(first), "Лух"))
        assertEquals(MapAreaSaveResult.SavedLegacySelection, areas.saveBounds(b, listOf(second), "Клязьма"))

        assertEquals(listOf(first), presentArea(areas.load(a, "Лух")).bounds)
        assertEquals(listOf(second), presentArea(areas.load(b, "Клязьма")).bounds)

        areas.clear(a)
        assertEquals(MapAreaReadResult.Absent, areas.load(a, "Лух"))
        assertEquals(listOf(second), presentArea(areas.load(b, "Клязьма")).bounds)

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = createDataStore()
        assertEquals(MapAreaReadResult.Absent, store().load(a, "Лух"))
        assertEquals(listOf(second), presentArea(store().load(b, "Клязьма")).bounds)
    }

    @Test fun aLegacySelectionMigratesOnceIntoOneNamedArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first, second))

        val migrated = presentArea(store().load(territoryId, "Лух"))

        assertEquals("Лух", migrated.name)
        assertEquals(listOf(first, second), migrated.bounds)
        assertTrue(store().snapshot(territoryId)!!.startsWith("v2|"))
    }

    @Test fun theMigratedIdIsStableAcrossReadsAndRecreation() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))

        val firstRead = presentArea(store().load(territoryId, "Лух"))
        val secondRead = presentArea(store().load(territoryId, "Лух"))

        assertEquals(firstRead.id, secondRead.id)

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = createDataStore()
        assertEquals(firstRead.id, presentArea(store().load(territoryId, "Лух")).id)
    }

    @Test fun migrationWithoutATerritoryNameFallsBackToTheNeutralName() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))

        assertEquals(DEFAULT_AREA_NAME, presentArea(store().load(territoryId, "   ")).name)
    }

    @Test fun anEmptyLegacySelectionIsNotAnArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        dataStore.edit { it[keyOf(territoryId)] = MapAreaCodec.EMPTY_LEGACY_VALUE }

        assertEquals(MapAreaReadResult.Absent, store().load(territoryId, "Лух"))
        assertEquals(MapAreaCodec.EMPTY_LEGACY_VALUE, store().snapshot(territoryId))
    }

    @Test fun savingBoundsKeepsTheExistingIdAndName() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))
        val migrated = presentArea(store().load(territoryId, "Лух"))

        val saved = store().saveBounds(territoryId, listOf(first, second), "Другое имя") as MapAreaSaveResult.Saved

        assertEquals(migrated.id, saved.area.id)
        assertEquals("Лух", saved.area.name)
        assertEquals(listOf(first, second), presentArea(store().load(territoryId, "Лух")).bounds)
    }

    @Test fun aDamagedValueIsNeitherAbsentNorOverwritten() = runBlocking {
        val territoryId = UUID.randomUUID()
        dataStore.edit { it[keyOf(territoryId)] = "v2|{" }

        val read = store().load(territoryId, "Лух")

        assertTrue("a damaged value must not read as absent", read is MapAreaReadResult.Corrupt)
        assertEquals("v2|{", store().snapshot(territoryId))

        // Saving refuses instead of replacing the damaged value.
        assertEquals(
            MapAreaSaveResult.Refused(CORRUPT_AREA_MESSAGE),
            store().saveBounds(territoryId, listOf(first), "Лух"),
        )
        assertEquals("v2|{", store().snapshot(territoryId))

        // A later read still reports damage rather than an empty selection.
        assertTrue(store().load(territoryId, "Лух") is MapAreaReadResult.Corrupt)
        assertEquals("v2|{", store().snapshot(territoryId))
    }

    @Test fun anExistingAreaRefusesToBecomeEmpty() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))
        store().load(territoryId, "Лух")
        val before = store().snapshot(territoryId)

        assertEquals(
            MapAreaSaveResult.Refused(EMPTY_AREA_MESSAGE),
            store().saveBounds(territoryId, emptyList(), "Лух"),
        )
        assertEquals(before, store().snapshot(territoryId))
        assertEquals(listOf(first), presentArea(store().load(territoryId, "Лух")).bounds)
    }

    @Test fun aTerritoryWithoutAnAreaStillStoresALegacySelection() = runBlocking {
        val territoryId = UUID.randomUUID()

        assertEquals(
            MapAreaSaveResult.SavedLegacySelection,
            store().saveBounds(territoryId, listOf(first), "Лух"),
        )
        assertEquals(MapAreaCodec.encodeLegacy(listOf(first)), store().snapshot(territoryId))
    }

    @Test fun snapshotAndRestorePreserveTheExactValue() = runBlocking {
        val territoryId = UUID.randomUUID()
        store().saveBounds(territoryId, listOf(first), "Лух")
        val saved = store().snapshot(territoryId)

        store().clear(territoryId)
        assertNull(store().snapshot(territoryId))

        store().restore(territoryId, saved)
        assertEquals(saved, store().snapshot(territoryId))
    }

    @Test fun aReadOnlyLoadDoesNotRewriteTheStoredValue() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))
        store().load(territoryId, "Лух")
        val afterMigration = store().snapshot(territoryId)

        store().load(territoryId, "Лух")
        store().load(territoryId, "Лух")

        assertEquals(afterMigration, store().snapshot(territoryId))
    }
}
