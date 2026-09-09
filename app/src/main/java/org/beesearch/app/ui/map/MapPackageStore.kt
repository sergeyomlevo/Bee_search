package org.beesearch.app.ui.map

import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Device-local D063 installation boundary. It never writes Room research data
 * and exposes a package only after D065 validation succeeds.
 */
internal interface MapPackageStore {
    suspend fun loadActive(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
    ): MapPackageAvailability

    suspend fun import(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
        manifestUri: Uri,
        pmtilesUri: Uri,
    ): MapPackageImportResult

    suspend fun clear(territoryId: UUID)
}

internal sealed interface MapPackageAvailability {
    data object Missing : MapPackageAvailability

    data class Ready(
        val activePackage: ActiveMapPackage,
    ) : MapPackageAvailability

    /** A retained package is not Ready for the current desired coverage or is no longer readable. */
    data class Unavailable(
        val message: String,
    ) : MapPackageAvailability
}

internal data class ActiveMapPackage(
    val manifest: MapPackageManifest,
    val pmtilesFile: File,
)

internal sealed interface MapPackageImportResult {
    data class Activated(val activePackage: ActiveMapPackage) : MapPackageImportResult
    data class Rejected(val message: String) : MapPackageImportResult
}
