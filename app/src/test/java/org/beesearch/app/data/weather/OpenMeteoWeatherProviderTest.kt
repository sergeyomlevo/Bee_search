package org.beesearch.app.data.weather

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.beesearch.app.domain.weather.WeatherProviderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherProviderTest {
    private val now = Instant.parse("2026-09-17T12:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val body = """{"hourly":{"time":[${now.minusSeconds(3600).epochSecond},${now.epochSecond},${now.plusSeconds(3600).epochSecond}],"temperature_2m":[17.0,18.4,19.0],"wind_speed_10m":[1.0,2.1,3.0],"wind_direction_10m":[180,247,270]}}"""

    @Test fun `selects nearest hourly sample and original units`() = runTest {
        var requested = ""
        val provider = OpenMeteoWeatherProvider(WeatherHttpTransport { url -> requested = url; WeatherHttpResponse(200, body) }, clock)
        val result = provider.getWeatherSnapshot(55.75, 37.61, now.minusSeconds(600))
        assertEquals(18.4, result.temperatureC, 0.0)
        assertEquals(2.1, result.windSpeedMps, 0.0)
        assertEquals(247.0, result.windDirectionDeg, 0.0)
        assertTrue(requested.contains("hourly=temperature_2m,wind_speed_10m,wind_direction_10m"))
        assertTrue(requested.contains("wind_speed_unit=ms"))
        assertTrue(requested.contains("timeformat=unixtime"))
        assertTrue(requested.contains("2026-09-17"))
    }

    @Test fun `historical observation uses archive endpoint`() = runTest {
        var requested = ""
        val old = now.minusSeconds(93L * 86_400)
        val oldBody = """{"hourly":{"time":[${old.minusSeconds(3600).epochSecond},${old.epochSecond}],"temperature_2m":[17,18],"wind_speed_10m":[1,2],"wind_direction_10m":[180,247]}}"""
        OpenMeteoWeatherProvider(WeatherHttpTransport { url -> requested = url; WeatherHttpResponse(200, oldBody) }, clock)
            .getWeatherSnapshot(55.0, 37.0, old)
        assertTrue(requested.startsWith("https://archive-api.open-meteo.com/v1/archive"))
    }

    @Test fun `rejects response that does not cover original observation time`() = runTest {
        val old = now.minusSeconds(93L * 86_400)
        val error = try {
            OpenMeteoWeatherProvider(WeatherHttpTransport { WeatherHttpResponse(200, body) }, clock)
                .getWeatherSnapshot(55.0, 37.0, old)
            null
        } catch (e: Throwable) { e }
        assertTrue(error is WeatherProviderException.Permanent)
    }

    @Test fun `ties choose earlier sample`() = runTest {
        val target = now.minusSeconds(1800)
        val tieBody = """{"hourly":{"time":[${target.minusSeconds(1800).epochSecond},${target.plusSeconds(1800).epochSecond}],"temperature_2m":[1,2],"wind_speed_10m":[1,2],"wind_direction_10m":[10,20]}}"""
        val result = OpenMeteoWeatherProvider(WeatherHttpTransport { WeatherHttpResponse(200, tieBody) }, clock)
            .getWeatherSnapshot(1.0, 2.0, target)
        assertEquals(1.0, result.temperatureC, 0.0)
        assertEquals(target.minusSeconds(1800), result.sampleAt)
    }

    @Test fun `malformed and retryable failures are classified`() = runTest {
        assertThrows(WeatherProviderException.Permanent::class.java) { provider(WeatherHttpResponse(200, "{}")) }
        assertThrows(WeatherProviderException.Retryable::class.java) { provider(WeatherHttpResponse(503, "")) }
        assertThrows(WeatherProviderException.Permanent::class.java) { provider(WeatherHttpResponse(400, "")) }
    }

    private suspend fun provider(response: WeatherHttpResponse) =
        OpenMeteoWeatherProvider(WeatherHttpTransport { response }, clock)
            .getWeatherSnapshot(1.0, 2.0, now)

    private fun assertThrows(expected: Class<out Throwable>, block: suspend () -> Unit) {
        var thrown: Throwable? = null
        try { runTest { block() } } catch (error: Throwable) { thrown = error }
        assertTrue("Expected ${expected.simpleName}, got $thrown", thrown != null && expected.isInstance(thrown))
    }

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
