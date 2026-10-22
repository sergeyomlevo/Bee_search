package org.beesearch.app.ui.map

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MapBasemapViewModelTest {
    @Test
    fun selectedBasemapSurvivesTheSameActivityViewModelStore() {
        val store = ViewModelStore()
        val factory = ViewModelProvider.NewInstanceFactory()

        val first = ViewModelProvider(store, factory)[MapBasemapViewModel::class.java]
        assertEquals(DeveloperBasemap.ONLINE, first.selected.value)
        first.select(DeveloperBasemap.ACTIVE_VECTOR)

        // A card route removes BeeMap from composition, but the Activity store keeps this owner.
        val second = ViewModelProvider(store, factory)[MapBasemapViewModel::class.java]
        assertSame(first, second)
        assertEquals(DeveloperBasemap.ACTIVE_VECTOR, second.selected.value)
        store.clear()
    }
}
