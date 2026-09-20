package org.beesearch.app.ui.map

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** D065 sidecar metadata. It deliberately contains no Territory identity or local path. */
internal data class MapPackageManifest(
    val schemaVersion: Int,
    val packageId: String,
    val datasetVersion: String,
    val profileId: String,
    val profileVersion: String,
    val styleVersion: String,
    val coverageFragments: List<MapCoverageFragment>,
    val minZoom: Int,
    val maxZoom: Int,
    val pmtilesFile: String,
    val pmtilesByteLength: Long,
    val pmtilesSha256: String,
)

internal data class PmtilesHeader(
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: MapGeoBounds,
)

internal data class ValidatedMapPackage(
    val manifest: MapPackageManifest,
    val pmtilesFile: File,
    val header: PmtilesHeader,
)

internal class MapPackageValidationException(message: String) : IllegalArgumentException(message)

/**
 * The D065 rejection shown when a package does not cover the current Ареал.
 *
 * Named so the Ареал screen can offer "choose another map" for exactly this outcome without copying
 * the wording, and without weakening the coverage rule itself.
 */
internal const val MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE =
    "Выбранные участки не полностью покрыты этой картой"

/** The field vector renderer supported by this first D063/D065 implementation. */
internal object BeeSearchMapPackageCompatibility {
    const val PROFILE_ID = "bee-search-field"
    const val PROFILE_VERSION = "v1"
    const val STYLE_VERSION = "vector-pmtiles-v1"

    fun validate(manifest: MapPackageManifest) {
        if (
            manifest.profileId != PROFILE_ID ||
            manifest.profileVersion != PROFILE_VERSION ||
            manifest.styleVersion != STYLE_VERSION
        ) {
            throw MapPackageValidationException("Эта карта использует неподдерживаемый профиль или стиль")
        }
    }
}

