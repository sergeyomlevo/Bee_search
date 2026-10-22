package org.beesearch.app.ui.map

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class DeveloperBasemap {
    ONLINE,
    ACTIVE_VECTOR,
    SENTINEL,
    HYBRID,
}

/**
 * Single Activity-scoped source of the selected basemap.
 *
 * BeeMap is recreated whenever a card route replaces the map, so the choice must live above that
 * composition. Route changes keep this ViewModel; it is deliberately not a second copy of BeeMap's
 * former local remember state.
 */
internal class MapBasemapViewModel : ViewModel() {
    private val _selected = MutableStateFlow(DeveloperBasemap.ONLINE)
    val selected: StateFlow<DeveloperBasemap> = _selected.asStateFlow()

    fun select(value: DeveloperBasemap) {
        _selected.value = value
    }
}
