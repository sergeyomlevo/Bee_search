package org.beesearch.app.data.local.room

import org.beesearch.app.addBee
import org.beesearch.app.createObservationPointWithFirstBee
import org.beesearch.app.removePreparedBee
import org.beesearch.app.startInitialGroupRelease

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.AzimuthCaptureAlreadyConsumedException
import org.beesearch.app.domain.model.AzimuthCaptureRequiresOpenFlightCycleException
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeUndoAction
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.DuplicateBeeMarkException
import org.beesearch.app.domain.model.DuplicateTerritoryCodeException
import org.beesearch.app.domain.model.DuplicateObserverCodeException
import org.beesearch.app.domain.model.RequiredFieldException
import org.beesearch.app.domain.model.InitialReleaseAlreadyStartedException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.NoBeesFoundAlreadyRecordedException
import org.beesearch.app.domain.model.NoReversibleBeeActionException
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.beesearch.app.domain.model.OpenFlightCycleNotFoundException
import org.beesearch.app.domain.model.isExcludedFromFlightDurationAnalysis
import org.beesearch.app.domain.usecase.StartupDestination
import org.beesearch.app.domain.usecase.StartupRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class RoomPersistenceTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var clock: MutableClock
    private lateinit var territoryRepository: RoomTerritoryRepository
    private lateinit var observationRepository: RoomObservationRepository
    private lateinit var territoryId: UUID
    private lateinit var observerId: UUID

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(Instant.parse("2026-08-26T06:34:12Z"))
        territoryRepository = RoomTerritoryRepository(database.territoryDao(), clock)
        val observerRepository = RoomObserverRepository(database.observerDao(), clock)
        observationRepository = RoomObservationRepository(
            database = database,
            territoryDao = database.territoryDao(),
            pointDao = database.observationPointDao(),
            observerDao = database.observerDao(),
            beeDao = database.beeDao(),
            cycleDao = database.flightCycleDao(),
            clock = clock,
            observationZoneIdProvider = clock::getZone,
        )
        territoryId = territoryRepository.createTerritory("KLYAZMA-01", "Клязьма", "Область", "Район").id
        observerId = observerRepository.createObserver("SV", "Сидоров", "Сергей", null, null).id
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyOneObservationPointCanBeActive() = runBlocking {
        val first = createPoint()

        assertThrows(ObservationPointAlreadyActiveException::class.java) {
            runBlocking { createPoint() }
        }

        clock.advanceSeconds(10)
        observationRepository.recordNoBeesFound(first.id)
        val second = createPoint()
        assertNull(second.completedAt)
    }

    @Test
    fun pointNumberStartsAtOneAndIncrementsWithinTerritoryYearObserver() = runBlocking {
        val first = createPoint()
        assertEquals(2026, first.observationYear)
        assertEquals(1, first.pointNumber)

        observationRepository.recordNoBeesFound(first.id)
        val second = createPoint()

        assertEquals(2026, second.observationYear)
        assertEquals(2, second.pointNumber)
    }

    @Test
    fun observationYearUsesLocalCalendarWithoutChangingCreatedAtInstant() = runBlocking {
        val creationInstant = Instant.parse("2026-12-31T22:30:00Z")
        clock.set(creationInstant)
        val localYearRepository = RoomObservationRepository(
            database = database,
            territoryDao = database.territoryDao(),
            pointDao = database.observationPointDao(),
            observerDao = database.observerDao(),
            beeDao = database.beeDao(),
            cycleDao = database.flightCycleDao(),
            clock = clock,
            observationZoneIdProvider = { ZoneOffset.ofHours(3) },
        )

        val point = localYearRepository.createObservationPoint(
            point = NewObservationPoint(
                territoryId = territoryId,
                observerId = observerId,
                latitude = 56.1,
                longitude = 42.7,
            ),
        )

        assertEquals(2027, point.observationYear)
        assertEquals(creationInstant, point.createdAt)
    }

    @Test
    fun pointNumberRestartsForDifferentObserverYearAndTerritory() = runBlocking {
        val first = createPoint()
        observationRepository.recordNoBeesFound(first.id)

        val otherObserverId = RoomObserverRepository(database.observerDao(), clock)
            .createObserver("IVN", "Иванов", "Иван", null, null).id
        val otherObserver = createPoint(observerId = otherObserverId)
        assertEquals(1, otherObserver.pointNumber)
        observationRepository.recordNoBeesFound(otherObserver.id)

        clock.set(Instant.parse("2027-01-02T06:34:12Z"))
        val otherYear = createPoint()
        assertEquals(2027, otherYear.observationYear)
        assertEquals(1, otherYear.pointNumber)
        observationRepository.recordNoBeesFound(otherYear.id)

        val otherTerritory = territoryRepository.createTerritory("OTHER", "Другая", "Область", "Район").id
        val pointInOtherTerritory = createPoint(
            pointTerritoryId = otherTerritory,
        )
        assertEquals(1, pointInOtherTerritory.pointNumber)
    }

    @Test
    fun pointNumberUniqueConstraintIsAuthoritative() = runBlocking {
        val point = createPoint()
        val duplicate = ObservationPointEntity(
            id = UUID.randomUUID(),
            territoryId = point.territoryId,
            observerId = point.observerId,
            observationYear = point.observationYear,
            pointNumber = point.pointNumber,
            beePresenceResult = null,
            code = null,
            latitude = 56.2,
            longitude = 42.8,
            gpsLatitude = null,
            gpsLongitude = null,
            gpsAccuracyM = null,
            createdAt = clock.instant(),
            initialGroupReleaseAt = null,
            completedAt = clock.instant(),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.observationPointDao().insert(duplicate) }
        }
        Unit
    }

    @Test
    fun failedCreateDoesNotConsumePointNumberOutsideTransaction() = runBlocking {
        val first = createPoint()
        assertThrows(ObservationPointAlreadyActiveException::class.java) {
            runBlocking { createPoint() }
        }

        observationRepository.recordNoBeesFound(first.id)
        assertEquals(2, createPoint().pointNumber)
    }

    @Test
    fun correctedCoordinatesAndOriginalGpsMeasurementAreStoredSeparately() = runBlocking {
        val point = createPoint()

        assertEquals(56.1959786, point.latitude, 0.0)
        assertEquals(42.7477116, point.longitude, 0.0)
        assertEquals(56.1959000, point.gpsLatitude!!, 0.0)
        assertEquals(42.7477000, point.gpsLongitude!!, 0.0)
        assertEquals(4.5, point.gpsAccuracyM!!, 0.0)

        val restored = observationRepository.observeActivePoint().first()
        assertEquals(point, restored)
    }

    @Test
    fun newPointHasNoBeePresenceResultUntilFirstBee() = runBlocking {
        val point = createPoint()

        assertNull(point.beePresenceResult)
        assertNull(observationRepository.observeActivePoint().first()?.beePresenceResult)
    }

    @Test
    fun firstDepartureCreatesBeeCycleAndPresenceResultInOneTransaction() = runBlocking {
        val point = createPoint()
        val departureTime = clock.instant()
        val started = observationRepository.startFirstFlight(
            pointId = point.id,
            markColor = "WHITE",
            markPosition = MarkPosition.NONE,
        )

        assertEquals(BeePresenceResult.BEES_FOUND, observationRepository.observeActivePoint().first()?.beePresenceResult)
        val bees = observationRepository.observeBees(point.id).first()
        assertEquals(1, bees.size)
        assertEquals(started.bee, bees.single())
        assertEquals(1, started.flightCycle.sequenceNumber)
        assertEquals(departureTime, started.flightCycle.departureTime)
        assertEquals(listOf(started.flightCycle), observationRepository.observeFlightCyclesForPoint(point.id).first())
    }

    @Test
    fun failedFirstDepartureTransactionKeepsEmptyObservationPointWithoutOrphans() = runBlocking {
        val point = createPoint()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_first_bee_insert
            BEFORE INSERT ON bees
            BEGIN
                SELECT RAISE(ABORT, 'forced first Bee failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            runBlocking {
                observationRepository.startFirstFlight(
                    pointId = point.id,
                    markColor = "WHITE",
                    markPosition = MarkPosition.NONE,
                )
            }
        }

        assertEquals(point, observationRepository.observeActivePoint().first())
        assertTrue(observationRepository.observeBees(point.id).first().isEmpty())
        assertTrue(observationRepository.observeFlightCyclesForPoint(point.id).first().isEmpty())
    }

    @Test
    fun noBeesFoundCreatesAndCompletesPointInOneTransaction() = runBlocking {
        val point = observationRepository.createObservationPointWithNoBeesFound(newPoint())

        assertEquals(BeePresenceResult.NO_BEES_FOUND, point.beePresenceResult)
        assertTrue(point.completedAt != null)
        assertNull(observationRepository.observeActivePoint().first())
        assertTrue(observationRepository.observeBees(point.id).first().isEmpty())
    }

    @Test
    fun firstAndAdditionalBeeKeepBeesFoundResult() = runBlocking {
        val point = createPoint()

        observationRepository.startFirstFlight(point.id, "WHITE", MarkPosition.NONE)
        assertEquals(
            BeePresenceResult.BEES_FOUND,
            observationRepository.observeActivePoint().first()?.beePresenceResult,
        )

        clock.advanceSeconds(1)
        observationRepository.startFirstFlight(point.id, "BLUE", MarkPosition.LEFT_WING)
        assertEquals(
            BeePresenceResult.BEES_FOUND,
            observationRepository.observeActivePoint().first()?.beePresenceResult,
        )
    }

    @Test
    fun cancellingOnlyFirstDepartureReturnsPresenceResultToNull() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.startFirstFlight(
            point.id,
            "GREEN",
            MarkPosition.RIGHT_WING,
        ).bee

        observationRepository.undoLastBeeAction(bee.id)

        assertNull(observationRepository.observeActivePoint().first()?.beePresenceResult)
    }

    @Test
    fun noBeesFoundResultBlocksBeeCreation() = runBlocking {
        val point = createPoint()
        database.observationPointDao().setBeePresenceResult(
            point.id,
            BeePresenceResult.NO_BEES_FOUND,
        )

        assertThrows(NoBeesFoundAlreadyRecordedException::class.java) {
            runBlocking {
                observationRepository.startFirstFlight(point.id, "WHITE", MarkPosition.NONE)
            }
        }
        Unit
    }

    @Test
    fun explicitNoBeesResultCompletesPointAtomically() = runBlocking {
        val point = createPoint()

        val completed = observationRepository.recordNoBeesFound(point.id)

        assertEquals(BeePresenceResult.NO_BEES_FOUND, completed.beePresenceResult)
        assertEquals(clock.instant(), completed.completedAt)
        assertTrue(observationRepository.observeBees(point.id).first().isEmpty())
        assertNull(observationRepository.observeActivePoint().first())

        val territory = requireNotNull(territoryRepository.getTerritory(territoryId))
        assertEquals(
            StartupDestination.ReadyForMap,
            StartupRouter.decide(
                activePoint = observationRepository.observeActivePoint().first(),
                currentTerritoryId = territoryId,
                territories = listOf(territory),
                currentObserverId = observerId,
                observers = listOf(requireNotNull(RoomObserverRepository(database.observerDao(), clock).getObserver(observerId))),
            ),
        )

        clock.advanceSeconds(1)
        val nextPoint = createPoint()
        assertNull(nextPoint.completedAt)
        assertEquals(nextPoint, observationRepository.observeActivePoint().first())
    }

    @Test
    fun territoryAndObserverCreationTrimFieldsAndRejectDuplicateCodes() = runBlocking {
        val observerRepository = RoomObserverRepository(database.observerDao(), clock)
        val trimmedTerritory = territoryRepository.createTerritory(
            "  A02 ", " Северный лес ", " Нижегородская область ", " Володарский район ",
        )
        val trimmedObserver = observerRepository.createObserver(
            " SP01 ", " Иванов ", " Сергей ", "  ", "  ",
        )

        assertEquals("A02", trimmedTerritory.code)
        assertEquals("Северный лес", trimmedTerritory.name)
        assertEquals("Нижегородская область", trimmedTerritory.region)
        assertEquals("Володарский район", trimmedTerritory.district)
        assertEquals("SP01", trimmedObserver.code)
        assertEquals("Иванов", trimmedObserver.lastName)
        assertEquals("Сергей", trimmedObserver.firstName)
        assertNull(trimmedObserver.middleName)
        assertNull(trimmedObserver.contact)
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { territoryRepository.createTerritory(" ", "Имя", "Регион", "Район") }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { territoryRepository.createTerritory("C04", " ", "Регион", "Район") }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { territoryRepository.createTerritory("C04", "Имя", " ", "Район") }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { territoryRepository.createTerritory("C04", "Имя", "Регион", " ") }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { observerRepository.createObserver(" ", "Иванов", "Сергей", null, null) }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { observerRepository.createObserver("AA", " ", "Сергей", null, null) }
        }
        assertThrows(RequiredFieldException::class.java) {
            runBlocking { observerRepository.createObserver("AA", "Иванов", " ", null, null) }
        }
        assertThrows(DuplicateTerritoryCodeException::class.java) {
            runBlocking { territoryRepository.createTerritory("A02", "Другая", "Область", "Район") }
        }
        assertThrows(DuplicateObserverCodeException::class.java) {
            runBlocking { observerRepository.createObserver("SP01", "Другой", "Наблюдатель", null, null) }
        }
        Unit
    }

    @Test
    fun createdPointKeepsItsTerritoryAndObserverAfterLaterEntitiesAreCreated() = runBlocking {
        val first = createPoint()
        observationRepository.recordNoBeesFound(first.id)
        val observerRepository = RoomObserverRepository(database.observerDao(), clock)
        val otherTerritory = territoryRepository.createTerritory("B03", "У реки", "Владимирская область", "Гороховецкий район")
        val otherObserver = observerRepository.createObserver("AN02", "Петров", "Алексей", null, null)

        val savedFirst = requireNotNull(database.observationPointDao().getById(first.id)).toDomain()
        assertEquals(territoryId, savedFirst.territoryId)
        assertEquals(observerId, savedFirst.observerId)

        val second = observationRepository.createObservationPoint(
            NewObservationPoint(otherTerritory.id, otherObserver.id, latitude = 56.2, longitude = 42.8),
        )
        assertEquals(otherTerritory.id, second.territoryId)
        assertEquals(otherObserver.id, second.observerId)
    }

    @Test
    fun noBeesResultCannotReplaceFoundBees() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "RED", MarkPosition.NONE)

        assertThrows(BeesAlreadyFoundException::class.java) {
            runBlocking { observationRepository.recordNoBeesFound(point.id) }
        }

        assertEquals(listOf(bee), observationRepository.observeBees(point.id).first())
        val unchangedPoint = requireNotNull(observationRepository.observeActivePoint().first())
        assertEquals(BeePresenceResult.BEES_FOUND, unchangedPoint.beePresenceResult)
        assertNull(unchangedPoint.completedAt)
    }

    @Test
    fun ordinaryCompletionRejectsUnknownBeePresence() = runBlocking {
        val point = createPoint()

        assertThrows(BeePresenceResultRequiredException::class.java) {
            runBlocking { observationRepository.completeObservationPoint(point.id) }
        }
        assertEquals(point, observationRepository.observeActivePoint().first())
        Unit
    }

    @Test
    fun individualFirstDeparturesHaveOwnTimestampsAndOneOpenCycleIsEnforced() = runBlocking {
        val point = createPoint()
        val first = observationRepository.startFirstFlight(point.id, "WHITE", MarkPosition.NONE)
        clock.advanceSeconds(7)
        val second = observationRepository.startFirstFlight(
            point.id,
            "BLUE",
            MarkPosition.RIGHT_WING,
        )
        val firstBee = first.bee
        val secondBee = second.bee
        val initialCycles = listOf(first.flightCycle, second.flightCycle)
        assertEquals(setOf(1), initialCycles.map { it.sequenceNumber }.toSet())
        assertEquals(2, initialCycles.map { it.departureTime }.toSet().size)
        initialCycles.forEach { cycle ->
            assertEquals(cycle.departureTime, cycle.createdAt)
            assertEquals(cycle.departureTime, cycle.updatedAt)
            assertFalse(cycle.isInitialGroupLaunch)
        }

        assertThrows(OpenFlightCycleExistsException::class.java) {
            runBlocking { observationRepository.startNextFlight(firstBee.id) }
        }

        clock.advanceSeconds(20)
        val returned = observationRepository.registerBeeReturn(firstBee.id)
        assertEquals(clock.instant(), returned.returnTime)
        assertEquals(clock.instant(), returned.updatedAt)
        clock.advanceSeconds(5)
        val next = observationRepository.startNextFlight(firstBee.id)
        assertEquals(2, next.sequenceNumber)
        assertEquals(next.departureTime, next.createdAt)
        assertEquals(next.departureTime, next.updatedAt)

        val firstBeeCycles = observationRepository.observeFlightCycles(firstBee.id).first()
        val secondBeeCycles = observationRepository.observeFlightCycles(secondBee.id).first()
        assertEquals(2, firstBeeCycles.size)
        assertEquals(1, secondBeeCycles.size)
        assertEquals(
            3,
            observationRepository.observeFlightCyclesForPoint(point.id).first().size,
        )
    }

    @Test
    fun repeatedReturnCannotCreateOrRewriteAnotherEvent() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        observationRepository.startInitialGroupRelease(point.id)
        clock.advanceSeconds(30)
        val returned = observationRepository.registerBeeReturn(bee.id)

        assertThrows(OpenFlightCycleNotFoundException::class.java) {
            runBlocking { observationRepository.registerBeeReturn(bee.id) }
        }

        assertEquals(
            listOf(returned),
            observationRepository.observeFlightCycles(bee.id).first(),
        )
    }

    @Test
    fun activeReleasedPointRestoresAsObservationWorkflow() = runBlocking {
        val point = createPoint()
        observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        observationRepository.startInitialGroupRelease(point.id)

        val restoredPoint = observationRepository.observeActivePoint().first()
        val restoredCycles = observationRepository.observeFlightCyclesForPoint(point.id).first()
        val territory = requireNotNull(territoryRepository.getTerritory(territoryId))

        assertEquals(point.id, restoredPoint?.id)
        assertTrue(restoredCycles.isNotEmpty())
        assertEquals(
            StartupDestination.ResumeObservation(restoredPoint!!),
            StartupRouter.decide(
                restoredPoint,
                territoryId,
                listOf(territory),
                observerId,
                listOf(requireNotNull(RoomObserverRepository(database.observerDao(), clock).getObserver(observerId))),
            ),
        )
    }

    @Test
    fun completionPreservesMixedOpenAndClosedFlightCycles() = runBlocking {
        val point = createPoint()
        val flyingBeeA = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val atPointBee = observationRepository.addBee(point.id, "BLUE", MarkPosition.RIGHT_WING)
        val flyingBeeC = observationRepository.addBee(point.id, "RED", MarkPosition.LEFT_WING)
        observationRepository.startInitialGroupRelease(point.id)
        clock.advanceSeconds(75)
        val returned = observationRepository.registerBeeReturn(atPointBee.id)
        val beforeCompletion = observationRepository.observeFlightCyclesForPoint(point.id).first()

        clock.advanceSeconds(10)
        val completed = observationRepository.completeObservationPoint(point.id)
        val afterCompletion = observationRepository.observeFlightCyclesForPoint(point.id).first()

        assertTrue(completed.completedAt != null)
        assertNull(observationRepository.observeActivePoint().first())
        assertEquals(beforeCompletion, afterCompletion)
        assertNull(afterCompletion.single { it.beeId == flyingBeeA.id }.returnTime)
        assertEquals(returned.returnTime, afterCompletion.single { it.beeId == atPointBee.id }.returnTime)
        assertNull(afterCompletion.single { it.beeId == flyingBeeC.id }.returnTime)
    }

    @Test
    fun shortFirstIndividualCycleUsesNormalEligibilityAndCanContinue() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val firstCycle = observationRepository.startInitialGroupRelease(point.id).single()

        clock.advanceSeconds(20)
        val closedFirstCycle = observationRepository.registerBeeReturn(bee.id)
        assertEquals(firstCycle.departureTime, closedFirstCycle.departureTime)
        assertFalse(closedFirstCycle.isExcludedFromFlightDurationAnalysis)

        clock.advanceSeconds(147)
        val actualDepartureTime = clock.instant()
        val secondCycle = observationRepository.startNextFlight(bee.id)
        assertEquals(2, secondCycle.sequenceNumber)
        assertEquals(actualDepartureTime, secondCycle.departureTime)

        clock.advanceSeconds(20)
        val closedSecondCycle = observationRepository.registerBeeReturn(bee.id)
        assertFalse(closedSecondCycle.isExcludedFromFlightDurationAnalysis)

        val persistedCycles = observationRepository.observeFlightCycles(bee.id).first()
        assertEquals(listOf(1, 2), persistedCycles.map { it.sequenceNumber })
        assertEquals(firstCycle.departureTime, persistedCycles.first().departureTime)
        assertEquals(actualDepartureTime, persistedCycles.last().departureTime)
    }

    @Test
    fun databaseAndRepositoryProtectCoreConstraints() = runBlocking {
        val point = createPoint()
        observationRepository.addBee(point.id, "WHITE", MarkPosition.LEFT_WING)

        assertThrows(DuplicateBeeMarkException::class.java) {
            runBlocking {
                observationRepository.addBee(point.id, "WHITE", MarkPosition.LEFT_WING)
            }
        }

        val orphan = BeeEntity(
            id = UUID.randomUUID(),
            observationPointId = UUID.randomUUID(),
            markColor = "BLUE",
            markPosition = MarkPosition.NONE,
            createdAt = clock.instant(),
        )
        assertThrows(Exception::class.java) {
            runBlocking { database.beeDao().insert(orphan) }
        }
        Unit
    }

    @Test
    fun everyMarkPositionAndIndependentMarkDimensionsCanBeAdded() = runBlocking {
        val point = createPoint()

        MarkPosition.entries.forEach { position ->
            observationRepository.addBee(point.id, "WHITE", position)
        }
        observationRepository.addBee(point.id, "BLUE", MarkPosition.RIGHT_WING)

        val restored = observationRepository.observeBees(point.id).first()
        assertEquals(4, restored.size)
        assertEquals(MarkPosition.entries.toSet(), restored.filter { it.markColor == "WHITE" }.map { it.markPosition }.toSet())
        assertEquals(2, restored.count { it.markPosition == MarkPosition.RIGHT_WING })
    }

    @Test
    fun duplicateMarkCombinationIsRejectedExplicitly() = runBlocking {
        val point = createPoint()
        observationRepository.addBee(point.id, "RED", MarkPosition.LEFT_WING)

        assertThrows(DuplicateBeeMarkException::class.java) {
            runBlocking { observationRepository.addBee(point.id, "RED", MarkPosition.LEFT_WING) }
        }
        Unit
    }

    @Test
    fun cancellingOneFirstDeparturePreservesOtherBeeAndFreesOneSlot() = runBlocking {
        val point = createPoint()
        val removed = observationRepository.startFirstFlight(point.id, "GREEN", MarkPosition.NONE).bee
        val retained = observationRepository.startFirstFlight(
            point.id,
            "YELLOW",
            MarkPosition.RIGHT_WING,
        ).bee

        observationRepository.undoLastBeeAction(removed.id)

        assertEquals(listOf(retained), observationRepository.observeBees(point.id).first())
        assertEquals(
            BeePresenceResult.BEES_FOUND,
            observationRepository.observeActivePoint().first()?.beePresenceResult,
        )
    }

    @Test
    fun repositoryLimitsRealBeesToTenAndCancellationFreesTheSlot() = runBlocking {
        val point = createPoint()

        val started = BeeMarkCatalog.supportedCombinations.take(10).map { mark ->
            observationRepository.startFirstFlight(point.id, mark.markColor, mark.markPosition)
        }
        val eleventh = BeeMarkCatalog.supportedCombinations[10]
        assertThrows(org.beesearch.app.domain.model.BeeLimitReachedException::class.java) {
            runBlocking {
                observationRepository.startFirstFlight(
                    point.id,
                    eleventh.markColor,
                    eleventh.markPosition,
                )
            }
        }

        observationRepository.undoLastBeeAction(started.last().bee.id)
        val replacement = observationRepository.startFirstFlight(
            point.id,
            eleventh.markColor,
            eleventh.markPosition,
        )
        assertEquals(10, observationRepository.observeBees(point.id).first().size)
        assertEquals(eleventh.markColor, replacement.bee.markColor)
    }

    @Test
    fun azimuthAllowsZeroRejects360AndCanBeRemoved() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "YELLOW", MarkPosition.NONE)
        val cycle = observationRepository.startInitialGroupRelease(point.id).single()

        val manuallySet = observationRepository.setFlightAzimuth(cycle.id, 0.0)
        assertEquals(0.0, manuallySet.azimuthDeg)
        assertFalse(manuallySet.azimuthCaptureConsumed)
        assertThrows(InvalidAzimuthException::class.java) {
            runBlocking { observationRepository.setFlightAzimuth(cycle.id, 360.0) }
        }
        val manuallyCleared = observationRepository.setFlightAzimuth(cycle.id, null)
        assertNull(manuallyCleared.azimuthDeg)
        assertFalse(manuallyCleared.azimuthCaptureConsumed)
        assertEquals(bee.id, cycle.beeId)
    }

    @Test
    fun fieldCaptureIsSingleUseUndoKeepsItConsumedAndNextCycleResetsIt() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val firstCycle = observationRepository.startInitialGroupRelease(point.id).single()

        assertNull(firstCycle.azimuthDeg)
        assertFalse(firstCycle.azimuthCaptureConsumed)

        val captured = observationRepository.captureFlightAzimuth(firstCycle.id, 247.0)
        assertEquals(247.0, captured.azimuthDeg)
        assertTrue(captured.azimuthCaptureConsumed)
        assertThrows(AzimuthCaptureAlreadyConsumedException::class.java) {
            runBlocking { observationRepository.captureFlightAzimuth(firstCycle.id, 250.0) }
        }

        val undone = observationRepository.setFlightAzimuth(firstCycle.id, null)
        assertNull(undone.azimuthDeg)
        assertTrue(undone.azimuthCaptureConsumed)
        assertThrows(AzimuthCaptureAlreadyConsumedException::class.java) {
            runBlocking { observationRepository.captureFlightAzimuth(firstCycle.id, 250.0) }
        }

        clock.advanceSeconds(30)
        observationRepository.registerBeeReturn(bee.id)
        clock.advanceSeconds(5)
        val secondCycle = observationRepository.startNextFlight(bee.id)
        assertNull(secondCycle.azimuthDeg)
        assertFalse(secondCycle.azimuthCaptureConsumed)
        assertEquals(132.0, observationRepository.captureFlightAzimuth(secondCycle.id, 132.0).azimuthDeg)
    }

    @Test
    fun fieldCaptureRejectsClosedCycle() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "BLUE", MarkPosition.NONE)
        val cycle = observationRepository.startInitialGroupRelease(point.id).single()
        clock.advanceSeconds(30)
        val closed = observationRepository.registerBeeReturn(bee.id)

        assertFalse(closed.azimuthCaptureConsumed)
        assertThrows(AzimuthCaptureRequiresOpenFlightCycleException::class.java) {
            runBlocking { observationRepository.captureFlightAzimuth(cycle.id, 90.0) }
        }
        Unit
    }

    @Test
    fun technicalCaptureFailureLeavesAzimuthAndConsumptionUnchanged() = runBlocking {
        val point = createPoint()
        observationRepository.addBee(point.id, "GREEN", MarkPosition.NONE)
        val cycle = observationRepository.startInitialGroupRelease(point.id).single()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_field_azimuth_capture
            BEFORE UPDATE OF azimuth_capture_consumed ON flight_cycles
            BEGIN
                SELECT RAISE(ABORT, 'forced capture failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            runBlocking { observationRepository.captureFlightAzimuth(cycle.id, 247.0) }
        }

        val unchanged = observationRepository.observeFlightCyclesForPoint(point.id).first().single()
        assertNull(unchanged.azimuthDeg)
        assertFalse(unchanged.azimuthCaptureConsumed)
    }

    @Test
    fun azimuthIsEditableScopedToCycleAndRestoredWithoutChangingFlightWorkflow() = runBlocking {
        val point = createPoint()
        val measuredBee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val otherBee = observationRepository.addBee(point.id, "BLUE", MarkPosition.RIGHT_WING)
        val initialCycles = observationRepository.startInitialGroupRelease(point.id)
        val measuredFirstCycle = initialCycles.single { it.beeId == measuredBee.id }
        val otherFirstCycle = initialCycles.single { it.beeId == otherBee.id }

        observationRepository.setFlightAzimuth(measuredFirstCycle.id, 247.0)
        observationRepository.setFlightAzimuth(measuredFirstCycle.id, 259.0)

        val restoredRepository = RoomObservationRepository(
            database = database,
            territoryDao = database.territoryDao(),
            pointDao = database.observationPointDao(),
            observerDao = database.observerDao(),
            beeDao = database.beeDao(),
            cycleDao = database.flightCycleDao(),
            clock = clock,
            observationZoneIdProvider = clock::getZone,
        )
        val restoredInitialCycles = restoredRepository.observeFlightCyclesForPoint(point.id).first()
        assertEquals(259.0, restoredInitialCycles.single { it.id == measuredFirstCycle.id }.azimuthDeg)
        assertNull(restoredInitialCycles.single { it.id == otherFirstCycle.id }.azimuthDeg)

        clock.advanceSeconds(20)
        val returned = restoredRepository.registerBeeReturn(measuredBee.id)
        assertEquals(259.0, returned.azimuthDeg)
        assertFalse(returned.isExcludedFromFlightDurationAnalysis)

        clock.advanceSeconds(5)
        val secondCycle = restoredRepository.startNextFlight(measuredBee.id)
        assertEquals(2, secondCycle.sequenceNumber)
        assertNull(secondCycle.azimuthDeg)

        val beforeCompletion = restoredRepository.observeFlightCyclesForPoint(point.id).first()
        restoredRepository.completeObservationPoint(point.id)
        val afterCompletion = restoredRepository.observeFlightCyclesForPoint(point.id).first()

        assertEquals(beforeCompletion, afterCompletion)
        assertEquals(259.0, afterCompletion.single { it.id == measuredFirstCycle.id }.azimuthDeg)
        assertNull(afterCompletion.single { it.id == secondCycle.id }.azimuthDeg)
        assertNull(afterCompletion.single { it.id == otherFirstCycle.id }.azimuthDeg)
    }

    @Test
    fun undoLastBeeActionRestoresArrivalToTheOriginalDepartureTime() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val firstCycle = observationRepository.startInitialGroupRelease(point.id).single()
        val departure = firstCycle.departureTime

        clock.advanceSeconds(360)
        val returned = observationRepository.registerBeeReturn(bee.id)
        assertEquals(BeeUndoAction.RETURN, observationRepository.undoLastBeeAction(bee.id))

        val restored = observationRepository.observeFlightCycles(bee.id).first().single()
        assertEquals(firstCycle.id, restored.id)
        assertEquals(departure, restored.departureTime)
        assertNull(restored.returnTime)
        assertEquals(returned.returnTime, clock.instant())
    }

    @Test
    fun undoLastBeeActionDeletesOnlyLatestRepeatFlightAndReusesSequenceNumber() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "BLUE", MarkPosition.NONE)
        val firstCycle = observationRepository.startInitialGroupRelease(point.id).single()
        clock.advanceSeconds(120)
        val returned = observationRepository.registerBeeReturn(bee.id)
        clock.advanceSeconds(40)
        val secondCycle = observationRepository.startNextFlight(bee.id)

        assertEquals(BeeUndoAction.NEXT_FLIGHT, observationRepository.undoLastBeeAction(bee.id))
        val afterUndo = observationRepository.observeFlightCycles(bee.id).first()
        assertEquals(listOf(firstCycle.id), afterUndo.map { it.id })
        assertEquals(returned.returnTime, afterUndo.single().returnTime)

        clock.advanceSeconds(10)
        val retriedSecondCycle = observationRepository.startNextFlight(bee.id)
        assertEquals(2, retriedSecondCycle.sequenceNumber)
        assertFalse(retriedSecondCycle.id == secondCycle.id)
    }

    @Test
    fun undoLastBeeActionUnwindsAzimuthBeforeTheLatestRepeatFlightWithoutTouchingOtherBee() = runBlocking {
        val point = createPoint()
        val correctedBee = observationRepository.addBee(point.id, "RED", MarkPosition.NONE)
        val otherBee = observationRepository.addBee(point.id, "GREEN", MarkPosition.RIGHT_WING)
        val initialCycles = observationRepository.startInitialGroupRelease(point.id)
        val correctedFirst = initialCycles.single { it.beeId == correctedBee.id }
        val otherInitial = initialCycles.single { it.beeId == otherBee.id }
        clock.advanceSeconds(80)
        val returned = observationRepository.registerBeeReturn(correctedBee.id)
        clock.advanceSeconds(20)
        observationRepository.startNextFlight(correctedBee.id)
        val second = observationRepository.observeFlightCycles(correctedBee.id).first().last()
        observationRepository.captureFlightAzimuth(second.id, 138.0)

        assertEquals(BeeUndoAction.AZIMUTH, observationRepository.undoLastBeeAction(correctedBee.id))
        val withoutAzimuth = observationRepository.observeFlightCycles(correctedBee.id).first().last()
        assertNull(withoutAzimuth.azimuthDeg)
        assertTrue(withoutAzimuth.azimuthCaptureConsumed)

        assertEquals(BeeUndoAction.NEXT_FLIGHT, observationRepository.undoLastBeeAction(correctedBee.id))
        val correctedAfterUndo = observationRepository.observeFlightCycles(correctedBee.id).first()
        assertEquals(listOf(correctedFirst.id), correctedAfterUndo.map { it.id })
        assertEquals(returned.returnTime, correctedAfterUndo.single().returnTime)
        assertEquals(listOf(otherInitial), observationRepository.observeFlightCycles(otherBee.id).first())
    }

    @Test
    fun firstDepartureUndoRemovesBeeAndCycleAndReleasesItsMark() = runBlocking {
        val point = createPoint()
        val started = observationRepository.startFirstFlight(point.id, "WHITE", MarkPosition.NONE)
        assertEquals(
            BeeUndoAction.FIRST_DEPARTURE,
            observationRepository.undoLastBeeAction(started.bee.id),
        )

        assertTrue(observationRepository.observeBees(point.id).first().isEmpty())
        assertTrue(observationRepository.observeFlightCyclesForPoint(point.id).first().isEmpty())
        assertNull(observationRepository.observeActivePoint().first()?.beePresenceResult)

        val retried = observationRepository.startFirstFlight(point.id, "WHITE", MarkPosition.NONE)
        assertEquals(1, retried.flightCycle.sequenceNumber)
        assertFalse(retried.flightCycle.isInitialGroupLaunch)
        assertTrue(retried.flightCycle.isFirstDepartureCancellationEligible)
    }

    @Test
    fun firstDepartureDeletionStaysUnavailableAfterARecordedReturnWasCorrected() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.startFirstFlight(
            point.id,
            "GREEN",
            MarkPosition.LEFT_WING,
        ).bee

        clock.advanceSeconds(45)
        observationRepository.registerBeeReturn(bee.id)
        assertEquals(BeeUndoAction.RETURN, observationRepository.undoLastBeeAction(bee.id))

        val restoredFirstCycle = observationRepository.observeFlightCycles(bee.id).first().single()
        assertNull(restoredFirstCycle.returnTime)
        assertFalse(restoredFirstCycle.isFirstDepartureCancellationEligible)
        assertThrows(NoReversibleBeeActionException::class.java) {
            runBlocking { observationRepository.undoLastBeeAction(bee.id) }
        }
        Unit
    }

    @Test
    fun territoryCodeConstraintMapsToDomainErrorForCreateAndUpdate() = runBlocking {
        assertThrows(DuplicateTerritoryCodeException::class.java) {
            runBlocking { territoryRepository.createTerritory("KLYAZMA-01", "Дубликат", "Область", "Район") }
        }

        territoryRepository.createTerritory("OTHER", "Другая", "Область", "Район")
        assertThrows(DuplicateTerritoryCodeException::class.java) {
            runBlocking { territoryRepository.createTerritory("KLYAZMA-01", "Дубликат", "Область", "Район") }
        }
        Unit
    }

    private suspend fun createPoint(
        observerId: UUID = this.observerId,
        pointTerritoryId: UUID = territoryId,
    ) = observationRepository.createObservationPoint(newPoint(observerId, pointTerritoryId))

    private fun newPoint(
        observerId: UUID = this.observerId,
        pointTerritoryId: UUID = territoryId,
    ) = NewObservationPoint(
        territoryId = pointTerritoryId,
        observerId = observerId,
        latitude = 56.1959786,
        longitude = 42.7477116,
        gpsLatitude = 56.1959000,
        gpsLongitude = 42.7477000,
        gpsAccuracyM = 4.5,
    )

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }

        fun set(instant: Instant) {
            current = instant
        }
    }
}
