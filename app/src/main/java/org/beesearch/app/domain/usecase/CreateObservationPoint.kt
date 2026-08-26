package org.beesearch.app.domain.usecase

import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObserverCodeRequiredException
import org.beesearch.app.domain.repository.ObservationPointCreator
import org.beesearch.app.domain.repository.SettingsRepository

class CreateObservationPoint(
    private val settingsRepository: SettingsRepository,
    private val pointCreator: ObservationPointCreator,
) {
    suspend fun create(point: NewObservationPoint): ObservationPoint {
        val observerCode = settingsRepository.getSettings().observerCode
            ?: throw ObserverCodeRequiredException()
        return pointCreator.createObservationPoint(point, observerCode)
    }

    suspend fun saveObserverCodeAndCreate(
        observerCodeInput: String,
        point: NewObservationPoint,
    ): ObservationPoint {
        val savedObserverCode = settingsRepository.saveObserverCode(observerCodeInput)
        return pointCreator.createObservationPoint(point, savedObserverCode)
    }
}
