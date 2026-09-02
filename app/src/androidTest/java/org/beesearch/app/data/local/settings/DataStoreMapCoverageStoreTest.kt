package org.beesearch.app.data.local.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataStoreMapCoverageStoreTest {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File.createTempFile("coverage-", ".preferences_pb")

    @After fun tearDown() { scope.cancel(); file.delete() }

    @Test fun territoryCoverageIsIndependentAndSurvivesStoreRecreation() = runBlocking {
        val store = DataStoreMapCoverageStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        val fragmentsA = listOf(MapCoverageFragment(MapGeoBounds(10.0, 20.0, 0.0, 0.0)))
        val fragmentsB = listOf(MapCoverageFragment(MapGeoBounds(30.0, 40.0, 20.0, 20.0)))
        store.replace(a, fragmentsA)
        store.replace(b, fragmentsB)
        assertEquals(fragmentsA, store.load(a))
        assertEquals(fragmentsB, store.load(b))
        store.clear(a)
        assertEquals(emptyList<MapCoverageFragment>(), store.load(a))
        assertEquals(fragmentsB, store.load(b))
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val recreated = DataStoreMapCoverageStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        assertEquals(emptyList<MapCoverageFragment>(), recreated.load(a))
        assertEquals(fragmentsB, recreated.load(b))
    }
}
