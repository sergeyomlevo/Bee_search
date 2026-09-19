package org.beesearch.app

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.TerritoryInUseException
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryCoverageDeletionTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val valueA = MapAreaCodec.encodeLegacy(listOf(MapGeoBounds(10.0, 20.0, 0.0, 0.0)))
    private val valueB = MapAreaCodec.encodeLegacy(listOf(MapGeoBounds(30.0, 40.0, 20.0, 20.0)))

    @Test fun `deleting unused territory removes only its area value`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a, b))
        val areas = FakeMapAreaStore(mapOf(a to valueA, b to valueB))

        TerritoryCoverageDeletion(territories, areas).delete(a)

        assertFalse(territories.contains(a))
        assertNull(areas.snapshot(a))
        assertEquals(valueB, areas.snapshot(b))
    }

    @Test fun `blocked territory deletion keeps its area value`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a), usedTerritoryIds = setOf(a))
        val areas = FakeMapAreaStore(mapOf(a to valueA))

        try {
            TerritoryCoverageDeletion(territories, areas).delete(a)
            throw AssertionError("Expected TerritoryInUseException")
        } catch (_: TerritoryInUseException) {
            // Expected: the preliminary check happens before map infrastructure is changed.
        }

        assertTrue(territories.contains(a))
        assertEquals(valueA, areas.snapshot(a))
    }

    @Test fun `failed delete restores the exact stored value`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a), failDeletion = true)
        val areas = FakeMapAreaStore(mapOf(a to valueA))

        try {
            TerritoryCoverageDeletion(territories, areas).delete(a)
            throw AssertionError("Expected delete failure")
        } catch (_: IllegalStateException) {
            // Expected: Room deletion failed after the preflight check.
        }

        assertTrue(territories.contains(a))
        assertEquals(valueA, areas.snapshot(a))
    }

    @Test fun `a damaged value is restored as well, not silently dropped`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a), failDeletion = true)
        val areas = FakeMapAreaStore(mapOf(a to "v2|{"))

        try {
            TerritoryCoverageDeletion(territories, areas).delete(a)
            throw AssertionError("Expected delete failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals("v2|{", areas.snapshot(a))
    }

    @Test fun `editing territory metadata preserves the area value with the same id`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a))
        val areas = FakeMapAreaStore(mapOf(a to valueA))

        territories.updateTerritory(
            Territory(a, "A01", "Новое имя", "Регион", "Район", Instant.EPOCH, Instant.EPOCH),
        )

        assertEquals(valueA, areas.snapshot(a))
    }

    private class FakeMapAreaStore(initial: Map<UUID, String>) : MapAreaStore {
        private val values = initial.toMutableMap()

        override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
            MapAreaCodec.decode(values[territoryId])

        override suspend fun create(
            territoryId: UUID,
            name: String,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = throw UnsupportedOperationException()

        override suspend fun updateBounds(
            territoryId: UUID,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = throw UnsupportedOperationException()

        override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult =
            throw UnsupportedOperationException()

        override suspend fun delete(territoryId: UUID): MapAreaChangeResult =
            throw UnsupportedOperationException()

        override suspend fun clear(territoryId: UUID) {
            values.remove(territoryId)
        }

        override suspend fun snapshot(territoryId: UUID): String? = values[territoryId]

        override suspend fun restore(territoryId: UUID, value: String?) {
            if (value == null) values.remove(territoryId) else values[territoryId] = value
        }
    }

    private class FakeTerritoryRepository(
        ids: Set<UUID>,
        private val usedTerritoryIds: Set<UUID> = emptySet(),
        private val failDeletion: Boolean = false,
    ) : TerritoryRepository {
        private val ids = ids.toMutableSet()

        fun contains(id: UUID): Boolean = id in ids

        override fun observeTerritories(): Flow<List<Territory>> = flowOf(emptyList())

        override suspend fun getTerritory(id: UUID): Territory? = null

        override suspend fun createTerritory(code: String, name: String, region: String, district: String): Territory =
            throw UnsupportedOperationException()

        override suspend fun updateTerritory(territory: Territory): Territory = territory

        override suspend fun ensureTerritoryCanBeDeleted(id: UUID) {
            if (id !in ids) throw EntityNotFoundException("Territory")
            if (id in usedTerritoryIds) throw TerritoryInUseException()
        }

        override suspend fun deleteTerritory(id: UUID) {
            ensureTerritoryCanBeDeleted(id)
            if (failDeletion) throw IllegalStateException("Delete failed")
            ids.remove(id)
        }
    }
}
