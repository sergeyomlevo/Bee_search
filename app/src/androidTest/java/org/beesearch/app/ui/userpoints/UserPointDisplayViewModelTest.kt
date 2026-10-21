package org.beesearch.app.ui.userpoints

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPointDisplayViewModelTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private suspend fun ready(vm: UserPointDisplayViewModel) =
        withTimeout(5_000) { vm.state.first { !it.loading } }

    private suspend fun pointState(vm: UserPointDisplayViewModel, id: UUID, visible: Boolean) =
        withTimeout(5_000) { vm.state.first { state -> state.points.any { it.id == id && it.isVisible == visible } } }

    private fun point(id: UUID, territory: UUID, visible: Boolean = false) = UserPoint(
        id = id, territoryId = territory, title = id.toString(), note = null,
        latitude = 56.8, longitude = 60.6, isVisible = visible,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private class FakeRepository : UserPointRepository {
        private val values = ConcurrentHashMap<UUID, MutableStateFlow<List<UserPoint>>>()
        private val persisted = ConcurrentHashMap<UUID, UserPoint>()
        var failWrites = false
        var writeGate: CompletableDeferred<Unit>? = null
        var suppressVisibilityFlow = false
        var firstReadStarted: CompletableDeferred<Unit>? = null
        var firstReadGate: CompletableDeferred<Unit>? = null
        private var readCount = 0

        fun seed(vararg points: UserPoint) {
            points.forEach { persisted[it.id] = it }
            points.groupBy { it.territoryId }.forEach { (territory, rows) ->
                values.getOrPut(territory) { MutableStateFlow(emptyList()) }.value = rows
            }
        }

        override fun observePoints(territoryId: UUID): Flow<List<UserPoint>> {
            val source = values.getOrPut(territoryId) { MutableStateFlow(emptyList()) }
            val read = readCount++
            return if (read == 0 && firstReadGate != null) flow {
                firstReadStarted?.complete(Unit)
                withContext(NonCancellable) { firstReadGate!!.await() }
                emit(source.value)
            } else source
        }

        override suspend fun getPoint(territoryId: UUID, id: UUID) =
            persisted[id]?.takeIf { it.territoryId == territoryId }

        override suspend fun setVisible(territoryId: UUID, id: UUID, visible: Boolean) {
            writeGate?.let { withContext(NonCancellable) { it.await() } }
            if (failWrites) throw IOException("write")
            val saved = persisted[id]?.takeIf { it.territoryId == territoryId }
                ?: error("missing")
            val updated = saved.copy(isVisible = visible, updatedAt = Instant.now())
            persisted[id] = updated
            if (!suppressVisibilityFlow) {
                values[territoryId]?.value = values[territoryId]!!.value.map { if (it.id == id) updated else it }
            }
        }

        override suspend fun create(point: UserPoint) = point
        override suspend fun update(point: UserPoint) = point
        override suspend fun delete(territoryId: UUID, id: UUID): UserPoint = error("unused")
    }

    @Test
    fun territoryFilteringAndStaleNonCooperativeReadCannotCrossAtoBtoA() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a), point(second, b))
        val vm = UserPointDisplayViewModel(repository)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.firstReadStarted = entered
        repository.firstReadGate = release
        onMain { vm.setTerritory(a) }
        withTimeout(5_000) { entered.await() }
        onMain { vm.setTerritory(b) }
        assertEquals(b, ready(vm).territoryId)
        release.complete(Unit)
        onMain { vm.setTerritory(a) }
        val final = ready(vm)
        assertEquals(a, final.territoryId)
        assertEquals(listOf(first), final.points.map { it.id })
        onMain { vm.setTerritory(null) }
    }

    @Test
    fun delayedFailedWriteIsNotOptimisticRetryWorksAndSeveralPointsStayOn() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a), point(second, a))
        val vm = UserPointDisplayViewModel(repository)
        onMain { vm.setTerritory(a) }
        ready(vm)
        repository.writeGate = CompletableDeferred()
        onMain { vm.setVisible(first, true) }
        withTimeout(5_000) { vm.state.first { first in it.writing } }
        assertFalse(vm.state.value.points.first { it.id == first }.isVisible)
        repository.failWrites = true
        repository.writeGate!!.complete(Unit)
        withTimeout(5_000) { vm.state.first { it.writeErrors[first] == true } }
        assertFalse(vm.state.value.points.first { it.id == first }.isVisible)

        repository.failWrites = false
        onMain { vm.retryVisibility(first) }
        pointState(vm, first, true)
        onMain { vm.setVisible(second, true) }
        pointState(vm, second, true)
        assertTrue(vm.state.value.points.all { it.isVisible })
        onMain { vm.setTerritory(null) }
    }

    @Test
    fun persistedVisibilityIsReadByANewViewModel() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a))
        val original = UserPointDisplayViewModel(repository)
        onMain { original.setTerritory(a) }
        ready(original)
        onMain { original.setVisible(first, true) }
        pointState(original, first, true)
        onMain { original.setTerritory(null) }

        val reloaded = UserPointDisplayViewModel(repository)
        onMain { reloaded.setTerritory(a) }
        assertTrue(ready(reloaded).points.single().isVisible)
        onMain { reloaded.setTerritory(null) }
    }

    @Test
    fun showOnMapRequiresPersistedOnAndFailureLeavesOffAndUnselected() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a), point(second, a))
        val vm = UserPointDisplayViewModel(repository)
        onMain { vm.setTerritory(a) }
        ready(vm)
        repository.failWrites = true
        assertNull(vm.showOnMap(first))
        assertFalse(vm.state.value.points.single { it.id == first }.isVisible)
        assertNull(vm.state.value.selectedId)
        assertEquals(true, vm.state.value.writeErrors[first])

        repository.failWrites = false
        val shown = vm.showOnMap(first)
        assertEquals(first, shown?.id)
        assertTrue(vm.state.value.points.single { it.id == first }.isVisible)
        assertEquals(first, vm.state.value.selectedId)
        onMain { vm.setTerritory(null) }
    }

    @Test
    fun canceledShowDoesNotReplaySelectionAfterTerritoryLeaves() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a))
        repository.writeGate = CompletableDeferred()
        val vm = UserPointDisplayViewModel(repository)
        onMain { vm.setTerritory(a) }
        ready(vm)
        val operation = async { vm.showOnMap(first) }
        withTimeout(5_000) { vm.state.first { first in it.writing } }
        onMain { vm.setTerritory(null) }
        repository.writeGate!!.complete(Unit)
        try {
            operation.await()
        } catch (_: CancellationException) {
            // Cancellation is expected when the owning territory leaves.
        }
        assertNull(vm.state.value.selectedId)
        assertNull(vm.state.value.territoryId)
    }

    @Test
    fun delayedShowAfterAtoBtoADoesNotApplyStaleStateOrCameraIntent() = runBlocking {
        val repository = FakeRepository()
        repository.seed(point(first, a), point(second, b))
        repository.writeGate = CompletableDeferred()
        repository.suppressVisibilityFlow = true
        val vm = UserPointDisplayViewModel(repository)
        onMain { vm.setTerritory(a) }
        ready(vm)

        val operation = async { vm.showOnMap(first) }
        withTimeout(5_000) { vm.state.first { first in it.writing } }

        onMain { vm.setTerritory(b) }
        assertEquals(b, ready(vm).territoryId)
        onMain { vm.setTerritory(a) }
        val reentered = ready(vm)
        assertEquals(a, reentered.territoryId)
        assertFalse(reentered.points.single { it.id == first }.isVisible)
        assertNull(reentered.selectedId)

        repository.writeGate!!.complete(Unit)
        val cameraTarget = operation.await()
        assertNull(cameraTarget)
        val final = vm.state.value
        assertEquals(a, final.territoryId)
        assertFalse(final.points.single { it.id == first }.isVisible)
        assertNull(final.selectedId)
        assertTrue(final.writing.isEmpty())
        assertTrue(final.writeErrors.isEmpty())
        onMain { vm.setTerritory(null) }
    }
}
