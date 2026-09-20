package org.beesearch.app.ui.map

/**
 * Test fixture: the legacy `v1` coverage encoding.
 *
 * The app only ever *reads* `v1` now - a new Ареал is always written as `v2` - so the encoder lives
 * in the tests instead of in production code. Tests need it to seed a device or a fake store with the
 * value an older version of Bee Search would have left behind and then exercise migration.
 */
internal fun legacyCoverageValue(bounds: List<MapGeoBounds>): String = buildString {
    append(MapAreaCodec.LEGACY_VERSION)
    bounds.forEach { bound ->
        append('|').append(bound.north).append(',').append(bound.east)
            .append(',').append(bound.south).append(',').append(bound.west)
    }
}