internal object MapPackageManifestParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(text: String): MapPackageManifest = try {
        val root = json.parseToJsonElement(text).jsonObject
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion != 1) {
            throw MapPackageValidationException("Версия описания карты не поддерживается")
        }
        val compatibility = root.requiredObject("territoryCompatibility")
        if (compatibility.requiredString("policy") != "coverage_fragments_only" || "territoryId" in compatibility) {
            throw MapPackageValidationException("Описание карты использует неподдерживаемую привязку к территории")
        }
        val fragments = root.requiredArray("coverageFragments").mapIndexed { index, element ->
            val fragment = element.jsonObject
            val west = fragment.requiredDouble("west")
            val south = fragment.requiredDouble("south")
            val east = fragment.requiredDouble("east")
            val north = fragment.requiredDouble("north")
            if (west > east) {
                throw MapPackageValidationException("Фрагмент покрытия ${index + 1} пересекает антимеридиан и не поддерживается")
            }
            MapCoverageFragment(MapGeoBounds(north = north, east = east, south = south, west = west))
        }
        if (fragments.isEmpty()) throw MapPackageValidationException("В описании карты нет участков покрытия")

        MapPackageManifest(
            schemaVersion = schemaVersion,
            packageId = root.requiredString("packageId").also { requireNotBlank(it, "packageId") },
            datasetVersion = root.requiredString("datasetVersion").also { requireNotBlank(it, "datasetVersion") },
            profileId = root.requiredString("profileId").also { requireNotBlank(it, "profileId") },
            profileVersion = root.requiredString("profileVersion").also { requireNotBlank(it, "profileVersion") },
            styleVersion = root.requiredString("styleVersion").also { requireNotBlank(it, "styleVersion") },
            coverageFragments = fragments,
            minZoom = root.requiredInt("minZoom").also { require(it in 0..30) },
            maxZoom = root.requiredInt("maxZoom").also { require(it in 0..30) },
            pmtilesFile = root.requiredString("pmtilesFile").also(::validatePmtilesBasename),
            pmtilesByteLength = root.requiredLong("pmtilesByteLength").also { require(it > 0) },
            pmtilesSha256 = root.requiredString("pmtilesSha256").also(::validateSha256),
        ).also { manifest ->
            if (manifest.minZoom > manifest.maxZoom) {
                throw MapPackageValidationException("Минимальный масштаб карты больше максимального")
            }
        }
    } catch (error: MapPackageValidationException) {
        throw error
    } catch (error: Exception) {
        throw MapPackageValidationException("Выбранный файл не является корректным manifest карты")
    }

    private fun JsonObject.requiredString(name: String): String =
        (this[name] as? JsonPrimitive)?.content
            ?: throw MapPackageValidationException("В описании карты нет поля $name")

    private fun JsonObject.requiredInt(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw MapPackageValidationException("Описание карты содержит неверное поле $name")

    private fun JsonObject.requiredLong(name: String): Long =
        (this[name] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: throw MapPackageValidationException("Описание карты содержит неверное поле $name")

    private fun JsonObject.requiredDouble(name: String): Double =
        (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw MapPackageValidationException("Описание карты содержит неверную координату $name")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.jsonObject ?: throw MapPackageValidationException("В описании карты нет поля $name")

    private fun JsonObject.requiredArray(name: String): JsonArray =
        this[name] as? JsonArray ?: throw MapPackageValidationException("В описании карты нет поля $name")

    private fun requireNotBlank(value: String, name: String) {
        if (value.isBlank()) throw MapPackageValidationException("В описании карты пустое поле $name")
    }

    private fun validatePmtilesBasename(value: String) {
        if (!value.endsWith(".pmtiles") || value.contains('/') || value.contains('\\') || value == ".pmtiles") {
            throw MapPackageValidationException("Описание карты содержит недопустимое имя PMTiles")
        }
    }

    private fun validateSha256(value: String) {
        if (!Regex("[0-9a-f]{64}").matches(value)) {
            throw MapPackageValidationException("Описание карты содержит неверную SHA-256 сумму")
        }
    }
}

internal object PmtilesHeaderReader {
    private const val HEADER_BYTES = 127

    fun read(file: File): PmtilesHeader {
        val header = ByteArray(HEADER_BYTES)
        FileInputStream(file).use { input ->
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count < 0) throw MapPackageValidationException("PMTiles файл слишком короткий")
                read += count
            }
        }
        if (header.copyOfRange(0, 7).decodeToString() != "PMTiles" || unsignedByte(header, 7) != 3) {
            throw MapPackageValidationException("Выбранный файл не является поддерживаемой PMTiles v3 картой")
        }
        return PmtilesHeader(
            minZoom = unsignedByte(header, 100),
            maxZoom = unsignedByte(header, 101),
            bounds = MapGeoBounds(
                north = signedIntLe(header, 114) / 10_000_000.0,
                east = signedIntLe(header, 110) / 10_000_000.0,
                south = signedIntLe(header, 106) / 10_000_000.0,
                west = signedIntLe(header, 102) / 10_000_000.0,
            ),
        )
    }

    private fun unsignedByte(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xff

    private fun signedIntLe(bytes: ByteArray, index: Int): Int =
        (bytes[index].toInt() and 0xff) or
            ((bytes[index + 1].toInt() and 0xff) shl 8) or
            ((bytes[index + 2].toInt() and 0xff) shl 16) or
            (bytes[index + 3].toInt() shl 24)
}

internal object MapPackageValidator {
    fun validate(
        manifest: MapPackageManifest,
        pmtilesFile: File,
        desiredCoverage: List<MapCoverageFragment>,
    ): ValidatedMapPackage {
        BeeSearchMapPackageCompatibility.validate(manifest)
        if (desiredCoverage.isEmpty()) {
            throw MapPackageValidationException("Сначала создайте ареал для офлайн-карты")
        }
        if (pmtilesFile.name != manifest.pmtilesFile || !pmtilesFile.isFile) {
            throw MapPackageValidationException("Выбранный PMTiles файл не соответствует описанию карты")
        }
        if (pmtilesFile.length() != manifest.pmtilesByteLength) {
            throw MapPackageValidationException("Размер PMTiles файла не совпадает с описанием карты")
        }
        if (sha256(pmtilesFile) != manifest.pmtilesSha256) {
            throw MapPackageValidationException("Контрольная сумма PMTiles файла не совпадает с описанием карты")
        }
        val header = PmtilesHeaderReader.read(pmtilesFile)
        if (header.minZoom != manifest.minZoom || header.maxZoom != manifest.maxZoom) {
            throw MapPackageValidationException("Диапазон масштабов PMTiles не совпадает с описанием карты")
        }
        if (manifest.coverageFragments.any { !header.bounds.contains(it.bounds) }) {
            throw MapPackageValidationException("Границы PMTiles не соответствуют заявленному покрытию")
        }
        if (desiredCoverage.any { desired -> manifest.coverageFragments.none { it.bounds.contains(desired.bounds) } }) {
            throw MapPackageValidationException(MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE)
        }
        return ValidatedMapPackage(manifest = manifest, pmtilesFile = pmtilesFile, header = header)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun MapGeoBounds.contains(other: MapGeoBounds): Boolean =
        other.west >= west && other.east <= east && other.south >= south && other.north <= north
}
