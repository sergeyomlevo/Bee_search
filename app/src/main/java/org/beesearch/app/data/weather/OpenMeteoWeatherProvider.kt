package org.beesearch.app.data.weather

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.beesearch.app.domain.weather.WeatherProvider
import org.beesearch.app.domain.weather.WeatherProviderException
import org.beesearch.app.domain.weather.WeatherSnapshot

data class WeatherHttpResponse(val statusCode: Int, val body: String)

fun interface WeatherHttpTransport {
    @Throws(IOException::class)
    fun get(url: String): WeatherHttpResponse
}

private class HttpUrlConnectionTransport : WeatherHttpTransport {
    override fun get(url: String): WeatherHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            WeatherHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

/** Open-Meteo adapter. Endpoint and response details intentionally stay here. */
class OpenMeteoWeatherProvider(
    private val transport: WeatherHttpTransport = HttpUrlConnectionTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : WeatherProvider {
    override suspend fun getWeatherSnapshot(
        latitude: Double,
        longitude: Double,
        observationTime: Instant,
    ): WeatherSnapshot {
        validateCoordinates(latitude, longitude)
        val now = clock.instant()
        if (observationTime.isAfter(now.plus(1, ChronoUnit.HOURS))) {
            throw WeatherProviderException.Permanent("Observation time is in the future")
        }
        val useForecast = !observationTime.isBefore(now.minus(92, ChronoUnit.DAYS))
        val date = observationTime.atZone(ZoneOffset.UTC).toLocalDate()
        val endpoint = if (useForecast) FORECAST_ENDPOINT else ARCHIVE_ENDPOINT
        val url = buildUrl(endpoint, latitude, longitude, date.toString())
        val response = try {
            transport.get(url)
        } catch (error: IOException) {
            throw WeatherProviderException.Retryable("Weather request failed", error)
        } catch (error: RuntimeException) {
            throw WeatherProviderException.Retryable("Weather request failed", error)
        }
        if (response.statusCode == 408 || response.statusCode == 429 || response.statusCode >= 500) {
            throw WeatherProviderException.Retryable("Weather service temporarily unavailable (${response.statusCode})")
        }
        if (response.statusCode !in 200..299) {
            throw WeatherProviderException.Permanent("Weather service rejected request (${response.statusCode})")
        }
        return parseSnapshot(response.body, observationTime, now)
    }

    private fun parseSnapshot(body: String, observationTime: Instant, fetchedAt: Instant): WeatherSnapshot {
        val root = try { Json.parseToJsonElement(body).jsonObject } catch (error: Exception) {
            throw WeatherProviderException.Permanent("Malformed weather response", error)
        }
        val hourly = root["hourly"]?.jsonObject
            ?: throw WeatherProviderException.Permanent("Weather response has no hourly data")
        val times = numbers(hourly["time"]).map { it.toLong() }
        val temperatures = numbers(hourly["temperature_2m"])
        val speeds = numbers(hourly["wind_speed_10m"])
        val directions = numbers(hourly["wind_direction_10m"])
        if (times.isEmpty() || times.size != temperatures.size || times.size != speeds.size || times.size != directions.size) {
            throw WeatherProviderException.Permanent("Weather response arrays are inconsistent")
        }
        val target = observationTime.epochSecond
        val index = times.indices.minWithOrNull(compareBy<Int>({ kotlin.math.abs(times[it] - target) }, { times[it] }))!!
        if (kotlin.math.abs(times[index] - target) > MAX_SAMPLE_DISTANCE_SECONDS) {
            throw WeatherProviderException.Permanent("Weather response does not cover observation time")
        }
        val temperature = temperatures[index]
        val speed = speeds[index]
        val direction = directions[index]
        if (!temperature.isFinite() || !speed.isFinite() || !direction.isFinite() || speed < 0.0 || direction !in 0.0..359.999999) {
            throw WeatherProviderException.Permanent("Weather response contains invalid values")
        }
        return WeatherSnapshot(temperature, speed, direction, Instant.ofEpochSecond(times[index]), fetchedAt, SOURCE)
    }

    private fun numbers(element: JsonElement?): List<Double> = try {
        element?.jsonArray?.map { it.jsonPrimitive.content.toDouble().let { value ->
            if (!value.isFinite()) throw IllegalArgumentException()
            value
        } } ?: emptyList()
    } catch (error: Exception) {
        throw WeatherProviderException.Permanent("Malformed weather numeric array", error)
    }

    private fun buildUrl(endpoint: String, latitude: Double, longitude: Double, date: String) =
        "$endpoint?latitude=$latitude&longitude=$longitude&hourly=temperature_2m,wind_speed_10m,wind_direction_10m" +
            "&wind_speed_unit=ms&timezone=GMT&timeformat=unixtime&start_date=$date&end_date=$date"

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw WeatherProviderException.Permanent("Invalid coordinates")
        }
    }

    private companion object {
        const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"
        const val SOURCE = "OPEN_METEO"
        const val MAX_SAMPLE_DISTANCE_SECONDS = 3_600L
    }
}
