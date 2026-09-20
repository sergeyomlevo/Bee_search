package org.beesearch.app.data.exchange

/**
 * The external file-name contract between an exported Ареал and the offline map built for it.
 *
 * ```text
 * Лух--7e82a310.json                  the Ареал itself
 * Лух--7e82a310--map-v1.pmtiles       the first map package generated for that Ареал
 * Лух--7e82a310--map-v2.pmtiles       the next one
 * ```
 *
 * The name exists so a human recognises the area, and so Bee Search can offer the right package
 * without the user hunting through files. It is **not** an identity and never a proof of anything:
 * a perfectly named package can still be the wrong map or a damaged file, so every candidate goes
 * through the existing manifest, integrity and D065 coverage validation before it can be activated.
 *
 * The name itself is derived from the same stem as the Area file (D083), so the two contracts cannot
 * drift apart.
 */
internal const val MAP_PACKAGE_VERSION_INFIX = "--map-v"
internal const val MAP_PACKAGE_EXTENSION = ".pmtiles"

/** Map package versions start at 1; there is no version 0 and no unversioned package. */
internal const val MIN_MAP_PACKAGE_VERSION = 1

/** `Ареал JSON + version` → `<areaStem>--map-v<N>.pmtiles`. */
internal fun canonicalMapPackageFileName(areaStem: String, version: Int): String {
    require(areaStem.isNotBlank()) { "У ареала должно быть имя файла" }
    require(version >= MIN_MAP_PACKAGE_VERSION) { "Версия карты начинается с $MIN_MAP_PACKAGE_VERSION" }
    return "$areaStem$MAP_PACKAGE_VERSION_INFIX$version$MAP_PACKAGE_EXTENSION"
}

/** How one file name relates to the Ареал whose package is being looked for. */
internal sealed interface MapPackageFileNameMatch {
    /** A map package of the current Ареал; [version] is its numeric version. */
    data class CurrentArea(val version: Int) : MapPackageFileNameMatch

    /** A map package that follows the contract but belongs to a different Ареал. */
    data object OtherArea : MapPackageFileNameMatch

    /** A `.pmtiles` file whose name does not follow the version contract. */
    data object Malformed : MapPackageFileNameMatch

    /** A name that looks like a map package but is not a `.pmtiles` file. */
    data object WrongExtension : MapPackageFileNameMatch
}

/**
 * Matches one file name against the exact stem of the current Ареал.
 *
 * Matching is exact on purpose: the whole expected stem must be the prefix, so a similar name, a
 * shared short id with another name, or a longer neighbouring name never becomes an automatic
 * candidate. The version grammar is strict - `[1-9][0-9]*` - which is why `v0`, `v01`, `v-1` and
 * `v1.0` are malformed rather than "some version". Any positive integer is a valid version, including
 * a large number such as a year; versions are compared as numbers, never as text and never by the
 * file's timestamp (`v12` is newer than `v2`, and `v2026` is newer than both).
 */
internal fun matchMapPackageFileName(
    expectedAreaStem: String,
    fileName: String,
): MapPackageFileNameMatch {
    val versionPrefix = expectedAreaStem + MAP_PACKAGE_VERSION_INFIX
    if (!fileName.endsWith(MAP_PACKAGE_EXTENSION)) {
        // Only a name shaped like a map package is worth reporting as a wrong extension.
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        return if (
            withoutExtension.startsWith(versionPrefix) || OTHER_AREA_MAP_BASE.matches(withoutExtension)
        ) {
            MapPackageFileNameMatch.WrongExtension
        } else {
            MapPackageFileNameMatch.Malformed
        }
    }
    val base = fileName.removeSuffix(MAP_PACKAGE_EXTENSION)
    if (base.startsWith(versionPrefix)) {
        val version = base.removePrefix(versionPrefix)
        val parsed = version.toIntOrNull()
        return if (parsed != null && VERSION_GRAMMAR.matches(version)) {
            MapPackageFileNameMatch.CurrentArea(parsed)
        } else {
            MapPackageFileNameMatch.Malformed
        }
    }
    return if (OTHER_AREA_MAP_BASE.matches(base)) {
        MapPackageFileNameMatch.OtherArea
    } else {
        MapPackageFileNameMatch.Malformed
    }
}

/** `[1-9][0-9]*`: a positive version without leading zeros. */
private val VERSION_GRAMMAR = Regex("[1-9][0-9]*")

/**
 * A map package name of *some* Ареал: `<any name>--<8 hex>--map-v<N>`.
 *
 * Used only to tell "this belongs to another area" apart from "this is not a map package name at
 * all"; it never makes a file a candidate for the current Ареал.
 */
private val OTHER_AREA_MAP_BASE = Regex(".+--[0-9a-f]{8}${Regex.escape(MAP_PACKAGE_VERSION_INFIX)}[1-9][0-9]*")
