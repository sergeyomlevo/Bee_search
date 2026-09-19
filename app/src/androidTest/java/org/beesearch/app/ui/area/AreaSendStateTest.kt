package org.beesearch.app.ui.area

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.exchange.AreaExchangeMirror
import org.beesearch.app.data.exchange.AreaMapDiscovery
import org.beesearch.app.data.exchange.AreaMapDiscoveryResult
import org.beesearch.app.data.exchange.AreaSendResult
import org.beesearch.app.data.exchange.AreaTransport
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
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
 * Sending the Ареал is a read-only user action.
 *
 * Preparing and handing the file to the system may succeed, fail, or be abandoned by the user in the
 * chooser; none of that is allowed to change the Ареал, so this test drives the real screen with a
 * transport that does both and checks that the canonical store is never written.
 */
@RunWith(AndroidJUnit4::class)
class AreaSendStateTest {
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

    @Before
    fun setUp() {
        root = Files.createTempDirectory("bee-search-send").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun transportReturning(result: AreaSendResult) = object : AreaTransport {
        override suspend fun send(area: MapArea): AreaSendResult = result
    }

    private fun show(transport: AreaTransport, store: RecordingAreaStore) {
        val storage = BeeSearchExchangeStorage(root, "Test")
        val mirror = AreaExchangeMirror(storage)
        composeRule.setContent {
            Bee_searchTheme {
                AreaRoute(
                    territory = territory,
                    areaStore = store,
                    areaMirror = mirror,
                    areaTransport = transport,
                    mapPackageStore = NoMapPackageStore,
                    mapDiscovery = NoAreaMapDiscovery,
                    exchangeStorage = storage,
                    onCreate = {},
                    onViewOnMap = {},
                    onBack = {},
                )
            }
        }
    }

    /** The send tests care about the Ареал: no map package is available and none is looked for. */
    private object NoMapPackageStore : MapPackageStore {
        override suspend fun loadActive(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
        ): MapPackageAvailability = MapPackageAvailability.Missing

        override suspend fun import(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
            manifestUri: Uri,
            pmtilesUri: Uri,
        ): MapPackageImportResult = MapPackageImportResult.Rejected("тест не импортирует карту")

        override suspend fun clear(territoryId: UUID) = Unit
    }

    private object NoAreaMapDiscovery : AreaMapDiscovery {
        override suspend fun discover(expectedAreaStem: String): AreaMapDiscoveryResult =
            AreaMapDiscoveryResult.None
    }

    @Test
    fun aFailedSendReportsTheProblemAndChangesNothing() {
        val store = RecordingAreaStore(area)

        show(
            transport = transportReturning(
                AreaSendResult.Failed("Не найдено приложение, которому можно отправить ареал"),
            ),
            store = store,
        )
        composeRule.onNodeWithTag(SEND_AREA_TAG).performClick()

        composeRule.onNodeWithText("Не найдено приложение, которому можно отправить ареал").assertExists()
        assertEquals(0, store.mutations)
    }

    @Test
    fun aHandedOffSendKeepsTheCardAsItWas() {
        val store = RecordingAreaStore(area)

        show(transport = transportReturning(AreaSendResult.HandedOff), store = store)
        composeRule.onNodeWithText("Лух").assertExists()
        composeRule.onNodeWithTag(SEND_AREA_TAG).performClick()

        composeRule.waitForIdle()
        assertEquals(0, store.mutations)
        composeRule.onNodeWithText("Лух").assertExists()
        composeRule.onNodeWithText("Участков: 1").assertExists()
    }

    @Test
    fun theSentAreaIsTheStoredOneEvenAfterSeveralAttempts() {
        val sent = mutableListOf<MapArea>()
        val store = RecordingAreaStore(area)
        val recording = object : AreaTransport {
            override suspend fun send(area: MapArea): AreaSendResult {
                sent += area
                return AreaSendResult.HandedOff
            }
        }

        show(transport = recording, store = store)
        composeRule.onNodeWithTag(SEND_AREA_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SEND_AREA_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(area, area), sent)
        assertEquals(0, store.mutations)
    }

    /** A store that only reads, and counts any attempt to write. */
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
}
