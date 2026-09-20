package org.beesearch.app.data.exchange

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The automatic managed Area file in `Exchange/Areas`.
 *
 * The canonical Ареал stays the DataStore value; this file is a mirror the user can find, open and
 * send. These tests fix its lifecycle: created after the first save, updated in place afterwards,
 * recreated when it disappears, removed only with the Ареал it belongs to, and never a reason for a
 * canonical write to fail.
 */
class AreaExchangeMirrorTest {
    private lateinit var root: File

    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val first = MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)
    private val second = MapGeoBounds(north = 56.9, east = 38.9, south = 56.8, west = 38.8)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("bee-search-exchange").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun storage(publicRoot: File = root) =
        BeeSearchExchangeStorage(publicRoot = publicRoot, variantName = "Test")

    private fun mirror(publicRoot: File = root) = AreaExchangeMirror(storage(publicRoot))

    private fun area(name: String = "Лух", bounds: List<MapGeoBounds> = listOf(first)) =
        MapArea(id = areaId, name = name, bounds = bounds)

    private fun managedFile(area: MapArea = area()): File = mirror().managedFile(area)

    private fun areasFolder(): File = storage().directoryOf(ExchangeFolder.AREAS).directory

    private fun readArea(file: File): MapArea =
        (AreaExchangeCodec.decode(file.readText()) as AreaExchangeReadResult.Present).area

    @Test
    fun `the first save of an area creates its managed file`() = runBlocking {
        val result = mirror().sync(area())

        assertEquals(AreaMirrorOutcome.Synced, result)
        val file = managedFile()
        assertTrue(file.absolutePath, file.isFile)
        assertEquals("Лух--7e82a310.json", file.name)
        assertEquals(area(), readArea(file))
    }

    @Test
    fun `changing the bounds updates the same file`() = runBlocking {
        mirror().sync(area(bounds = listOf(first)))
        val file = managedFile()

        mirror().sync(area(bounds = listOf(first, second)))

        // The same path is rewritten: no new file versions accumulate for one Ареал.
        assertEquals(1, areasFolder().listFiles()!!.count { it.name.endsWith(".json") })
        assertEquals(2, readArea(file).bounds.size)
        assertEquals(areaId, readArea(file).id)
    }

    @Test
    fun `adding a участок adds a bound to the file`() = runBlocking {
        mirror().sync(area(bounds = listOf(first)))

        mirror().sync(area(bounds = listOf(first, second)))

        assertEquals(listOf(first, second), readArea(managedFile()).bounds)
    }

    @Test
    fun `removing a участок removes it from the file`() = runBlocking {
        mirror().sync(area(bounds = listOf(first, second)))

        mirror().sync(area(bounds = listOf(second)))

        assertEquals(listOf(second), readArea(managedFile()).bounds)
    }

    @Test
    fun `a missing managed file is recreated by the next sync`() = runBlocking {
        mirror().sync(area())
        val file = managedFile()
        assertTrue(file.delete())

        val result = mirror().sync(area())

        assertEquals(AreaMirrorOutcome.Synced, result)
        assertTrue(file.isFile)
        assertEquals(area(), readArea(file))
    }

    @Test
    fun `an area saved before this iteration gets its file on the first sync`() = runBlocking {
        // Exactly the situation on an upgraded device: the canonical Ареал exists, the file does not.
        val stored = MapAreaCodec.encode(area(bounds = listOf(first, second)))
        assertFalse(managedFile().exists())
        assertTrue(stored.isNotEmpty())

        mirror().sync(area(bounds = listOf(first, second)))

        assertTrue(managedFile().isFile)
        assertEquals(2, readArea(managedFile()).bounds.size)
    }

    @Test
    fun `an unavailable exchange folder is reported but never throws`() = runBlocking {
        // A regular file where the exchange tree must be created: the platform refuses, and the
        // mirror has to say so instead of failing the caller.
        val blockedRoot = File(root, "blocked").apply { writeText("not a directory") }

        val result = mirror(publicRoot = blockedRoot).sync(area())

        assertTrue(result is AreaMirrorOutcome.Failed)
    }

    @Test
    fun `a canonical save still succeeds while the exchange folder is unavailable`() = runBlocking {
        val blockedRoot = File(root, "blocked").apply { writeText("not a directory") }
        val store = MirroringMapAreaStore(
            delegate = FakeAreaStore(),
            mirror = mirror(publicRoot = blockedRoot),
        )

        val result = store.create(TERRITORY, "Лух", listOf(first))

        // The canonical Ареал is the source of truth: a mirror that cannot be written is not an error
        // of the save, and the stored value is still a complete v2 Ареал.
        assertTrue(result is MapAreaChangeResult.Saved)
        assertTrue(store.snapshot(TERRITORY)!!.startsWith("v2|"))
    }

    @Test
    fun `deleting the area removes only its own managed file`() = runBlocking {
        val areas = areasFolder().apply { mkdirs() }
        val foreign = File(areas, "чужой--11111111.json")
        foreign.writeText(
            AreaExchangeCodec.encode(
                MapArea(
                    id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                    name = "Чужой",
                    bounds = listOf(first),
                ),
            ),
        )
        val store = MirroringMapAreaStore(delegate = FakeAreaStore(), mirror = mirror())
        val created = (store.create(TERRITORY, "Лух", listOf(first)) as MapAreaChangeResult.Saved).area
        val managed = mirror().managedFile(created)
        assertTrue(managed.absolutePath, managed.isFile)

        val result = store.delete(TERRITORY)

        assertEquals(MapAreaChangeResult.Deleted, result)
        assertFalse(managed.exists())
        assertTrue("a file of another Ареал must never be deleted", foreign.isFile)
    }

    @Test
    fun `a file carrying another area id is not deleted even at the managed path`() = runBlocking {
        val file = managedFile()
        file.parentFile!!.mkdirs()
        file.writeText(
            AreaExchangeCodec.encode(
                MapArea(
                    id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                    name = "Лух",
                    bounds = listOf(first),
                ),
            ),
        )

        val outcome = mirror().remove(area())

        assertTrue(outcome is AreaMirrorOutcome.Skipped)
        assertTrue(file.isFile)
    }

    @Test
    fun `a file that is not an area file is left alone`() = runBlocking {
        val file = managedFile()
        file.parentFile!!.mkdirs()
        file.writeText("это не файл ареала")

        val outcome = mirror().remove(area())

        assertTrue(outcome is AreaMirrorOutcome.Skipped)
        assertTrue(file.isFile)
    }

    @Test
    fun `removing an absent managed file is a normal outcome`() = runBlocking {
        assertEquals(AreaMirrorOutcome.Removed, mirror().remove(area()))
    }

    @Test
    fun `the store mirrors a save and leaves reading untouched`() = runBlocking {
        val delegate = FakeAreaStore()
        val store = MirroringMapAreaStore(delegate = delegate, mirror = mirror())

        val created = (store.create(TERRITORY, "Лух", listOf(first)) as MapAreaChangeResult.Saved).area
        val writtenBySave = delegate.canonicalWrites
        val file = mirror().managedFile(created)
        assertTrue(file.absolutePath, file.isFile)

        store.load(TERRITORY, "Лух")

        assertEquals(writtenBySave, delegate.canonicalWrites)
        assertTrue(file.isFile)
    }

    @Test
    fun `reading an absent area writes no managed file`() = runBlocking {
        val delegate = FakeAreaStore()
        val store = MirroringMapAreaStore(delegate = delegate, mirror = mirror())

        val read = store.load(TERRITORY, "Лух")

        assertTrue(read is MapAreaReadResult.Absent)
        assertNull(areasFolder().listFiles()?.firstOrNull { it.name.endsWith(".json") })
    }

    private companion object {
        val TERRITORY: UUID = UUID.fromString("48ef6a6c-59d4-4405-838a-b9a40bbe32c0")
    }
}

