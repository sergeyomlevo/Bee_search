package org.beesearch.app.domain.weather

import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.repository.WeatherBackfillStore

fun interface WeatherSyncScheduler {
    fun enqueue()
}

enum class WeatherBackfillResult { COMPLETE, RETRY }

/** Loads only persisted pending requests, so retries retain the point's original place and time. */
class WeatherBackfillRunner(
    private val repository: WeatherBackfillStore,
    private val provider: WeatherProvider,
) {
    suspend fun run(): WeatherBackfillResult {
        var shouldRetry = false
        repository.getPendingWeatherRequests().forEach { request ->
            try {
                val snapshot = provider.getWeatherSnapshot(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    observationTime = request.observationTime,
                )
                repository.storeLoadedWeather(
                    request.observationPointId,
                    ObservationPointWeather(
                        observationPointId = request.observationPointId,
                        status = WeatherStatus.LOADED,
                        temperatureC = snapshot.temperatureC,
                        windSpeedMps = snapshot.windSpeedMps,
                        windDirectionDeg = snapshot.windDirectionDeg,
                        sampleAt = snapshot.sampleAt,
                        fetchedAt = snapshot.fetchedAt,
                        source = snapshot.source,
                    ),
                )
            } catch (_: WeatherProviderException.Retryable) {
                shouldRetry = true
            } catch (_: WeatherProviderException.Permanent) {
                repository.markWeatherUnavailable(request.observationPointId)
            }
        }
        return if (shouldRetry) WeatherBackfillResult.RETRY else WeatherBackfillResult.COMPLETE
    }
}
