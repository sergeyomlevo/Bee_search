package org.beesearch.app

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val MEAN_EARTH_RADIUS_METERS = 6_371_008.8

internal fun geodesicDistanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    val fromLatitudeRad = Math.toRadians(fromLatitude)
    val toLatitudeRad = Math.toRadians(toLatitude)
    val latitudeDeltaRad = Math.toRadians(toLatitude - fromLatitude)
    val longitudeDeltaRad = Math.toRadians(toLongitude - fromLongitude)

    val haversine = sin(latitudeDeltaRad / 2).let { it * it } +
        cos(fromLatitudeRad) * cos(toLatitudeRad) *
        sin(longitudeDeltaRad / 2).let { it * it }

    return 2 * MEAN_EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

internal fun formatManualOffsetMeters(
    distanceMeters: Double,
    locale: Locale = Locale.getDefault(),
): String {
    require(distanceMeters.isFinite() && distanceMeters >= 0.0)

    val roundedTenths = (distanceMeters * 10).roundToInt() / 10.0
    return if (roundedTenths < 10.0) {
        String.format(locale, "%.1f м", roundedTenths)
    } else {
        "${distanceMeters.roundToInt()} м"
    }
}
