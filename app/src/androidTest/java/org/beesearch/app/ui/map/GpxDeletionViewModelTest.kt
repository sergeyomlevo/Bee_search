package org.beesearch.app.ui.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import org.beesearch.app.data.local.room.GpxReadyMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Focused ViewModel checks for the durable deletion boundary. */
@RunWith(AndroidJUnit4::class)
class GpxDeletionViewModelTest {
    private val territoryA = UUID.randomUUID()
    private val territoryB = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private suspend fun loaded(vm: GpxDisplayViewModel) =
        withTimeout(5_000) { vm.state.first { !it.loading } }

    private suspend fun row(vm: GpxDisplayViewModel, id: UUID, predicate: (GpxDisplayRow) -> Boolean) =
        withTimeout(5_000) { vm.state.first { state -> state.rows.firstOrNull { it.id == id }?.let(predicate) == true } }

    private suspend fun overlays(vm: GpxDisplayViewModel, count: Int) =
        withTimeout(5_000) { vm.overlays.first { it.size == count } }

    private class Store : GpxVisibilityStore {
        val values = mutableMapOf<UUID, Set<UUID>>()
        override suspend fun load(territoryId: UUID) = values[territoryId].orEmpty()
        override suspend fun setVisible(territoryId: UUID, importId: UUID, visible: Boolean) {
            val old = values[territoryId].orEmpty()
            values[territoryId] = if (visible) old + importId else old - importId
        }
    }

    private fun metadata(vararg ids: UUID) = ids.mapIndexed { index, id ->
        GpxReadyMetadata(id, "track-$index.gpx", index.toLong())
    }

    private fun vm(
        store: GpxVisibilityStore = Store(),
        catalog: suspend (UUID) -> List<GpxReadyMetadata> = { metadata(first, second) },
        prepare: suspend (UUID, UUID, Long) -> GpxPreparedOverlay = { _, id, revision ->
            GpxPreparedOverlay(id, revision, "{}")
        },
        delete: suspend (UUID, UUID) -> Unit = { _, _ -> },
        deletions: MutableSharedFlow<UUID> = MutableSharedFlow(extraBufferCapacity = 8),
    ): Pair<GpxDisplayViewModel, MutableSharedFlow<UUID>> =
        GpxDisplayViewModel(catalog, prepare, store, delete, deletions) to deletions

    @Test
    fun precommitFailureLeavesTheRowVisibleWithDeleteError(): Unit = runBlocking {
        val (vm, _) = vm(delete = { _, _ -> throw IOException("before Room commit") })
        onMain { vm.setTerritory(territoryA) }
        loaded(vm)
        onMain { vm.deleteImport(first) }
        val failed = row(vm, first) { it.deleteError != null && !it.deleting }
        assertEquals("Не удалось удалить трек", failed.rows.first { it.id == first }.deleteError)
        assertTrue(vm.state.value.rows.any { it.id == first })
    }

    @Test
    fun secondDeleteRequestIsRejectedWhileTheFirstOperationIsBusy(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val (vm, _) = vm(delete = { _, _ ->
            calls++
            entered.complete(Unit)
            release.await()
        })
        onMain { vm.setTerritory(territoryA) }
        loaded(vm)
        onMain { vm.deleteImport(first); vm.deleteImport(first) }
        withTimeout(5_000) { entered.await() }
        assertEquals(1, calls)
        assertTrue(vm.state.value.rows.first { it.id == first }.deleting)
        release.complete(Unit)
        withTimeout(5_000) { vm.state.first { state -> state.rows.none { it.id == first } } }
    }

