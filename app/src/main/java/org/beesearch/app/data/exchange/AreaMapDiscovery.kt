package org.beesearch.app.data.exchange

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.ui.map.MapPackageManifestParser

/**
 * A map package description is a JSON document; the documented delivery name is
 * `<pmtiles basename>.manifest.json`.
 */
private const val AREA_MAP_MANIFEST_EXTENSION = ".json"

/** One complete map package found for the current Ареал. */
internal data class AreaMapCandidate(
    val version: Int,
    val pmtilesFileName: String,
    val manifestFileName: String,
) {
    /**
     * What the user sees in the confirmation: the package basename without the file extension, for
     * example `Лух--7e82a310--map-v2`.
     */
    val displayName: String = pmtilesFileName.removeSuffix(MAP_PACKAGE_EXTENSION)
}

/**
 * What automatic discovery found.
 *
 * "Nothing found" and "the platform would not let us look" are deliberately different outcomes: the
 * first is an ordinary situation, the second is a normal consequence of how Android shares storage
 * with other applications. Both lead to the same standard file picker, and neither is an error.
 */
internal sealed interface AreaMapDiscoveryResult {
    /** No complete package of the current Ареал is available; use the file picker. */
    data object None : AreaMapDiscoveryResult

    /** Exactly one complete package: offer it. */
    data class One(val candidate: AreaMapCandidate) : AreaMapDiscoveryResult

    /** Several complete versions: offer the highest version and keep the rest available. */
    data class Several(
        val preferred: AreaMapCandidate,
        val alternatives: List<AreaMapCandidate>,
    ) : AreaMapDiscoveryResult

    /** The pairing is unclear (for example two manifests claiming one map); never choose silently. */
    data class Ambiguous(val reason: String) : AreaMapDiscoveryResult

    /** The platform refused to list the exchange folder; use the file picker. */
    data class Unavailable(val reason: String) : AreaMapDiscoveryResult
}

/** Looks for a map package of the current Ареал in the exchange folder. */
internal interface AreaMapDiscovery {
    suspend fun discover(expectedAreaStem: String): AreaMapDiscoveryResult
}

/**
 * Pairs the files of one exchange folder into complete map packages of the current Ареал.
 *
 * A package is complete only when a manifest and the PMTiles it names are both present: the pairing
 * is taken from the manifest's own `pmtilesFile` field, which is the existing D065 contract, so no
 * second naming rule is invented here. An orphan PMTiles, an orphan manifest, a manifest of another
 * version and a manifest of another Ареал are all simply not a complete package.
 *
 * The file name decides *only* whether a package belongs to the current Ареал and which version it
 * claims. It says nothing about coverage, integrity or compatibility: those are checked by the
 * existing importer before anything is activated.
 */
internal fun discoverAreaMapPackages(
    expectedAreaStem: String,
    fileNames: Collection<String>,
    readManifestText: (fileName: String) -> String?,
): AreaMapDiscoveryResult {
    val present = fileNames.toSet()
    val manifestsByPmtiles = linkedMapOf<String, MutableList<String>>()
    fileNames.forEach { fileName ->
        if (!fileName.endsWith(AREA_MAP_MANIFEST_EXTENSION)) return@forEach
        val manifest = readManifestText(fileName)
            ?.let { text -> runCatching { MapPackageManifestParser.parse(text) }.getOrNull() }
            ?: return@forEach
        val pmtiles = manifest.pmtilesFile
        // The declared artifact must actually be here, and it must be a map package of this Ареал.
        if (pmtiles !in present) return@forEach
        if (matchMapPackageFileName(expectedAreaStem, pmtiles) !is MapPackageFileNameMatch.CurrentArea) {
            return@forEach
        }
        manifestsByPmtiles.getOrPut(pmtiles) { mutableListOf() }.add(fileName)
    }

    val complete = mutableListOf<AreaMapCandidate>()
    val ambiguousVersions = mutableListOf<Int>()
    manifestsByPmtiles.forEach { (pmtiles, manifests) ->
        val version = (matchMapPackageFileName(expectedAreaStem, pmtiles) as
            MapPackageFileNameMatch.CurrentArea).version
        if (manifests.size == 1) {
            complete += AreaMapCandidate(
                version = version,
                pmtilesFileName = pmtiles,
                manifestFileName = manifests.single(),
            )
        } else {
            ambiguousVersions += version
        }
    }
    complete.sortByDescending(AreaMapCandidate::version)

    // An unclear pairing at the newest version must not be papered over by an older, clear one.
    val newestAmbiguous = ambiguousVersions.maxOrNull()
    val newestComplete = complete.firstOrNull()?.version
    if (newestAmbiguous != null && (newestComplete == null || newestAmbiguous >= newestComplete)) {
        return AreaMapDiscoveryResult.Ambiguous(
            "Для одной версии карты найдено несколько описаний, поэтому выбрать её автоматически нельзя",
        )
    }
    return when {
        complete.isEmpty() -> AreaMapDiscoveryResult.None
        complete.size == 1 -> AreaMapDiscoveryResult.One(complete.single())
        else -> AreaMapDiscoveryResult.Several(
            preferred = complete.first(),
            alternatives = complete.drop(1),
        )
    }
}

/**
 * Discovery over the real exchange folder.
 *
 * Only files this application owns can be seen here: on the target platform a file placed in the
 * shared `Download` tree by another application or by adb is invisible to directory listing and
 * unreadable even by its exact path (`EACCES`), and Bee Search deliberately asks for no permission
 * that would change that. Externally copied packages therefore end up in [AreaMapDiscoveryResult.None]
 * and the user picks them with the standard Android file picker, while packages Bee Search itself
 * stored - the whole point of the future server download - are found automatically.
 */
internal class AndroidAreaMapDiscovery(
    private val storage: BeeSearchExchangeStorage,
) : AreaMapDiscovery {
    override suspend fun discover(expectedAreaStem: String): AreaMapDiscoveryResult =
        withContext(Dispatchers.IO) {
            val folder = storage.directoryOf(ExchangeFolder.OFFLINE_MAPS).directory
            if (!folder.isDirectory) return@withContext AreaMapDiscoveryResult.None
            val fileNames = try {
                folder.list()
            } catch (error: Exception) {
                null
            } ?: return@withContext AreaMapDiscoveryResult.Unavailable(
                "Система не позволила просмотреть папку обмена",
            )
            discoverAreaMapPackages(
                expectedAreaStem = expectedAreaStem,
                fileNames = fileNames.toList(),
                readManifestText = { name -> runCatching { File(folder, name).readText() }.getOrNull() },
            )
        }
}
