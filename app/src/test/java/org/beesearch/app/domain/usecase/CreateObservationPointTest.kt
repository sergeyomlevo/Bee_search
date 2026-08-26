package org.beesearch.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.repository.ObservationPointCreator
import org.beesearch.app.domain.repository.SettingsRepository
import org.beesearch.app.domain.validation.ObserverCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CreateObservationPointTest {
    @Test
    fun `DataStore failure prevents Room creation`() {
        val settings = FakeSettingsRepository().apply {
            saveFailure = IllegalStateException("DataStore failed")
        }
        val creator = FakePointCreator()
        val useCase = CreateObservationPoint(settings, creator)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase.saveObserverCodeAndCreate(" SV ", newPoint()) }
        }

        assertEquals(0, creator.callCount)
        assertNull(settings.current.observerCode)
    }

    @Test
    fun `Room failure keeps saved code and retry uses it`() {
        val settings = FakeSettingsRepository()
        val creator = FakePointCreator().apply {
            createFailure = IllegalStateException("Room failed")
        }
        val useCase = CreateObservationPoint(settings, creator)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase.saveObserverCodeAndCreate("  Сергей-01 ", newPoint()) }
        }
        assertEquals("Сергей-01", settings.current.observerCode)

        creator.createFailure = null
        runBlocking { useCase.create(newPoint()) }

        assertEquals(2, creator.callCount)
        assertEquals("Сергей-01", creator.lastObserverCode)
    }

    private fun newPoint() = NewObservationPoint(
        territoryId = UUID.randomUUID(),
        latitude = 56.1,
        longitude = 42.7,
    )

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings(null, null))
        var saveFailure: RuntimeException? = null
        val current: AppSettings get() = state.value

        override val settings: Flow<AppSettings> = state

        override suspend fun getSettings(): AppSettings = state.value

        override suspend fun saveObserverCode(value: String): String {
            saveFailure?.let { throw it }
            val normalized = ObserverCode.normalize(value)
            state.value = state.value.copy(observerCode = normalized)
            return normalized
        }

        override suspend fun setCurrentTerritoryId(territoryId: UUID?) {
            state.value = state.value.copy(currentTerritoryId = territoryId)
        }
    }

    private class FakePointCreator : ObservationPointCreator {
        var callCount = 0
        var lastObserverCode: String? = null
        var createFailure: RuntimeException? = null

        override suspend fun createObservationPoint(
            point: NewObservationPoint,
            observerCode: String,
        ): ObservationPoint {
            callCount += 1
            lastObserverCode = observerCode
            createFailure?.let { throw it }
            return ObservationPoint(
                id = UUID.randomUUID(),
                territoryId = point.territoryId,
                observerCode = observerCode,
                code = point.code,
                latitude = point.latitude,
                longitude = point.longitude,
                gpsLatitude = point.gpsLatitude,
                gpsLongitude = point.gpsLongitude,
                gpsAccuracyM = point.gpsAccuracyM,
                createdAt = Instant.EPOCH,
                completedAt = null,
            )
        }
    }
}
