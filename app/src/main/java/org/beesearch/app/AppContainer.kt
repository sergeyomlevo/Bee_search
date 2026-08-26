package org.beesearch.app

import android.app.Application
import android.content.Context
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.local.settings.settingsDataStore
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.repository.TerritoryRepository
import org.beesearch.app.domain.usecase.CreateObservationPoint
import java.time.Clock

class BeeSearchApplication : Application() {
    internal val container: AppContainer by lazy { AppContainer(this) }
}

internal class AppContainer(context: Context) {
    private val clock = Clock.systemUTC()
    private val database = BeeSearchDatabase.create(context)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val territoryRepository: TerritoryRepository = RoomTerritoryRepository(
        territoryDao = database.territoryDao(),
        clock = clock,
    )
    val observationRepository: ObservationRepository = RoomObservationRepository(
        database = database,
        territoryDao = database.territoryDao(),
        pointDao = database.observationPointDao(),
        beeDao = database.beeDao(),
        cycleDao = database.flightCycleDao(),
        clock = clock,
    )
    val createObservationPoint = CreateObservationPoint(
        settingsRepository = settingsRepository,
        pointCreator = observationRepository,
    )
}
