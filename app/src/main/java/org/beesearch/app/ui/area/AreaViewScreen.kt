package org.beesearch.app.ui.area

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.BeeMap
import org.beesearch.app.ui.map.BeeMapMode
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapPackageStore

internal const val AREA_VIEW_TAG = "area-view-map"

/**
 * The Ареал on a clean map: every saved участок, nothing else.
 *
 * This is deliberately a separate screen from the участки editor. Looking at the Ареал is a normal
 * thing to do - before a field trip, in a conversation with the person preparing the offline map -
 * and it must not put the user in a state where an accidental tap changes the geometry. The editor is
 * one deliberate step away through «Изменить участки», and Back returns to the Ареал card.
 *
 * The map itself loads the saved Ареал, draws the участки unchanged (including any overlap) and
 * frames the whole area once on entry.
 */
@Composable
internal fun AreaViewRoute(
    territory: Territory?,
    mapAreaStore: MapAreaStore,
    mapPackageStore: MapPackageStore,
    locationState: LocationUiState,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onEditSections: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        BeeMap(
            territoryId = territory?.id,
            territoryName = territory?.name,
            areaStore = mapAreaStore,
            packageStore = mapPackageStore,
            locationState = locationState,
            locationPermissionGranted = locationPermissionGranted,
            onRequestLocationPermission = onRequestLocationPermission,
            // View mode creates no records: the field actions belong to the main map.
            onRequestCreateRecord = { _, _ -> },
            mode = BeeMapMode.AREA_VIEW,
            onEditAreaSections = onEditSections,
            onExitAreaView = onBack,
            modifier = Modifier.fillMaxSize().testTag(AREA_VIEW_TAG),
        )
    }
}
