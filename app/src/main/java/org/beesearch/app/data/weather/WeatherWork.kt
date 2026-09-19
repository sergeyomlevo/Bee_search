package org.beesearch.app.data.weather

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.beesearch.app.BeeSearchApplication
import org.beesearch.app.domain.weather.WeatherBackfillResult
import org.beesearch.app.domain.weather.WeatherSyncScheduler

internal class WorkManagerWeatherSyncScheduler(context: Context) : WeatherSyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<WeatherBackfillWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "observation-point-weather-backfill"
    }
}

internal class WeatherBackfillWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as BeeSearchApplication
        return when (application.container.weatherBackfillRunner.run()) {
            WeatherBackfillResult.COMPLETE -> Result.success()
            WeatherBackfillResult.RETRY -> Result.retry()
        }
    }
}
