package org.beesearch.app.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.ui.map.DEFAULT_MAP_DATA_DISPLAY
import org.beesearch.app.ui.map.MapDataDisplayCodec
import org.beesearch.app.ui.map.MapDataType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Данные на карте» store against a real Preferences DataStore: the two owner decisions of the
 * approved specification — state belongs to one Territory and survives a restart — plus the rule that
 * the default state is never written. The DataStore file is a temporary one, never the app's own.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreMapDataDisplayStoreTest {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File.createTempFile("map-data-", ".preferences_pb")
    private lateinit var dataStore: DataStore<Preferences>

    private val territoryA = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val territoryB = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private fun store() = DataStoreMapDataDisplayStore(dataStore)

    private fun keyOf(territoryId: UUID) = stringPreferencesKey("map_data_display_$territoryId")

    @Test
    fun aTerritoryWithoutSavedStateUsesTheApprovedDefault() = runBlocking {
        val state = store().load(territoryA)
        assertEquals(DEFAULT_MAP_DATA_DISPLAY, state)
        MapDataType.entries.forEach { type ->
            assertTrue(state.isVisible(type))
            assertNull(state.period(type))
        }
    }

    @Test
    fun stateOfOneTerritoryNeverMixesWithAnother() = runBlocking {
        val store = store()
        val stateA = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withVisibility(MapDataType.LOG_HIVE, false)
        val stateB = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, may2026)

        store.save(territoryA, stateA)
        store.save(territoryB, stateB)

        assertEquals(stateA, store.load(territoryA))
        assertEquals(stateB, store.load(territoryB))
        // Returning to the first Territory restores exactly its own state.
        assertEquals(stateA, store.load(territoryA))
        assertFalse(store.load(territoryA).isVisible(MapDataType.LOG_HIVE))
        assertTrue(store.load(territoryB).isVisible(MapDataType.LOG_HIVE))
    }

    @Test
    fun savingTheDefaultStateWritesNoEntry() = runBlocking {
        val store = store()
        store.save(territoryA, DEFAULT_MAP_DATA_DISPLAY)
        assertNull(dataStore.data.first()[keyOf(territoryA)])

        // A non-default value is stored, and returning to the default removes it again.
        store.save(territoryA, DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.HOLLOW, may2026))
        assertTrue(dataStore.data.first()[keyOf(territoryA)]!!.isNotEmpty())
        store.save(territoryA, DEFAULT_MAP_DATA_DISPLAY)
        assertNull(dataStore.data.first()[keyOf(territoryA)])
        assertEquals(DEFAULT_MAP_DATA_DISPLAY, store.load(territoryA))
    }

    @Test
    fun aDamagedValueReadsAsTheDefaultAndIsReplacedByTheNextSave() = runBlocking {
        val store = store()
        dataStore.edit { it[keyOf(territoryA)] = "v1|{broken" }
        assertEquals(DEFAULT_MAP_DATA_DISPLAY, store.load(territoryA))

        val state = DEFAULT_MAP_DATA_DISPLAY.withPeriod(MapDataType.LOG_HIVE, may2026)
        store.save(territoryA, state)
        assertEquals(state, store.load(territoryA))
        assertEquals(
            state,
            MapDataDisplayCodec.decode(dataStore.data.first()[keyOf(territoryA)]),
        )
    }

    @Test
    fun savedStateSurvivesStoreRecreation() = runBlocking {
        val state = DEFAULT_MAP_DATA_DISPLAY
            .withVisibility(MapDataType.HOLLOW, false)
            .withPeriod(MapDataType.HOLLOW, may2026)
        store().save(territoryA, state)

        // A new store over the same file is what a process restart looks like.
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

        val restored = store().load(territoryA)
        assertEquals(state, restored)
        assertEquals(may2026, restored.period(MapDataType.HOLLOW))
        assertFalse(restored.isVisible(MapDataType.HOLLOW))
    }
}
