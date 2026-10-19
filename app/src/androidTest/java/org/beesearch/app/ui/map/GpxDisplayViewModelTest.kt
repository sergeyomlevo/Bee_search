package org.beesearch.app.ui.map

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.room.GpxReadyMetadata
import org.beesearch.app.data.local.settings.DataStoreGpxVisibilityStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxDisplayViewModelTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private fun metadata() = listOf(GpxReadyMetadata(first, "Лесная дорога.gpx", 2), GpxReadyMetadata(second, "К реке.gpx", 1))
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private suspend fun ready(vm: GpxDisplayViewModel) = withTimeout(5000) { vm.state.first { !it.loading } }
    private suspend fun selected(vm: GpxDisplayViewModel, id: UUID, value: Boolean) = withTimeout(5000) {
        vm.state.first { s -> s.rows.any { it.id == id && it.selected == value && !it.saving } }
    }
    private suspend fun prepared(vm: GpxDisplayViewModel, count: Int) = withTimeout(5000) { vm.overlays.first { it.size == count } }
    private class Store : GpxVisibilityStore {
        val values = mutableMapOf<UUID, Set<UUID>>()
        var fail = false
        var delay: CompletableDeferred<Unit>? = null
        override suspend fun load(territoryId: UUID) = values[territoryId].orEmpty()
        override suspend fun setVisible(territoryId: UUID, importId: UUID, visible: Boolean) {
            delay?.await()
            if (fail) throw IOException("write")
            val old = values[territoryId].orEmpty()
            values[territoryId] = if (visible) old + importId else old - importId
        }
    }
    private fun vm(store: GpxVisibilityStore, read: suspend (UUID, UUID, Long) -> GpxPreparedOverlay = { _, id, revision -> GpxPreparedOverlay(id, revision, "{}") },
        catalog: suspend (UUID) -> List<GpxReadyMetadata> = { metadata() }) = GpxDisplayViewModel(catalog, read, store)

    @Test fun newImportsOffAndSeveralOnOffDoesNotChangeCatalog() = runBlocking {
        val store = Store(); val reads = mutableListOf<UUID>()
        val vm = vm(store, read = { _, id, r -> reads.add(id); GpxPreparedOverlay(id, r, "{}") })
        onMain { vm.setTerritory(a) }; assertTrue(ready(vm).rows.none { it.selected }); assertTrue(reads.isEmpty())
        onMain { vm.setVisible(first, true) }; selected(vm, first, true); prepared(vm, 1)
        onMain { vm.setVisible(second, true) }; selected(vm, second, true); prepared(vm, 2)
        onMain { vm.setVisible(first, false) }; selected(vm, first, false); prepared(vm, 1)
        assertEquals(listOf(first, second), reads)
        assertEquals(2, vm.state.value.rows.size)
        assertEquals(setOf(second), store.values[a])
        onMain { vm.setTerritory(null) }
    }

    @Test fun savingMustCompleteBeforeSelectionAndFailureIsNotSuccess() = runBlocking {
        val store = Store(); store.delay = CompletableDeferred(); val vm = vm(store)
        onMain { vm.setTerritory(a) }; ready(vm)
        onMain { vm.setVisible(first, true) }
        withTimeout(5000) { vm.state.first { it.rows.any { row -> row.saving } } }
        assertFalse(vm.state.value.rows.first().selected); assertTrue(vm.overlays.value.isEmpty())
        store.fail = true; store.delay!!.complete(Unit)
        val failed = withTimeout(5000) { vm.state.first { it.rows.first().error != null } }
        assertFalse(failed.rows.first().selected); assertEquals("Не удалось сохранить", failed.rows.first().error)
        store.fail=false; onMain { vm.retry(first) }; selected(vm,first,true); prepared(vm,1)
        assertEquals(setOf(first),store.values[a])
        onMain { vm.setTerritory(null) }
    }

    @Test fun canonicalFailureAndNativeFailureArePerFileAndRetryable() = runBlocking {
        val store = Store(); var fail = true
        val vm = vm(store, read = { _, id, r -> if (id == first && fail) throw IOException("canonical") else GpxPreparedOverlay(id,r,"{}") })
        onMain { vm.setTerritory(a) }; ready(vm)
        onMain { vm.setVisible(first, true); vm.setVisible(second, true) }
        withTimeout(5000) { vm.state.first { it.rows.first().error != null } }; prepared(vm,1)
        assertTrue(vm.state.value.rows.all { it.selected })
        fail = false; onMain { vm.retry(first) }; prepared(vm,2)
        val overlay = vm.overlays.value.first { it.id == first }
        onMain { vm.onPresentation(a, mapOf(overlay.key to OverlayPresentation(overlay.revision,OverlayResult.FACTORY_FAILED))) }
        assertEquals("Не удалось показать трек",vm.state.value.rows.first().error)
        onMain { vm.onPresentation(a, mapOf(overlay.key to OverlayPresentation(overlay.revision,OverlayResult.APPLIED))) }
        assertNull(vm.state.value.rows.first().error); assertFalse(vm.state.value.rows.first().preparing)
        onMain { vm.setTerritory(null) }
    }

    @Test fun lateGeometryAndAcknowledgementsCannotCrossTerritories() = runBlocking {
        val store = Store();store.values[a]=setOf(first)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val vm = vm(store, read = { _, id, r ->
            entered.complete(Unit)
            // Simulate an upstream non-cancellable native/IO completion.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
            GpxPreparedOverlay(id,r,"{}")
        })
        onMain { vm.setTerritory(a) }; withTimeout(5000) { entered.await() }
        onMain { vm.setTerritory(b) }; ready(vm); release.complete(Unit)
        onMain { vm.onPresentation(a,mapOf("gpx-$first" to OverlayPresentation(1,OverlayResult.APPLIED))) }
        assertEquals(b,vm.state.value.territoryId); assertTrue(vm.state.value.rows.none { it.selected }); assertTrue(vm.overlays.value.isEmpty())
        onMain { vm.setTerritory(a) }; ready(vm); prepared(vm,1)
        assertEquals(a,vm.state.value.territoryId); assertTrue(vm.state.value.rows.first().selected)
        onMain { vm.setTerritory(null) }
    }

    @Test fun offOnCannotPublishAnOlderNonCancellablePreparation() = runBlocking {
        val store=Store();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val returned=CompletableDeferred<Unit>()
        var count=0
        val vm=vm(store,read={_,id,revision ->
            count++
            if(count==1) {
                entered.complete(Unit)
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                returned.complete(Unit)
            }
            GpxPreparedOverlay(id,revision,"{}")
        })
        onMain { vm.setTerritory(a) };ready(vm)
        onMain { vm.setVisible(first,true) };withTimeout(5000) { entered.await() }
        onMain { vm.setVisible(first,false) };selected(vm,first,false)
        onMain { vm.setVisible(first,true) };selected(vm,first,true);prepared(vm,1)
        val current=vm.overlays.value.single()
        release.complete(Unit);withTimeout(5000) { returned.await() }
        // Barrier after the old continuation has had its Main-thread publication opportunity.
        onMain { }
        assertEquals(listOf(current),vm.overlays.value)
        onMain { vm.setTerritory(null) }
    }

    @Test fun addingAnImportDoesNotAddItToThePersistedVisibleSet() = runBlocking {
        val store=Store();var catalog=metadata().take(1)
        val vm=vm(store,catalog={catalog})
        onMain { vm.setTerritory(a) };ready(vm)
        onMain { vm.setVisible(first,true) };selected(vm,first,true);prepared(vm,1)
        catalog=metadata();onMain { vm.refresh() };ready(vm);prepared(vm,1)
        assertTrue(vm.state.value.rows.first().selected)
        assertFalse(vm.state.value.rows.last().selected)
        assertEquals(setOf(first),store.values[a])
        onMain { vm.setTerritory(null) }
    }

    @Test fun persistenceSurvivesNewStateOwnerAndTerritorySwitch() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File(context.cacheDir,"gpx-visibility-${UUID.randomUUID()}.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val dataStore=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
        val store=DataStoreGpxVisibilityStore(dataStore)
        try {
            val original=vm(store);onMain { original.setTerritory(a) };ready(original)
            onMain { original.setVisible(first,true) };selected(original,first,true)
            onMain { original.setTerritory(b) };ready(original);assertTrue(original.state.value.rows.none { it.selected })
            onMain { original.setTerritory(null) }
            // A new ViewModel reads persisted settings, not the former UI state.
            val restarted=vm(DataStoreGpxVisibilityStore(dataStore));onMain { restarted.setTerritory(a) };ready(restarted)
            assertTrue(restarted.state.value.rows.first().selected);assertFalse(restarted.state.value.rows.last().selected)
            onMain { restarted.setTerritory(null) }
            scope.cancel(); scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            val diskScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            try {
                val reopened=DataStoreGpxVisibilityStore(PreferenceDataStoreFactory.create(scope=diskScope,produceFile={file}))
                assertEquals(setOf(first),reopened.load(a));assertEquals(emptySet<UUID>(),reopened.load(b))
            } finally { diskScope.cancel(); diskScope.coroutineContext[kotlinx.coroutines.Job]!!.join() }
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun catalogFailureIsNotEmptyAndCanRetry() = runBlocking {
        var fail=true;val vm=vm(Store(),catalog={if(fail)throw IOException("read") else emptyList()})
        onMain { vm.setTerritory(a) };assertNotNull(ready(vm).error)
        fail=false;onMain { vm.retry(null) };val state=ready(vm);assertNull(state.error);assertTrue(state.rows.isEmpty())
        onMain { vm.setTerritory(null) }
    }

    @Test fun duplicateAndMissingNamesHaveStableReadableDisambiguation() {
        val one=GpxReadyMetadata(UUID(0,1),"same.gpx",0);val two=GpxReadyMetadata(UUID(0,2),"same.gpx",0)
        val missing=GpxReadyMetadata(UUID(0,3)," ",1)
        val names=GpxDisplayViewModel.names(listOf(two,one,missing))
        assertEquals(3,names.map { it.second }.distinct().size)
        assertTrue(names.first().second.startsWith("GPX от "))
        assertTrue(names[1].second.endsWith(" · 1"));assertTrue(names[2].second.endsWith(" · 2"))
        assertEquals(names,GpxDisplayViewModel.names(listOf(missing,one,two)))
    }
}
