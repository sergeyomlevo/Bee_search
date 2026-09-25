package org.beesearch.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.ObserverDao
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.DuplicateObserverCodeException
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.RequiredFieldException
import org.beesearch.app.domain.model.ObserverInUseException
import org.beesearch.app.domain.repository.ObserverRepository
import java.time.Clock
import java.util.UUID

internal class RoomObserverRepository(
    private val observerDao: ObserverDao,
    private val clock: Clock,
) : ObserverRepository {
    override fun observeObservers(): Flow<List<Observer>> = observerDao.observeAll()
        .map { observers -> observers.map(ObserverEntity::toDomain) }

    override suspend fun getObserver(id: UUID): Observer? = observerDao.getById(id)?.toDomain()

    override suspend fun createObserver(
        code: String,
        lastName: String,
        firstName: String,
        middleName: String?,
        contact: String?,
    ): Observer {
        val now = clock.instant()
        val observer = ObserverEntity(
            id = UUID.randomUUID(),
            code = requiredTrimmed(code, "Observer code"),
            lastName = requiredTrimmed(lastName, "Observer lastName"),
            firstName = requiredTrimmed(firstName, "Observer firstName"),
            middleName = optionalTrimmed(middleName),
            contact = optionalTrimmed(contact),
            createdAt = now,
            updatedAt = now,
        )
        try {
            observerDao.insert(observer)
        } catch (_: SQLiteConstraintException) {
            throw DuplicateObserverCodeException()
        }
        return observer.toDomain()
    }

    override suspend fun updateObserver(observer: Observer): Observer {
        val updated = observer.copy(
            code = requiredTrimmed(observer.code, "Observer code"),
            lastName = requiredTrimmed(observer.lastName, "Observer lastName"),
            firstName = requiredTrimmed(observer.firstName, "Observer firstName"),
            middleName = optionalTrimmed(observer.middleName),
            contact = optionalTrimmed(observer.contact),
            updatedAt = clock.instant(),
        )
        try {
            if (observerDao.update(updated.id, updated.code, updated.lastName, updated.firstName, updated.middleName, updated.contact, updated.updatedAt) != 1) {
                throw org.beesearch.app.domain.model.EntityNotFoundException("Observer")
            }
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            throw DuplicateObserverCodeException()
        }
        return updated
    }

    override suspend fun deleteObserver(id: UUID) {
        if (observerDao.countObservationPoints(id) != 0 || observerDao.countCreatedPhysicalObjects(id) != 0) {
            throw ObserverInUseException()
        }
        if (observerDao.deleteById(id) != 1) throw org.beesearch.app.domain.model.EntityNotFoundException("Observer")
    }
}

internal fun requiredTrimmed(value: String, field: String): String = value.trim().ifEmpty {
    throw RequiredFieldException(field)
}

internal fun optionalTrimmed(value: String?): String? = value?.trim()?.ifEmpty { null }
