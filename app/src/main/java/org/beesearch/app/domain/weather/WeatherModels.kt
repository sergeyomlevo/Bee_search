package org.beesearch.app.domain.weather

import java.time.Instant

/** Provider-neutral snapshot of weather conditions at an observation point. */
data class WeatherSnapshot(
    val temperatureC: Double,
    val windSpeedMps: Double,
    val windDirectionDeg: Double,
    val sampleAt: Instant,
    val fetchedAt: Instant,
    /** Provider identifier retained as data without coupling domain to an API SDK. */
    val source: String,
)

fun interface WeatherProvider {
    suspend fun getWeatherSnapshot(
        latitude: Double,
        longitude: Double,
        observationTime: Instant,
    ): WeatherSnapshot
}

sealed class WeatherProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Retryable(message: String, cause: Throwable? = null) : WeatherProviderException(message, cause)
    class Permanent(message: String, cause: Throwable? = null) : WeatherProviderException(message, cause)
}
