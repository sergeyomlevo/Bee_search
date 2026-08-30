package org.beesearch.app.domain.heading

import kotlin.math.roundToInt

fun normalizeHeadingDegrees(degrees: Double): Double {
    val normalized = degrees % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

fun trueHeadingDegrees(magneticHeadingDeg: Double, declinationDeg: Double): Double =
    normalizeHeadingDegrees(magneticHeadingDeg + declinationDeg)

fun roundedHeadingDegrees(degrees: Double): Int =
    normalizeHeadingDegrees(degrees).roundToInt() % 360
