package org.beesearch.app

import android.app.Application
import android.content.Context
import org.beesearch.app.data.backup.BackupDocumentExporter
import org.beesearch.app.data.backup.BackupService
import org.beesearch.app.data.backup.SafBackupDocumentExporter
import org.beesearch.app.data.heading.AndroidHeadingProvider
import org.beesearch.app.data.location.AndroidLocationProvider
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.FileAwareObservationDataMaintenance
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.local.settings.settingsDataStore
import org.beesearch.app.data.local.settings.DataStoreMapCoverageStore
import org.beesearch.app.data.local.settings.DataStoreMapPackageStore
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.data.weather.OpenMeteoWeatherProvider
import org.beesearch.app.data.weather.WorkManagerWeatherSyncScheduler
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.ObserverRepository
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.domain.usecase.CreateObservationPoint
import org.beesearch.app.domain.weather.WeatherBackfillRunner
import java.time.Clock

class BeeSearchApplication : Application() {
    internal val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.weatherSyncScheduler.enqueue()
    }
}

internal class AppContainer(context: Context) {
    private val clock = Clock.systemUTC()
    private val database = BeeSearchDatabase.create(context)
    val attachmentFileStore = ObservationAttachmentFileStore(context.filesDir, context.cacheDir)
    val weatherSyncScheduler = WorkManagerWeatherSyncScheduler(context)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val mapCoverageStore = DataStoreMapCoverageStore(context.settingsDataStore)
    val mapPackageStore = DataStoreMapPackageStore(
        contentResolver = context.contentResolver,
        filesDir = context.filesDir,
        dataStore = context.settingsDataStore,
    )
    val territoryRepository: TerritoryRepository = RoomTerritoryRepository(
        territoryDao = database.territoryDao(),
        clock = clock,
    )
    val observerRepository: ObserverRepository = RoomObserverRepository(
        observerDao = database.observerDao(),
        clock = clock,
    )
    val observationRepository: ObservationRepository = RoomObservationRepository(
        database = database,
        territoryDao = database.territoryDao(),
        pointDao = database.observationPointDao(),
        observerDao = database.observerDao(),
        beeDao = database.beeDao(),
        cycleDao = database.flightCycleDao(),
        attachmentDao = database.observationPointAttachmentDao(),
        weatherDao = database.observationPointWeatherDao(),
        clock = clock,
    )
    val observationDataMaintenance = FileAwareObservationDataMaintenance(
        repository = observationRepository,
        fileStore = attachmentFileStore,
    )
    val weatherBackfillRunner = WeatherBackfillRunner(
        repository = observationRepository,
        provider = OpenMeteoWeatherProvider(),
    )
    val backupDocumentExporter: BackupDocumentExporter = SafBackupDocumentExporter(
        backupService = BackupService(
            database = database,
            settings = context.settingsDataStore,
            attachmentStore = attachmentFileStore,
        ),
        contentResolver = context.contentResolver,
        cacheDirectory = context.cacheDir,
    )
    val createObservationPoint = CreateObservationPoint(
        settingsRepository = settingsRepository,
        pointCreator = observationRepository,
        weatherSyncScheduler = weatherSyncScheduler,
    )
    val locationProvider = AndroidLocationProvider(context)
    val headingProvider = AndroidHeadingProvider(context, clock)
}
