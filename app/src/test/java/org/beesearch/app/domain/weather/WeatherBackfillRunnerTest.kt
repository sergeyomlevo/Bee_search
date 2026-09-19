package org.beesearch.app.domain.weather

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.PendingWeatherRequest
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.repository.WeatherBackfillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherBackfillRunnerTest {
    private val pointId = UUID.randomUUID()
    private val observationTime = Instant.parse("2026-09-10T11:37:00Z")
    private val request = PendingWeatherRequest(pointId, 56.123, 42.456, observationTime)

    @Test fun `retry uses persisted original coordinates and time then saves snapshot`() = runBlocking {
        val store = FakeStore(listOf(request))
        var received: Triple<Double, Double, Instant>? = null
        val provider = WeatherProvider { latitude, longitude, time ->
            received = Triple(latitude, longitude, time)
            WeatherSnapshot(18.4, 2.1, 247.0, Instant.parse("2026-09-10T12:00:00Z"),
                Instant.parse("2026-09-17T18:00:00Z"), "OPEN_METEO")
        }

        assertEquals(WeatherBackfillResult.COMPLETE, WeatherBackfillRunner(store, provider).run())
        assertEquals(Triple(56.123, 42.456, observationTime), received)
        assertEquals(WeatherStatus.LOADED, store.saved?.status)
        assertEquals(2.1, store.saved?.windSpeedMps)
        assertEquals(247.0, store.saved?.windDirectionDeg)
        assertEquals(Instant.parse("2026-09-10T12:00:00Z"), store.saved?.sampleAt)
    }

    @Test fun `temporary failure leaves point pending and requests retry`() = runBlocking {
        val store = FakeStore(listOf(request))
        val result = WeatherBackfillRunner(store, WeatherProvider { _, _, _ ->
            throw WeatherProviderException.Retryable("offline")
        }).run()
        assertEquals(WeatherBackfillResult.RETRY, result)
        assertNull(store.saved)
        assertEquals(emptyList<UUID>(), store.unavailable)
    }

    @Test fun `permanent malformed response marks unavailable without corrupting point`() = runBlocking {
        val store = FakeStore(listOf(request))
        val result = WeatherBackfillRunner(store, WeatherProvider { _, _, _ ->
            throw WeatherProviderException.Permanent("malformed")
        }).run()
        assertEquals(WeatherBackfillResult.COMPLETE, result)
        assertEquals(listOf(pointId), store.unavailable)
        assertNull(store.saved)
    }

    @Test fun `repeated run is idempotent after request is no longer pending`() = runBlocking {
        val store = FakeStore(listOf(request))
        var calls = 0
        val provider = WeatherProvider { _, _, _ ->
            calls++
            WeatherSnapshot(1.0, 2.0, 3.0, observationTime, observationTime, "FAKE")
        }
        val runner = WeatherBackfillRunner(store, provider)
        runner.run()
        store.requests = emptyList()
        runner.run()
        assertEquals(1, calls)
    }

    private class FakeStore(var requests: List<PendingWeatherRequest>) : WeatherBackfillStore {
        var saved: ObservationPointWeather? = null
        val unavailable = mutableListOf<UUID>()
        override suspend fun getPendingWeatherRequests() = requests
        override suspend fun storeLoadedWeather(pointId: UUID, weather: ObservationPointWeather): Boolean {
            saved = weather
            return true
        }
        override suspend fun markWeatherUnavailable(pointId: UUID): Boolean {
            unavailable += pointId
            return true
        }
    }
}
