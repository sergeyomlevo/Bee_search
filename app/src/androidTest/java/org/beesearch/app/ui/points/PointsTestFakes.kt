package org.beesearch.app.ui.points

import android.net.Uri
import java.util.UUID
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.beesearch.app.ui.map.MapPackageStore

/**
 * Device-local stores for browser tests that never compose the map.
 *
 * The Points screen only reaches them in Map mode, so these fakes keep Area and offline-map state out
 * of a test that verifies the header, the filters, the mass actions and the table.
 */
internal class UnusedMapAreaStore : MapAreaStore {
    override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
        MapAreaReadResult.Absent

    override suspend fun create(
        territoryId: UUID,
        name: String,
        bounds: List<MapGeoBounds>,
    ): MapAreaChangeResult = MapAreaChangeResult.Refused("unused in this test")

    override suspend fun updateBounds(territoryId: UUID, bounds: List<MapGeoBounds>): MapAreaChangeResult =
        MapAreaChangeResult.Refused("unused in this test")

    override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult =
        MapAreaChangeResult.Refused("unused in this test")

    override suspend fun delete(territoryId: UUID): MapAreaChangeResult =
        MapAreaChangeResult.Refused("unused in this test")

    override suspend fun clear(territoryId: UUID) = Unit

    override suspend fun snapshot(territoryId: UUID): String? = null

    override suspend fun restore(territoryId: UUID, value: String?) = Unit
}

internal class UnusedMapPackageStore : MapPackageStore {
    override suspend fun loadActive(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
    ): MapPackageAvailability = MapPackageAvailability.Missing

    override suspend fun import(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
        manifestUri: Uri,
        pmtilesUri: Uri,
    ): MapPackageImportResult = MapPackageImportResult.Rejected("unused in this test")

    override suspend fun clear(territoryId: UUID) = Unit
}