    @Test
    fun confirmedDeletionRemovesOnlyTheSelectedRowAndOverlay(): Unit = runBlocking {
        val store = Store().also { it.values[territoryA] = setOf(first, second) }
        val (vm, _) = vm(store = store)
        onMain { vm.setTerritory(territoryA) }
        loaded(vm)
        overlays(vm, 2)
        onMain { vm.deleteImport(first) }
        withTimeout(5_000) { vm.state.first { it.rows.none { row -> row.id == first } } }
        withTimeout(5_000) { vm.overlays.first { overlays -> overlays.map { it.id } == listOf(second) } }
        assertTrue(vm.state.value.rows.any { it.id == second })
    }

    @Test
    fun deletionEventPreventsLateNonCancellableGeometryFromRepublishing(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val (vm, deletions) = vm(
            store = Store().also { it.values[territoryA] = setOf(first) },
            prepare = { _, id, revision ->
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                GpxPreparedOverlay(id, revision, "late")
            },
        )
        onMain { vm.setTerritory(territoryA) }
        withTimeout(5_000) { entered.await() }
        deletions.emit(first)
        withTimeout(5_000) { vm.state.first { it.rows.none { row -> row.id == first } } }
        release.complete(Unit)
        withTimeout(5_000) { vm.overlays.first { it.isEmpty() } }
        assertTrue(vm.overlays.value.isEmpty())
    }

    @Test
    fun deletionEventDuringCatalogLoadCannotReintroduceTheDeletedRow(): Unit = runBlocking {
        val catalogStarted = CompletableDeferred<Unit>()
        val releaseCatalog = CompletableDeferred<Unit>()
        val (vm, deletions) = vm(
            catalog = {
                catalogStarted.complete(Unit)
                releaseCatalog.await()
                metadata(first, second)
            },
        )
        onMain { vm.setTerritory(territoryA) }
        withTimeout(5_000) { catalogStarted.await() }
        deletions.emit(first)
        releaseCatalog.complete(Unit)
        val loaded = loaded(vm)
        assertTrue(loaded.rows.none { it.id == first })
        assertTrue(loaded.rows.any { it.id == second })
    }

    @Test
    fun deletionCapturedInTerritoryADoesNotAlterTerritoryB(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val (vm, _) = vm(
            catalog = { id -> if (id == territoryA) metadata(first) else metadata(second) },
            delete = { _, _ ->
                entered.complete(Unit)
                release.await()
            },
        )
        onMain { vm.setTerritory(territoryA) }
        loaded(vm)
        onMain { vm.deleteImport(first) }
        withTimeout(5_000) { entered.await() }
        onMain { vm.setTerritory(territoryB) }
        loaded(vm)
        assertTrue(vm.state.value.rows.single().id == second)
        release.complete(Unit)
        withTimeout(5_000) { vm.state.first { it.territoryId == territoryB && it.rows.single().id == second } }
        assertTrue(vm.state.value.rows.single().id == second)
    }

    @Test
    fun staleFirstTerritoryCallbackCannotCrossFreshAToBToAState(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val staleFinished = CompletableDeferred<Unit>()
        var firstPreparation = true
        val (vm, _) = vm(
            store = Store().also { it.values[territoryA] = setOf(first) },
            catalog = { id -> if (id == territoryA) metadata(first) else metadata(second) },
            prepare = { _, id, revision ->
                if (id == first && firstPreparation) {
                    firstPreparation = false
                    entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    staleFinished.complete(Unit)
                }
                GpxPreparedOverlay(id, revision, "{}")
            },
        )
        onMain { vm.setTerritory(territoryA) }
        withTimeout(5_000) { entered.await() }
        onMain { vm.setTerritory(territoryB) }
        loaded(vm)
        onMain { vm.setTerritory(territoryA) }
        loaded(vm)
        assertTrue(vm.state.value.territoryId == territoryA)
        assertTrue(vm.state.value.rows.single().id == first)
        release.complete(Unit)
        withTimeout(5_000) { staleFinished.await() }
        onMain { }
        withTimeout(5_000) { vm.overlays.first { overlays -> overlays.size == 1 } }
        assertEquals(listOf(first), vm.overlays.value.map { it.id })
    }
}
