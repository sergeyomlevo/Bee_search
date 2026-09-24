package org.beesearch.app.ui.area

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.exchange.AreaExchangeMirror
import org.beesearch.app.data.exchange.AreaMapCandidate
import org.beesearch.app.data.exchange.AreaMapDiscovery
import org.beesearch.app.data.exchange.AreaMapDiscoveryResult
import org.beesearch.app.data.exchange.AreaSendResult
import org.beesearch.app.data.exchange.AreaTransport
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.CANCEL_LABEL
import org.beesearch.app.ui.map.CHOOSE_ANOTHER_AREA_MAP_LABEL
import org.beesearch.app.ui.map.LOAD_AREA_MAP_LABEL
import org.beesearch.app.ui.map.MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.beesearch.app.ui.map.MapPackageManifest
import org.beesearch.app.ui.map.MapPackageStore
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `Загрузить карту` on the Ареал card.
 *
 * The button is an entry point into the one existing import flow. A package the app recognises by its
 * file name is only *offered*: nothing is imported or activated until the user confirms, and the
 * package still passes the usual manifest, integrity and coverage checks. The Ареал itself is never
 * touched by any of this.
 */
@RunWith(AndroidJUnit4::class)
class AreaMapLoadingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var root: File

    private val area = MapArea(
        id = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d"),
        name = "Лух",
        bounds = listOf(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)),
    )

    private val territory = Territory(
        id = UUID.fromString("48ef6a6c-59d4-4405-838a-b9a40bbe32c0"),
        code = "DEV-BENCH2",
        name = "DEV Territory",
        region = "Test",
        district = "Test",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val v1 = AreaMapCandidate(1, "Лух--7e82a310--map-v1.pmtiles", "m1.json")
    private val v12 = AreaMapCandidate(12, "Лух--7e82a310--map-v12.pmtiles", "m12.json")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("bee-search-area-map").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun show(
        discovery: AreaMapDiscoveryResult,
        read: MapAreaReadResult = MapAreaReadResult.Present(area),
        importResult: MapPackageImportResult = activated(),
        availability: MapPackageAvailability = MapPackageAvailability.Missing,
        areaStore: MapAreaStore = RecordingAreaStore(area),
    ): FakePackageStore {
        val store = FakePackageStore(importResult, availability)
        val canonicalStore = if (read is MapAreaReadResult.Absent) AbsentAreaStore else areaStore
        composeRule.setContent {
            Bee_searchTheme {
                AreaRoute(
                    territory = territory,
                    areaStore = canonicalStore,
                    areaMirror = AreaExchangeMirror(BeeSearchExchangeStorage(root, "Test")),
                    areaTransport = object : AreaTransport {
                        override suspend fun send(area: MapArea): AreaSendResult = AreaSendResult.HandedOff
                    },
                    mapPackageStore = store,
                    mapDiscovery = object : AreaMapDiscovery {
                        override suspend fun discover(expectedAreaStem: String) = discovery
                    },
                    exchangeStorage = BeeSearchExchangeStorage(root, "Test"),
                    onCreate = {},
                    onViewOnMap = {},
                    onBack = {},
                )
            }
        }
        return store
    }

    private fun activated(): MapPackageImportResult.Activated = MapPackageImportResult.Activated(
        org.beesearch.app.ui.map.ActiveMapPackage(
            manifest = testManifest(),
            pmtilesFile = File(root, "active.pmtiles"),
        ),
    )

    private fun testManifest() = MapPackageManifest(
        schemaVersion = 1,
        packageId = "package-1",
        datasetVersion = "dataset-v1",
        profileId = "bee-search-field",
        profileVersion = "v1",
        styleVersion = "vector-pmtiles-v1",
        coverageFragments = listOf(MapCoverageFragment(MapGeoBounds(57.0, 39.0, 56.0, 38.0))),
        minZoom = 8,
        maxZoom = 15,
        pmtilesFile = "Лух--7e82a310--map-v12.pmtiles",
        pmtilesByteLength = 127,
        pmtilesSha256 = "a".repeat(64),
    )

    @Test
    fun theAreaCardOffersLoadingAMap() {
        show(AreaMapDiscoveryResult.None)

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(LOAD_AREA_MAP_LABEL).assertIsDisplayed()
    }

    @Test
    fun anAbsentAreaOffersNothingToLoad() {
        show(AreaMapDiscoveryResult.None, read = MapAreaReadResult.Absent)

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(LOAD_AREA_MAP_LABEL).assertDoesNotExist()
    }

    @Test
    fun oneFoundMapIsOfferedAndNotImported() {
        val store = show(AreaMapDiscoveryResult.One(v1))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AREA_MAP_FOUND_DIALOG_TAG).assertExists()
        composeRule.onNodeWithText(areaMapFoundTitle("Лух")).assertExists()
        // Nothing happens until the user confirms.
        assertEquals(0, store.importCalls)
    }

    @Test
    fun theConfirmationNamesThePackageReadably() {
        show(AreaMapDiscoveryResult.One(v1))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AREA_MAP_CANDIDATE_TAG).assertExists()
        composeRule.onNodeWithText("Лух--7e82a310--map-v1").assertExists()
    }

    @Test
    fun cancellingTheConfirmationChangesNothing() {
        val store = show(AreaMapDiscoveryResult.One(v1))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(CANCEL_LABEL).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AREA_MAP_FOUND_DIALOG_TAG).assertDoesNotExist()
        assertEquals(0, store.importCalls)
        composeRule.onNodeWithText("Лух").assertExists()
    }

    @Test
    fun confirmingOffersThePairToTheExistingImportFlow() {
        val store = show(AreaMapDiscoveryResult.One(v1))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AREA_MAP_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(1, store.importCalls)
        assertEquals("m1.json", store.lastPair?.first?.lastPathSegment)
        assertEquals("Лух--7e82a310--map-v1.pmtiles", store.lastPair?.second?.lastPathSegment)
    }

    @Test
    fun theNewestVersionIsOfferedWhenSeveralExist() {
        val store = show(AreaMapDiscoveryResult.Several(preferred = v12, alternatives = listOf(v1)))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Лух--7e82a310--map-v12").assertExists()
        // Offering is not activating.
        assertEquals(0, store.importCalls)
    }

    @Test
    fun theOtherVersionsAreReachableFromTheConfirmation() {
        val store = show(AreaMapDiscoveryResult.Several(preferred = v12, alternatives = listOf(v1)))

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AREA_MAP_ALTERNATIVES_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AREA_MAP_ALTERNATIVES_DIALOG_TAG).assertExists()
        composeRule.onNodeWithText("Лух--7e82a310--map-v1").assertExists()
        assertEquals(0, store.importCalls)
    }

    @Test
    fun aMapThatDoesNotCoverTheAreaIsExplainedAndChangesNothing() {
        val areaStore = RecordingAreaStore(area)
        val store = show(
            discovery = AreaMapDiscoveryResult.One(v12),
            importResult = MapPackageImportResult.Rejected(MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE),
            areaStore = areaStore,
        )

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AREA_MAP_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AREA_MAP_COVERAGE_MISMATCH_AREA_MESSAGE).assertExists()
        composeRule.onNodeWithTag(AREA_MAP_CHOOSE_ANOTHER_ACTION_TAG).assertExists()
        composeRule.onNodeWithText(CHOOSE_ANOTHER_MAP_ACTION_LABEL).assertExists()
        assertEquals(1, store.importCalls)
        // The Ареал itself is untouched by a rejected map.
        assertEquals(0, areaStore.mutations)
        composeRule.onNodeWithText("Лух").assertExists()
    }

    @Test
    fun aSuccessfulLoadReportsTheReadyMapOnTheCard() {
        val areaStore = RecordingAreaStore(area)
        val store = show(
            discovery = AreaMapDiscoveryResult.One(v12),
            importResult = activated(),
            availability = MapPackageAvailability.Ready(activated().activePackage),
            areaStore = areaStore,
        )

        composeRule.onNodeWithTag(LOAD_AREA_MAP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AREA_MAP_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AREA_MAP_READY_TAG).assertExists()
        assertEquals(1, store.importCalls)
        // Importing a map never rewrites the Ареал.
        assertEquals(0, areaStore.mutations)
        composeRule.onNodeWithText("Участков: 1").assertExists()
    }

    @Test
    fun theOtherAreaActionsRemainAvailable() {
        show(AreaMapDiscoveryResult.None)

        composeRule.onNodeWithTag(VIEW_AREA_ON_MAP_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SEND_AREA_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_AREA_TAG).performScrollTo().assertIsDisplayed()
    }

    /** A canonical store that only reads, and counts any attempt to write. */
    private class RecordingAreaStore(private val area: MapArea) : MapAreaStore {
        var mutations = 0
            private set
        override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
            MapAreaReadResult.Present(area)

        override suspend fun create(
            territoryId: UUID,
            name: String,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = mutate()

        override suspend fun updateBounds(
            territoryId: UUID,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = mutate()

        override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult = mutate()

        override suspend fun delete(territoryId: UUID): MapAreaChangeResult {
            mutations++
            return MapAreaChangeResult.Deleted
        }

        override suspend fun clear(territoryId: UUID) {
            mutations++
        }

        override suspend fun snapshot(territoryId: UUID): String? = null

        override suspend fun restore(territoryId: UUID, value: String?) {
            mutations++
        }

        private fun mutate(): MapAreaChangeResult {
            mutations++
            return MapAreaChangeResult.Refused("тест не должен записывать ареал")
        }
    }

    /** A store of a Territory that has no Ареал at all. */
    private object AbsentAreaStore : MapAreaStore {
        override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
            MapAreaReadResult.Absent

        override suspend fun create(
            territoryId: UUID,
            name: String,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = MapAreaChangeResult.Refused("тест не создаёт ареал")

        override suspend fun updateBounds(
            territoryId: UUID,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = MapAreaChangeResult.Refused("тест не изменяет ареал")

        override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult =
            MapAreaChangeResult.Refused("тест не переименовывает ареал")

        override suspend fun delete(territoryId: UUID): MapAreaChangeResult =
            MapAreaChangeResult.Refused("тест не удаляет ареал")

        override suspend fun clear(territoryId: UUID) = Unit

        override suspend fun snapshot(territoryId: UUID): String? = null

        override suspend fun restore(territoryId: UUID, value: String?) = Unit
    }

    private class FakePackageStore(
        private val importResult: MapPackageImportResult,
        private val availability: MapPackageAvailability,
    ) : MapPackageStore {
        var importCalls = 0
            private set
        var lastPair: Pair<Uri, Uri>? = null
            private set

        override suspend fun loadActive(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
        ): MapPackageAvailability = availability

        override suspend fun import(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
            manifestUri: Uri,
            pmtilesUri: Uri,
        ): MapPackageImportResult {
            importCalls++
            lastPair = manifestUri to pmtilesUri
            return importResult
        }

        override suspend fun clear(territoryId: UUID) = Unit
    }
}