/** Minimal canonical store: one stored value per Territory, plus the number of canonical writes. */
private class FakeAreaStore : MapAreaStore {
    private val values = mutableMapOf<UUID, String>()

    var canonicalWrites = 0
        private set

    override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
        MapAreaCodec.decode(values[territoryId])

    override suspend fun create(
        territoryId: UUID,
        name: String,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult {
        if (values[territoryId] != null) return MapAreaChangeResult.Refused("Ареал уже создан")
        val area = MapArea(id = UUID.randomUUID(), name = name, bounds = bounds)
        values[territoryId] = MapAreaCodec.encode(area)
        canonicalWrites++
        return MapAreaChangeResult.Saved(area)
    }

    override suspend fun updateBounds(
        territoryId: UUID,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult {
        val current = (MapAreaCodec.decode(values[territoryId]) as? MapAreaReadResult.Present)?.area
            ?: return MapAreaChangeResult.Refused("Ареал не создан")
        val updated = current.copy(bounds = bounds)
        values[territoryId] = MapAreaCodec.encode(updated)
        canonicalWrites++
        return MapAreaChangeResult.Saved(updated)
    }

    override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult {
        val current = (MapAreaCodec.decode(values[territoryId]) as? MapAreaReadResult.Present)?.area
            ?: return MapAreaChangeResult.Refused("Ареал не создан")
        val renamed = current.copy(name = name)
        values[territoryId] = MapAreaCodec.encode(renamed)
        canonicalWrites++
        return MapAreaChangeResult.Saved(renamed)
    }

    override suspend fun delete(territoryId: UUID): MapAreaChangeResult {
        if (values.remove(territoryId) != null) canonicalWrites++
        return MapAreaChangeResult.Deleted
    }

    override suspend fun clear(territoryId: UUID) {
        values.remove(territoryId)
        canonicalWrites++
    }

    override suspend fun snapshot(territoryId: UUID): String? = values[territoryId]

    override suspend fun restore(territoryId: UUID, value: String?) {
        if (value == null) values.remove(territoryId) else values[territoryId] = value
        canonicalWrites++
    }
}
