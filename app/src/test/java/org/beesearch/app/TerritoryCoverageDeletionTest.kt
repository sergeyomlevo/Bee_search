package org.beesearch.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.TerritoryInUseException
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapCoverageStore
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class TerritoryCoverageDeletionTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val coverageA = listOf(MapCoverageFragment(MapGeoBounds(10.0, 20.0, 0.0, 0.0)))
    private val coverageB = listOf(MapCoverageFragment(MapGeoBounds(30.0, 40.0, 20.0, 20.0)))

    @Test fun `deleting unused territory removes only its coverage`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a, b))
        val coverage = FakeMapCoverageStore(mapOf(a to coverageA, b to coverageB))

        TerritoryCoverageDeletion(territories, coverage).delete(a)

        assertFalse(territories.contains(a))
        assertTrue(coverage.load(a).isEmpty())
        assertEquals(coverageB, coverage.load(b))
    }

    @Test fun `blocked territory deletion keeps its coverage`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a), usedTerritoryIds = setOf(a))
        val coverage = FakeMapCoverageStore(mapOf(a to coverageA))

        try {
            TerritoryCoverageDeletion(territories, coverage).delete(a)
            throw AssertionError("Expected TerritoryInUseException")
        } catch (_: TerritoryInUseException) {
            // Expected: the preliminary check happens before map infrastructure is changed.
        }

        assertTrue(territories.contains(a))
        assertEquals(coverageA, coverage.load(a))
    }

    @Test fun `failed delete restores coverage after its preliminary cleanup`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a), failDeletion = true)
        val coverage = FakeMapCoverageStore(mapOf(a to coverageA))

        try {
            TerritoryCoverageDeletion(territories, coverage).delete(a)
            throw AssertionError("Expected delete failure")
        } catch (_: IllegalStateException) {
            // Expected: Room deletion failed after the preflight check.
        }

        assertTrue(territories.contains(a))
        assertEquals(coverageA, coverage.load(a))
    }

    @Test fun `editing territory metadata preserves coverage with the same id`() = runBlocking {
        val territories = FakeTerritoryRepository(setOf(a))
        val coverage = FakeMapCoverageStore(mapOf(a to coverageA))

        territories.updateTerritory(
            Territory(a, "A01", "Новое имя", "Регион", "Район", Instant.EPOCH, Instant.EPOCH),
        )

        assertEquals(coverageA, coverage.load(a))
    }

    private class FakeMapCoverageStore(initial: Map<UUID, List<MapCoverageFragment>>) : MapCoverageStore {
        private val values = initial.toMutableMap()

        override suspend fun load(territoryId: UUID): List<MapCoverageFragment> = values[territoryId].orEmpty()

        override suspend fun replace(territoryId: UUID, fragments: List<MapCoverageFragment>) {
            values[territoryId] = fragments
        }

        override suspend fun clear(territoryId: UUID) {
            values.remove(territoryId)
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
