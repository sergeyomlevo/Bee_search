package org.beesearch.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObserverRequiredException
import org.beesearch.app.domain.model.TerritoryRequiredException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.repository.ObservationPointPreparationCreator
import org.beesearch.app.domain.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class CreateObservationPointTest {
    private val territoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()
    private val point = NewObservationPoint(territoryId, observerId, latitude = 56.1, longitude = 42.7)

    @Test fun `delegates selected ids without a code snapshot`() = runBlocking {
        val creator = FakeCreator()
        CreateObservationPoint(FakeSettings(AppSettings(territoryId, observerId)), creator).create(point)
        assertEquals(point, creator.created)
    }

    @Test fun `rejects stale current observer`() {
        assertThrows(ObserverRequiredException::class.java) {
            runBlocking { CreateObservationPoint(FakeSettings(AppSettings(territoryId, UUID.randomUUID())), FakeCreator()).create(point) }
        }
    }

    @Test fun `rejects stale current territory`() {
        assertThrows(TerritoryRequiredException::class.java) {
            runBlocking { CreateObservationPoint(FakeSettings(AppSettings(UUID.randomUUID(), observerId)), FakeCreator()).create(point) }
        }
    }

    @Test fun `first Bee creation uses the selected point`() = runBlocking {
        val creator = FakeCreator()
        CreateObservationPoint(FakeSettings(AppSettings(territoryId, observerId)), creator)
            .createWithFirstBee(point, "WHITE", MarkPosition.NONE)

        assertEquals(point, creator.firstBeePoint)
        assertEquals("WHITE", creator.firstBeeColor)
        assertEquals(MarkPosition.NONE, creator.firstBeePosition)
    }

    private class FakeSettings(private val value: AppSettings) : SettingsRepository {
        override val settings: Flow<AppSettings> = emptyFlow()
        override suspend fun getSettings() = value
        override suspend fun setCurrentTerritoryId(territoryId: UUID?) = Unit
        override suspend fun setCurrentObserverId(observerId: UUID?) = Unit
    }

    private class FakeCreator : ObservationPointPreparationCreator {
        var created: NewObservationPoint? = null
        var firstBeePoint: NewObservationPoint? = null
        var firstBeeColor: String? = null
        var firstBeePosition: MarkPosition? = null
        override suspend fun createObservationPoint(point: NewObservationPoint): ObservationPoint {
            created = point
            return point.toObservationPoint()
        }

        override suspend fun createObservationPointWithFirstBee(
            point: NewObservationPoint,
            markColor: String,
            markPosition: MarkPosition,
        ): ObservationPoint {
            firstBeePoint = point
            firstBeeColor = markColor
            firstBeePosition = markPosition
            return point.toObservationPoint()
        }

        override suspend fun createObservationPointWithNoBeesFound(
            point: NewObservationPoint,
        ): ObservationPoint = point.toObservationPoint()

        private fun NewObservationPoint.toObservationPoint(): ObservationPoint {
            return ObservationPoint(
                id = UUID.randomUUID(), territoryId = territoryId, observerId = observerId,
                observationYear = 2026, pointNumber = 1, beePresenceResult = null, code = null,
                latitude = latitude, longitude = longitude, gpsLatitude = null,
                gpsLongitude = null, gpsAccuracyM = null, createdAt = java.time.Instant.EPOCH, completedAt = null,
            )
        }
    }
}
