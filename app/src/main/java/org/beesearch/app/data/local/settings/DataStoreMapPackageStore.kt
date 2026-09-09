package org.beesearch.app.data.local.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.beesearch.app.ui.map.ActiveMapPackage
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.beesearch.app.ui.map.MapPackageManifestParser
import org.beesearch.app.ui.map.MapPackageStore
import org.beesearch.app.ui.map.MapPackageValidationException
import org.beesearch.app.ui.map.MapPackageValidator

/**
 * Stores only a device-local pointer to a validated immutable package.
 * Imported files are never written into the active directory until validation
 * completes, so a rejected replacement cannot change the current pointer.
 */
internal class DataStoreMapPackageStore(
    private val contentResolver: ContentResolver,
    private val filesDir: File,
    private val dataStore: DataStore<Preferences>,
) : MapPackageStore {
    override suspend fun loadActive(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
    ): MapPackageAvailability = withContext(Dispatchers.IO) {
        val packageDirectory = activeDirectory(territoryId) ?: return@withContext MapPackageAvailability.Missing
        try {
            val manifestFile = File(packageDirectory, MANIFEST_FILE_NAME)
            val manifest = MapPackageManifestParser.parse(manifestFile.readText())
            val validated = MapPackageValidator.validate(
                manifest = manifest,
                pmtilesFile = File(packageDirectory, manifest.pmtilesFile),
                desiredCoverage = desiredCoverage,
            )
            MapPackageAvailability.Ready(
                ActiveMapPackage(manifest = validated.manifest, pmtilesFile = validated.pmtilesFile),
            )
        } catch (error: MapPackageValidationException) {
            MapPackageAvailability.Unavailable(error.message ?: "Офлайн-карта не готова")
        } catch (_: Exception) {
            MapPackageAvailability.Unavailable("Не удалось прочитать активную офлайн-карту")
        }
    }

    override suspend fun import(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
        manifestUri: Uri,
        pmtilesUri: Uri,
    ): MapPackageImportResult = withContext(Dispatchers.IO) {
        val stage = File(stagingRoot(), "stage-${UUID.randomUUID()}")
        try {
            if (!stage.mkdirs()) throw IOException("Не удалось подготовить место для импорта карты")
            val stagedManifestFile = File(stage, MANIFEST_FILE_NAME)
            copyUri(manifestUri, stagedManifestFile)
            val manifest = MapPackageManifestParser.parse(stagedManifestFile.readText())
            // DocumentsProvider names are presentation metadata, not package identity. The
            // imported bytes are staged under the manifest name and validated by length,
            // SHA-256 and PMTiles v3 header before they can become active.
            val stagedPmtilesFile = File(stage, manifest.pmtilesFile)
            copyUri(pmtilesUri, stagedPmtilesFile)
            val validated = MapPackageValidator.validate(manifest, stagedPmtilesFile, desiredCoverage)

            // Activity-result delivery may be retried after Android recreates a
            // picker-owning Activity. A byte-identical, already active package
            // is therefore an idempotent success, not a new immutable copy.
            // The candidate is fully staged and validated first, so a wrong or
            // damaged PMTiles selection can never pass through this shortcut.
            loadValidatedActive(territoryId, desiredCoverage)?.let { active ->
                if (active.manifest == validated.manifest) {
                    return@withContext MapPackageImportResult.Activated(active)
                }
            }

            val immutableDirectoryName = "package-${UUID.randomUUID()}"
            val immutableDirectory = File(packagesRoot(), immutableDirectoryName)
            if (!packagesRoot().mkdirs() && !packagesRoot().isDirectory) {
                throw IOException("Не удалось подготовить хранилище офлайн-карт")
            }
            if (!stage.renameTo(immutableDirectory)) {
                throw IOException("Не удалось активировать проверенную офлайн-карту")
            }
            try {
                dataStore.edit { preferences ->
                    preferences[activeKey(territoryId)] = immutableDirectoryName
                }
            } catch (error: Exception) {
                immutableDirectory.deleteRecursively()
                throw error
            }
            MapPackageImportResult.Activated(
                ActiveMapPackage(
                    manifest = validated.manifest,
                    pmtilesFile = File(immutableDirectory, validated.manifest.pmtilesFile),
                ),
            )
        } catch (error: MapPackageValidationException) {
            MapPackageImportResult.Rejected(error.message ?: "Карта не прошла проверку")
        } catch (_: IOException) {
            MapPackageImportResult.Rejected("Не удалось прочитать или сохранить выбранную карту")
        } catch (_: Exception) {
            MapPackageImportResult.Rejected("Не удалось импортировать офлайн-карту")
        } finally {
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    override suspend fun clear(territoryId: UUID) {
        dataStore.edit { preferences -> preferences.remove(activeKey(territoryId)) }
    }

    private suspend fun activeDirectory(territoryId: UUID): File? {
        val name = dataStore.firstOrNullValue(activeKey(territoryId)) ?: return null
        if (!PACKAGE_DIRECTORY_PATTERN.matches(name)) return null
        return File(packagesRoot(), name).takeIf(File::isDirectory)
    }

    private suspend fun loadValidatedActive(
        territoryId: UUID,
        desiredCoverage: List<MapCoverageFragment>,
    ): ActiveMapPackage? {
        val packageDirectory = activeDirectory(territoryId) ?: return null
        return try {
            val manifest = MapPackageManifestParser.parse(File(packageDirectory, MANIFEST_FILE_NAME).readText())
            val validated = MapPackageValidator.validate(
                manifest = manifest,
                pmtilesFile = File(packageDirectory, manifest.pmtilesFile),
                desiredCoverage = desiredCoverage,
            )
            ActiveMapPackage(validated.manifest, validated.pmtilesFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun copyUri(uri: Uri, destination: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Не удалось открыть выбранный файл")
    }

    private fun stagingRoot() = File(filesDir, "map-packages/staging")
    private fun packagesRoot() = File(filesDir, "map-packages/packages")
    private fun activeKey(territoryId: UUID) = stringPreferencesKey("map_package_active_$territoryId")

    private companion object {
        const val MANIFEST_FILE_NAME = "package.manifest.json"
        val PACKAGE_DIRECTORY_PATTERN = Regex("package-[0-9a-f-]{36}")
    }
}

private suspend fun DataStore<Preferences>.firstOrNullValue(key: Preferences.Key<String>): String? =
    data.first()[key]
