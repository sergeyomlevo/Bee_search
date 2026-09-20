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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.BLANK_AREA_NAME_MESSAGE
import org.beesearch.app.ui.map.CORRUPT_AREA_MESSAGE
import org.beesearch.app.ui.map.DEFAULT_AREA_NAME
import org.beesearch.app.ui.map.EMPTY_AREA_MESSAGE
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Ареал store against a real Preferences DataStore: the one-time migration of a legacy
 * selection, the final create/update/rename/delete API and the guarantee that every user-facing
 * write is a complete `v2` Ареал. The DataStore file is a temporary one, never the app's own.
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
        dataStore.edit { it[keyOf(territoryId)] = legacyCoverageValue(bounds) }
    }

    /**
     * The legacy `v1` coverage encoding, spelled out here because the app only reads it now: this
     * test needs to seed exactly the value an older version of Bee Search would have left behind.
     */
    private fun legacyCoverageValue(bounds: List<MapGeoBounds>): String = buildString {
        append(MapAreaCodec.LEGACY_VERSION)
        bounds.forEach { bound ->
            append('|').append(bound.north).append(',').append(bound.east)
                .append(',').append(bound.south).append(',').append(bound.west)
        }
    }

    private fun saved(result: MapAreaChangeResult) = (result as MapAreaChangeResult.Saved).area

    private suspend fun reload(territoryId: UUID) =
        (store().load(territoryId, null) as MapAreaReadResult.Present).area

    // ---- migration from the legacy format ------------------------------------------------

    @Test fun aLegacySelectionMigratesOnceIntoOneNamedArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first, second))

        val migrated = reload(territoryId)

        assertEquals(DEFAULT_AREA_NAME, migrated.name)
        assertEquals(listOf(first, second), migrated.bounds)
        assertTrue(store().snapshot(territoryId)!!.startsWith("v2|"))
    }

    @Test fun theMigratedIdIsStableAcrossReadsAndRecreation() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))

        val firstRead = reload(territoryId)
        assertEquals(firstRead.id, reload(territoryId).id)

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = createDataStore()
        assertEquals(firstRead.id, reload(territoryId).id)
    }

    @Test fun anEmptyLegacySelectionIsNotAnArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        dataStore.edit { it[keyOf(territoryId)] = MapAreaCodec.EMPTY_LEGACY_VALUE }

        assertEquals(MapAreaReadResult.Absent, store().load(territoryId, null))
        assertEquals(MapAreaCodec.EMPTY_LEGACY_VALUE, store().snapshot(territoryId))
    }

    // ---- create --------------------------------------------------------------------------

    @Test fun createWritesACompleteV2AreaWithFreshIdentityAndAllBounds() = runBlocking {
        val territoryId = UUID.randomUUID()

        val created = saved(store().create(territoryId, "Лух", listOf(first, second)))

        assertEquals("Лух", created.name)
        assertEquals(listOf(first, second), created.bounds)
        assertTrue(store().snapshot(territoryId)!!.startsWith("v2|"))
        assertEquals(created, reload(territoryId))
    }

    @Test fun eachCreatedAreaGetsItsOwnIdentity() = runBlocking {
        val firstArea = saved(store().create(UUID.randomUUID(), "Лух", listOf(first)))
        val secondArea = saved(store().create(UUID.randomUUID(), "Лух", listOf(first)))

        assertNotEquals(firstArea.id, secondArea.id)
    }

    @Test fun createTrimsTheNameAndRejectsAnEmptyOne() = runBlocking {
        val territoryId = UUID.randomUUID()

        assertEquals("Лух", saved(store().create(territoryId, "  Лух  ", listOf(first))).name)
        assertEquals(
            MapAreaChangeResult.Refused(BLANK_AREA_NAME_MESSAGE),
            store().create(UUID.randomUUID(), "   ", listOf(first)),
        )
    }

    @Test fun createRejectsAnEmptyBoundsListAndWritesNothing() = runBlocking {
        val territoryId = UUID.randomUUID()

        assertEquals(
            MapAreaChangeResult.Refused(EMPTY_AREA_MESSAGE),
            store().create(territoryId, "Лух", emptyList()),
        )
        assertNull(store().snapshot(territoryId))
    }

    @Test fun createRefusesToReplaceAnExistingArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        val existing = saved(store().create(territoryId, "Лух", listOf(first)))

        val result = store().create(territoryId, "Другое", listOf(second))

        assertTrue(result is MapAreaChangeResult.Refused)
        assertEquals(existing, reload(territoryId))
    }

    // ---- update bounds -------------------------------------------------------------------

    @Test fun updateBoundsKeepsIdentityAndName() = runBlocking {
        val territoryId = UUID.randomUUID()
        val created = saved(store().create(territoryId, "Лух", listOf(first)))

        val updated = saved(store().updateBounds(territoryId, listOf(first, second)))

        assertEquals(created.id, updated.id)
        assertEquals("Лух", updated.name)
        assertEquals(listOf(first, second), updated.bounds)
        assertEquals(updated, reload(territoryId))
    }

    /** The old silent data-loss path: an empty draft must never become the stored value. */
    @Test fun updateBoundsRefusesAnEmptyListAndKeepsTheStoredArea() = runBlocking {
        val territoryId = UUID.randomUUID()
        val created = saved(store().create(territoryId, "Лух", listOf(first)))
        val before = store().snapshot(territoryId)

        assertEquals(
            MapAreaChangeResult.Refused(EMPTY_AREA_MESSAGE),
            store().updateBounds(territoryId, emptyList()),
        )
        assertEquals(before, store().snapshot(territoryId))
        assertEquals(created, reload(territoryId))
    }

    @Test fun updateBoundsRefusesWhenThereIsNoArea() = runBlocking {
        val territoryId = UUID.randomUUID()

        assertTrue(store().updateBounds(territoryId, listOf(first)) is MapAreaChangeResult.Refused)
        assertNull(store().snapshot(territoryId))
    }

    @Test fun updateBoundsPreservesOrderAndOverlapExactly() = runBlocking {
        val territoryId = UUID.randomUUID()
        store().create(territoryId, "Лух", listOf(first))
        val overlapping = MapGeoBounds(north = 10.5, east = 20.5, south = -0.5, west = -0.5)
        val bounds = listOf(second, overlapping, first, first)

        val updated = saved(store().updateBounds(territoryId, bounds))

        assertEquals(bounds, updated.bounds)
        assertEquals(bounds, reload(territoryId).bounds)
    }

    // ---- rename --------------------------------------------------------------------------

    @Test fun renameChangesOnlyTheName() = runBlocking {
        val territoryId = UUID.randomUUID()
        val created = saved(store().create(territoryId, "Лух", listOf(first, second)))

        val renamed = saved(store().rename(territoryId, "Клязьма"))

        assertEquals(created.id, renamed.id)
        assertEquals(created.bounds, renamed.bounds)
        assertEquals("Клязьма", renamed.name)
        assertEquals("Клязьма", reload(territoryId).name)
    }

    @Test fun renameTrimsAndRejectsAnEmptyNameWithoutWriting() = runBlocking {
        val territoryId = UUID.randomUUID()
        val created = saved(store().create(territoryId, "Лух", listOf(first)))

        assertEquals("Клязьма", saved(store().rename(territoryId, "  Клязьма  ")).name)
        val afterTrim = store().snapshot(territoryId)
        assertEquals(
            MapAreaChangeResult.Refused(BLANK_AREA_NAME_MESSAGE),
            store().rename(territoryId, "   "),
        )
        assertEquals(afterTrim, store().snapshot(territoryId))
        assertEquals(created.id, reload(territoryId).id)
    }

    @Test fun renameRefusesWhenThereIsNoArea() = runBlocking {
        val territoryId = UUID.randomUUID()

        assertTrue(store().rename(territoryId, "Лух") is MapAreaChangeResult.Refused)
        assertNull(store().snapshot(territoryId))
    }

    // ---- delete --------------------------------------------------------------------------

    @Test fun deleteRemovesOnlyTheCanonicalAreaValue() = runBlocking {
        val territoryId = UUID.randomUUID()
        val other = UUID.randomUUID()
        val packageKey = stringPreferencesKey("map_package_active_$territoryId")
        store().create(territoryId, "Лух", listOf(first))
        store().create(other, "Клязьма", listOf(second))
        dataStore.edit { it[packageKey] = "package-1" }

        assertEquals(MapAreaChangeResult.Deleted, store().delete(territoryId))

        assertNull(store().snapshot(territoryId))
        assertEquals(MapAreaReadResult.Absent, store().load(territoryId, null))
        // Nothing else in the store is affected.
        assertEquals("Клязьма", reload(other).name)
        assertEquals("package-1", dataStore.data.first()[packageKey])
    }

    @Test fun deleteIsIdempotentOnAnAbsentArea() = runBlocking {
        assertEquals(MapAreaChangeResult.Deleted, store().delete(UUID.randomUUID()))
    }

    // ---- corrupt and reversible maintenance ----------------------------------------------

    @Test fun aDamagedValueIsNeitherAbsentNorOverwritten() = runBlocking {
        val territoryId = UUID.randomUUID()
        dataStore.edit { it[keyOf(territoryId)] = "v2|{" }

        assertTrue(store().load(territoryId, null) is MapAreaReadResult.Corrupt)
        assertEquals("v2|{", store().snapshot(territoryId))

        // Every mutation refuses instead of replacing the damaged value.
        assertEquals(
            MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE),
            store().create(territoryId, "Лух", listOf(first)),
        )
        assertEquals(
            MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE),
            store().updateBounds(territoryId, listOf(first)),
        )
        assertEquals(
            MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE),
            store().rename(territoryId, "Клязьма"),
        )
        assertEquals(
            MapAreaChangeResult.Refused(CORRUPT_AREA_MESSAGE),
            store().delete(territoryId),
        )
        assertEquals("v2|{", store().snapshot(territoryId))
        assertTrue(store().load(territoryId, null) is MapAreaReadResult.Corrupt)
    }

    @Test fun aReadOnlyLoadDoesNotRewriteTheStoredValue() = runBlocking {
        val territoryId = UUID.randomUUID()
        seedLegacy(territoryId, listOf(first))
        store().load(territoryId, null)
        val afterMigration = store().snapshot(territoryId)

        store().load(territoryId, null)
        store().load(territoryId, null)

        assertEquals(afterMigration, store().snapshot(territoryId))
    }

    @Test fun snapshotAndRestorePreserveTheExactValue() = runBlocking {
        val territoryId = UUID.randomUUID()
        store().create(territoryId, "Лух", listOf(first))
        val saved = store().snapshot(territoryId)

        store().clear(territoryId)
        assertNull(store().snapshot(territoryId))

        store().restore(territoryId, saved)
        assertEquals(saved, store().snapshot(territoryId))
    }
}
