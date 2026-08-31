package org.beesearch.app

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val MEAN_EARTH_RADIUS_METERS = 6_371_008.8
internal const val MIN_VISIBLE_MAP_MEASUREMENT_METERS = 0.5

internal data class MapTarget(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        requireValidCoordinate(latitude, longitude)
    }
}

internal data class MapMeasurement(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val directionAbbreviation: String,
)

internal fun geodesicDistanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    requireValidCoordinate(fromLatitude, fromLongitude)
    requireValidCoordinate(toLatitude, toLongitude)
    val fromLatitudeRad = Math.toRadians(fromLatitude)
    val toLatitudeRad = Math.toRadians(toLatitude)
    val latitudeDeltaRad = Math.toRadians(toLatitude - fromLatitude)
    val longitudeDeltaRad = Math.toRadians(toLongitude - fromLongitude)

    val haversine = sin(latitudeDeltaRad / 2).let { it * it } +
        cos(fromLatitudeRad) * cos(toLatitudeRad) *
        sin(longitudeDeltaRad / 2).let { it * it }

    return 2 * MEAN_EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

internal fun initialGeographicBearingDegrees(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    requireValidCoordinate(fromLatitude, fromLongitude)
    requireValidCoordinate(toLatitude, toLongitude)
    val fromLatitudeRad = Math.toRadians(fromLatitude)
    val toLatitudeRad = Math.toRadians(toLatitude)
    val longitudeDeltaRad = Math.toRadians(toLongitude - fromLongitude)
    val y = sin(longitudeDeltaRad) * cos(toLatitudeRad)
    val x = cos(fromLatitudeRad) * sin(toLatitudeRad) -
        sin(fromLatitudeRad) * cos(toLatitudeRad) * cos(longitudeDeltaRad)
    return normalizeDegrees(Math.toDegrees(atan2(y, x)))
}

internal fun normalizeDegrees(degrees: Double): Double {
    require(degrees.isFinite())
    return ((degrees % 360.0) + 360.0) % 360.0
}

internal fun mapDirectionAbbreviation(bearingDegrees: Double): String {
    val directions = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
    val sector = floor((normalizeDegrees(bearingDegrees) + 22.5) / 45.0).toInt() % directions.size
    return directions[sector]
}

internal fun mapMeasurement(
    gpsPosition: MapTarget,
    mapCenter: MapTarget,
): MapMeasurement {
    val distance = geodesicDistanceMeters(
        fromLatitude = gpsPosition.latitude,
        fromLongitude = gpsPosition.longitude,
        toLatitude = mapCenter.latitude,
        toLongitude = mapCenter.longitude,
    )
    val bearing = initialGeographicBearingDegrees(
        fromLatitude = gpsPosition.latitude,
        fromLongitude = gpsPosition.longitude,
        toLatitude = mapCenter.latitude,
        toLongitude = mapCenter.longitude,
    )
    return MapMeasurement(
        distanceMeters = distance,
        bearingDegrees = bearing,
        directionAbbreviation = mapDirectionAbbreviation(bearing),
    )
}

internal fun visibleMapMeasurement(
    gpsPosition: MapTarget?,
    mapCenter: MapTarget?,
): MapMeasurement? {
    if (gpsPosition == null || mapCenter == null) return null
    return mapMeasurement(gpsPosition, mapCenter).takeIf {
        it.distanceMeters >= MIN_VISIBLE_MAP_MEASUREMENT_METERS
    }
}

internal fun formatMapMeasurement(
    measurement: MapMeasurement,
    locale: Locale = Locale.getDefault(),
): String {
    val bearing = measurement.bearingDegrees.roundToInt().mod(360)
    return "${formatManualOffsetMeters(measurement.distanceMeters, locale)} · " +
        "$bearing° ${measurement.directionAbbreviation}"
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

private fun requireValidCoordinate(latitude: Double, longitude: Double) {
    require(latitude.isFinite() && latitude in -90.0..90.0)
    require(longitude.isFinite() && longitude in -180.0..180.0)
}
