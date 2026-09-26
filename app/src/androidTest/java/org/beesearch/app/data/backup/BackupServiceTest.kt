package org.beesearch.app.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.*
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.backup.*
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var source: BeeSearchDatabase
    private lateinit var target: BeeSearchDatabase
    private lateinit var archive: File
    private lateinit var secondArchive: File
    private lateinit var sourceStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var targetStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before fun setUp() {
        source = database(); target = database()
        archive = temp("backup.zip"); secondArchive = temp("backup-2.zip")
        sourceStore = dataStore(temp("source.preferences_pb")); targetStore = dataStore(temp("target.preferences_pb"))
    }

    @After fun tearDown() {
        source.close(); target.close(); scope.cancel()
        listOf(archive, secondArchive).forEach(File::delete)
    }

    @Test fun fullGraphRoundTripPreservesCanonicalLogicalContentAndPortableSettings() = runBlocking {
        val ids = seed(source)
        sourceStore.edit {
            it[stringPreferencesKey("current_territory_id")] = ids.territory2.toString()
            it[stringPreferencesKey("current_observer_id")] = ids.observer2.toString()
            it[stringPreferencesKey("map_coverage_${ids.territory1}")] = "v1|56.2,42.8,56.1,42.7"
            it[stringPreferencesKey("map_package_active_${ids.territory1}")] = "source.pmtiles"
            it[stringPreferencesKey("map-poc-path")] = "/private/source/path"
        }
        targetStore.edit { it[stringPreferencesKey("map_package_active_${ids.territory1}")] = "target.pmtiles" }
        val sourceService = service(source, sourceStore)
        sourceService.export(archive)
        val before = sourceService.validate(archive)

        service(target, targetStore).restore(archive)
        service(target, targetStore).export(secondArchive)
        val after = service(target, targetStore).validate(secondArchive)

        assertEquals(before.logicalContentSha256, after.logicalContentSha256)
        assertEquals(2, target.backupDao().territoryCount())
        assertEquals(2, target.backupDao().observerCount())
        assertEquals(2, target.backupDao().observationPointCount())
        assertEquals(2, target.backupDao().beeCount())
        assertEquals(3, target.backupDao().flightCycleCount())
        val prefs = targetStore.data.first()
        assertEquals(ids.territory2.toString(), prefs[stringPreferencesKey("current_territory_id")])
        assertEquals(ids.observer2.toString(), prefs[stringPreferencesKey("current_observer_id")])
        assertEquals("v1|56.2,42.8,56.1,42.7", prefs[stringPreferencesKey("map_coverage_${ids.territory1}")])
        assertEquals("target.pmtiles", prefs[stringPreferencesKey("map_package_active_${ids.territory1}")])
        val names = zipEntries(archive).keys
        assertFalse(names.any { it.startsWith(BackupContractV4.ATTACHMENT_PREFIX) })
        assertFalse(zipEntries(archive).values.any { String(it).contains("source.pmtiles") || String(it).contains("/private/source/path") })
    }

    @Test fun completeRestoreKeepsTheInstallingDevicesOfferState() = runBlocking {
        val ids = seed(source)
        sourceStore.edit {
            it[stringPreferencesKey("current_territory_id")] = ids.territory2.toString()
            it[stringPreferencesKey("current_observer_id")] = ids.observer2.toString()
        }
        val installStateFile = temp("install-state.preferences_pb")
        val targetInstallState = dataStore(installStateFile)
        try {
            val targetRepository = DataStoreSettingsRepository(targetStore, targetInstallState)
            // The target installation already handled the Initial Setup offer on this device.
            targetRepository.setInitialSetupOfferHandled(true)

            service(source, sourceStore).export(archive)
            val archiveText = zipEntries(archive).values.joinToString("\n") { String(it) }
            assertFalse(
                "Complete Backup must not export the install-local offer state",
                archiveText.contains("initial_setup_offer_handled"),
            )
            assertFalse(archiveText.contains("offerHandled"))
            assertFalse(archiveText.contains("install-state"))

            service(target, targetStore).restore(archive)

            assertTrue(
                "a Complete restore must not import or reset the target installation's offer state",
                targetRepository.getSettings().initialSetupOfferHandled,
            )
            assertEquals(ids.territory2, targetRepository.getSettings().currentTerritoryId)
        } finally {
            installStateFile.delete()
        }
    }

    @Test fun v3RoundTripPreservesPropertiesWeatherAndPhotoBytes() = runBlocking {
        seed(source)
        val point = source.backupDao().observationPoints().single { it.code == "P2" }
        val attachmentId = UUID.randomUUID()
        val sourceFiles = ObservationAttachmentFileStore(temp("source-files"), temp("source-staging"))
        val payload = "tiny-photo".toByteArray()
        val stored = sourceFiles.importPhoto(point.id, attachmentId) { ByteArrayInputStream(payload) }
        source.backupDao().insertObservationPointAttachments(listOf(ObservationPointAttachmentEntity(attachmentId, point.id, AttachmentType.PHOTO, stored.relativePath, "bee.jpg", "image/jpeg", stored.byteSize, stored.sha256, NOW)))
        source.backupDao().insertObservationPointWeather(listOf(ObservationPointWeatherEntity(point.id, WeatherStatus.LOADED, 18.4, 2.1, 247.0, NOW, NOW.plusSeconds(10), "Open-Meteo")))
        val targetFiles = ObservationAttachmentFileStore(temp("target-files"), temp("target-staging"))
        service(source, sourceStore, sourceFiles).export(archive)
        service(target, targetStore, targetFiles).restore(archive)
        val restoredPoint = target.backupDao().observationPoints().single { it.code == "P2" }
        assertEquals("description", restoredPoint.description)
        assertEquals(payload.toList(), targetFiles.resolve(stored.relativePath).readBytes().toList())
        assertEquals(1, target.backupDao().observationPointAttachments().size)
        assertEquals(
            WeatherStatus.LOADED,
            target.backupDao().observationPointWeather().single { it.observationPointId == point.id }.status,
        )
    }

    @Test fun v1RestoreCreatesPendingWeatherAndNoProperties() = runBlocking {
        seed(source)
        service(source, sourceStore).export(archive)
        val v1 = convertV3ToV1(zipEntries(archive))
        service(target, targetStore).restore(v1)
        assertTrue(target.backupDao().observationPoints().all { it.description == null })
        assertEquals(2, target.backupDao().observationPointWeather().size)
        assertTrue(target.backupDao().observationPointWeather().all { it.status == WeatherStatus.PENDING })
        assertTrue(target.backupDao().observationPointAttachments().isEmpty())
    }

    @Test fun attachmentBytesAndOwnersAreValidatedBeforeRestore() = runBlocking {
        seed(source)
        val point = source.backupDao().observationPoints().single { it.code == "P2" }
        val attachmentId = UUID.randomUUID()
        val files = ObservationAttachmentFileStore(temp("files"), temp("staging"))
        val stored = files.importPhoto(point.id, attachmentId) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        source.backupDao().insertObservationPointAttachments(listOf(ObservationPointAttachmentEntity(attachmentId, point.id, AttachmentType.PHOTO, stored.relativePath, null, "image/jpeg", stored.byteSize, stored.sha256, NOW)))
        service(source, sourceStore, files).export(archive)
        val base = zipEntries(archive)
        val archivePath = "${BackupContractV3.ATTACHMENT_PREFIX}${point.id}/$attachmentId"
        assertFailure<MissingBackupCollection>(mutate(base) { it.remove(archivePath) })
        assertFailure<BackupIntegrityMismatch>(mutate(base) { it[archivePath] = byteArrayOf(9, 9, 9) })
        val metadata = String(base.getValue(BackupContractV3.collections.getValue("observation-point-attachments")))
            .replace(point.id.toString(), UUID.randomUUID().toString())
        assertFailure<BrokenBackupForeignKey>(replaceCollection(base, "observation-point-attachments", metadata))
        assertFailure<MalformedBackup>(mutate(base) { it["attachments/../escape"] = byteArrayOf(1) })
    }

    @Test fun manifestContainsAllV5RequiredCollections() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val manifest = String(zipEntries(archive).getValue("manifest.json"))
        BackupContractV5.collections.forEach { (name, path) ->
            assertTrue(manifest.contains("\"name\":\"$name\"")); assertTrue(manifest.contains("\"path\":\"$path\""))
        }
        assertEquals(15, "\"collectionSchemaVersion\":1".toRegex().findAll(manifest).count())
        assertTrue(manifest.contains("\"backupFormatVersion\":5"))
        assertTrue(manifest.contains("\"archiveSchemaVersion\":5"))
        assertTrue(manifest.contains("\"roomSchemaVersion\":10"))
    }

    @Test fun v3RoundTripPreservesPhysicalObjectsApiaryAndBeeAssociation() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        val apiaryId = UUID.randomUUID()
        val bee = source.backupDao().bees().first()
        source.backupDao().insertPhysicalObjects(
            listOf(
                PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 1, 56.3, 42.9, NOW),
                PhysicalObjectEntity(apiaryId, ids.territory2, PhysicalObjectType.APIARY, 1, 56.4, 43.0, NOW),
            ),
        )
        source.backupDao().insertHollows(
            listOf(HollowEntity(hollowId, null, null, null, null, null, null)),
        )
        source.backupDao().insertApiaries(listOf(ApiaryEntity(apiaryId, "Пасека Иванова")))
        source.beeDao().setSourceObject(bee.id, hollowId)

        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(convertV4ToV3(zipEntries(archive)))

        assertEquals(source.backupDao().physicalObjects(), target.backupDao().physicalObjects())
        assertEquals(source.backupDao().apiaries(), target.backupDao().apiaries())
        assertEquals(hollowId, target.backupDao().bees().single { it.id == bee.id }.sourceObjectId)
    }

    @Test fun v4RoundTripPreservesSubtypeCreatorMediaAndBeeAssociation() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        val logHiveId = UUID.randomUUID()
        val imageId = UUID.randomUUID()
        val videoId = UUID.randomUUID()
        val imageBytes = "hollow-image".toByteArray()
        val videoBytes = "log-hive-video".toByteArray()
        val mediaStore = PhysicalObjectMediaFileStore(temp("object-source-files"), temp("object-source-cache"))
        source.backupDao().insertPhysicalObjects(
            listOf(
                PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 1, 56.3, 42.9, NOW, ids.observer2),
                PhysicalObjectEntity(logHiveId, ids.territory2, PhysicalObjectType.LOG_HIVE, 1, 56.4, 43.0, NOW, ids.observer2),
            ),
        )
        source.backupDao().insertHollows(listOf(HollowEntity(hollowId, "дуб", 180.0, 123, 40.0, 25.0, "hollow note")))
        source.backupDao().insertLogHives(listOf(LogHiveEntity(logHiveId, "сосна", 150.0, 90, 50.0, "липа", 30.0, 80.0, "log note")))
        val image = mediaStore.importMedia(hollowId, imageId) { imageBytes.inputStream() }
        val video = mediaStore.importMedia(logHiveId, videoId) { videoBytes.inputStream() }
        source.backupDao().insertPhysicalObjectMedia(
            listOf(
                PhysicalObjectMediaEntity(imageId, hollowId, PhysicalObjectMediaType.IMAGE, image.relativePath, "hollow.jpg", "image/jpeg", image.byteSize, image.sha256, NOW),
                PhysicalObjectMediaEntity(videoId, logHiveId, PhysicalObjectMediaType.VIDEO, video.relativePath, "log.mp4", "video/mp4", video.byteSize, video.sha256, NOW),
            ),
        )
        val bee = source.backupDao().bees().first()
        source.beeDao().setSourceObject(bee.id, hollowId)
        val objectTargetStore = PhysicalObjectMediaFileStore(temp("object-target-files"), temp("object-target-cache"))
        service(source, sourceStore, objectMediaStore = mediaStore).export(archive)
        val manifest = String(zipEntries(archive).getValue("manifest.json"))
        assertTrue(manifest.contains("\"backupFormatVersion\":5"))
        service(target, targetStore, objectMediaStore = objectTargetStore).restore(archive)
        assertEquals(source.backupDao().physicalObjects(), target.backupDao().physicalObjects())
        assertEquals(source.backupDao().hollows(), target.backupDao().hollows())
        assertEquals(source.backupDao().logHives(), target.backupDao().logHives())
        assertEquals(source.backupDao().physicalObjectMedia(), target.backupDao().physicalObjectMedia())
        assertEquals(hollowId, target.backupDao().bees().single { it.id == bee.id }.sourceObjectId)
        assertEquals(imageBytes.toList(), objectTargetStore.resolve(image.relativePath).readBytes().toList())
        assertEquals(videoBytes.toList(), objectTargetStore.resolve(video.relativePath).readBytes().toList())
    }

    @Test fun v5RoundTripPreservesNumberingStateOfEmptyAndDeletedScopes() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 5, 56.3, 42.9, NOW, ids.observer2)),
        )
        source.backupDao().insertHollows(listOf(HollowEntity(hollowId, "дуб", 180.0, 123, 40.0, 25.0, null)))
        // The counter stands above the highest stored object, and one scope was already reset.
        source.backupDao().insertPhysicalObjectSequences(
            listOf(
                PhysicalObjectSequenceEntity(ids.territory2, PhysicalObjectType.HOLLOW, 9),
                PhysicalObjectSequenceEntity(ids.territory2, PhysicalObjectType.LOG_HIVE, 0),
            ),
        )

        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(archive)

        assertEquals(source.backupDao().physicalObjectSequences(), target.backupDao().physicalObjectSequences())
        assertEquals(9, target.backupDao().physicalObjectSequences().single {
            it.objectType == PhysicalObjectType.HOLLOW
        }.lastIssued)
        val repository = objectRepository(target, ids.territory2)
        assertEquals(10, repository.createHollow(NewHollow(UUID.randomUUID(), ids.territory2, ids.observer2, 56.6, 43.2, HollowProperties("дуб", 1.0, 0, 1.0, null, null))).sequenceNumber)
        assertEquals(1, repository.createLogHive(NewLogHive(UUID.randomUUID(), ids.territory2, ids.observer2, 56.7, 43.3, LogHiveProperties("сосна", 1.0, 0, 1.0, "липа", 1.0, 1.0, null))).sequenceNumber)
    }

    @Test fun v4ArchiveWithoutSequenceStateBootstrapsFromStoredNumbers() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 3, 56.3, 42.9, NOW, ids.observer2)),
        )
        source.backupDao().insertHollows(listOf(HollowEntity(hollowId, "дуб", 180.0, 123, 40.0, 25.0, null)))
        source.backupDao().insertPhysicalObjectSequences(
            listOf(PhysicalObjectSequenceEntity(ids.territory2, PhysicalObjectType.HOLLOW, 7)),
        )

        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(convertV5ToV4(zipEntries(archive)))

        assertTrue(target.backupDao().physicalObjectSequences().isEmpty())
        assertEquals(3, target.backupDao().physicalObjects().single { it.id == hollowId }.sequenceNumber)
        val repository = objectRepository(target, ids.territory2)
        assertEquals(4, repository.createHollow(NewHollow(UUID.randomUUID(), ids.territory2, ids.observer2, 56.6, 43.2, HollowProperties("дуб", 1.0, 0, 1.0, null, null))).sequenceNumber)
    }

    @Test fun sequenceValidationRejectsUnsafeArchiveState() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 4, 56.3, 42.9, NOW, ids.observer2)),
        )
        source.backupDao().insertHollows(listOf(HollowEntity(hollowId, "дуб", 180.0, 123, 40.0, 25.0, null)))
        source.backupDao().insertPhysicalObjectSequences(
            listOf(PhysicalObjectSequenceEntity(ids.territory2, PhysicalObjectType.HOLLOW, 4)),
        )
        service(source, sourceStore).export(archive)
        val base = zipEntries(archive)
        val row = String(base.getValue(BackupContractV5.collections.getValue("physical-object-sequences"))).trim()

        assertFailure<DuplicateBackupIdentity>(replaceCollection(base, "physical-object-sequences", "$row\n$row"))
        assertFailure<BackupDomainInvariantViolation>(
            replaceCollection(base, "physical-object-sequences", row.replace("\"lastIssued\":4", "\"lastIssued\":-1")),
        )
        assertFailure<BackupDomainInvariantViolation>(
            replaceCollection(base, "physical-object-sequences", row.replace("\"lastIssued\":4", "\"lastIssued\":3")),
        )
        assertFailure<BrokenBackupForeignKey>(
            replaceCollection(base, "physical-object-sequences", row.replace(ids.territory2.toString(), UUID.randomUUID().toString())),
        )
    }

    @Test fun v3RestoreSynthesizesHistoricalNullSubtypePropertiesAndNullCreator() = runBlocking {
        val ids = seed(source)
        val hollowId = UUID.randomUUID()
        val bee = source.backupDao().bees().first()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(hollowId, ids.territory2, PhysicalObjectType.HOLLOW, 1, 56.3, 42.9, NOW, ids.observer2)),
        )
        source.backupDao().insertHollows(listOf(HollowEntity(hollowId, "дуб", 180.0, 123, 40.0, 25.0, "note")))
        source.beeDao().setSourceObject(bee.id, hollowId)
        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(convertV4ToV3(zipEntries(archive)))
        assertEquals(hollowId, target.backupDao().bees().single { it.id == bee.id }.sourceObjectId)
        val restored = target.backupDao().hollows().single { it.physicalObjectId == hollowId }
        assertNull(restored.tree)
        assertNull(restored.entranceHeightCm)
        assertNull(target.backupDao().physicalObjects().single { it.id == hollowId }.creatorObserverId)
    }

    @Test fun v2RestoreRemainsCompatibleAndHasNoPhysicalObjectFacts() = runBlocking {
        seed(source)
        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(convertV3ToV2(zipEntries(archive)))

        assertTrue(target.backupDao().physicalObjects().isEmpty())
        assertTrue(target.backupDao().apiaries().isEmpty())
        assertTrue(target.backupDao().bees().all { it.sourceObjectId == null })
    }

    @Test fun v3RejectsMissingPhysicalObjectAndApiarySubtypeBeforeRestore() = runBlocking {
        val ids = seed(source)
        val apiaryId = UUID.randomUUID()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(apiaryId, ids.territory2, PhysicalObjectType.APIARY, 1, 56.4, 43.0, NOW)),
        )
        source.backupDao().insertApiaries(listOf(ApiaryEntity(apiaryId, null)))
        val beeId = source.backupDao().bees().first().id
        source.beeDao().setSourceObject(beeId, apiaryId)
        service(source, sourceStore).export(archive)
        val base = zipEntries(archive)

        assertFailure<BrokenBackupForeignKey>(replaceCollection(base, "physical-objects", ""))
        val missingApiary = replaceCollection(base, "apiaries", "")
        assertThrows(BackupDomainInvariantViolation::class.java) {
            runBlocking { service(target, targetStore).restore(missingApiary) }
        }
        assertEquals(0, target.backupDao().total())
        val beeRows = String(base.getValue(BackupContractV3.collections.getValue("bees")))
        val danglingSource = replaceCollection(base, "bees", beeRows.replace(apiaryId.toString(), UUID.randomUUID().toString()))
        assertThrows(BrokenBackupForeignKey::class.java) {
            runBlocking { service(target, targetStore).restore(danglingSource) }
        }
        assertEquals(0, target.backupDao().total())
    }

    /** A named Ареал travels in the existing map-coverage settings section, with no archive change. */
    @Test fun aV2AreaRoundTripsThroughTheExistingSettingsSection() = runBlocking {
        val ids = seed(source)
        val area = MapArea(
            id = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d"),
            name = "Лух",
            bounds = listOf(MapGeoBounds(north = 57.111673, east = 39.026918, south = 56.562186, west = 38.470994)),
        )
        val encoded = MapAreaCodec.encode(area)
        sourceStore.edit {
            it[stringPreferencesKey("current_territory_id")] = ids.territory1.toString()
            it[stringPreferencesKey("map_coverage_${ids.territory1}")] = encoded
        }

        service(source, sourceStore).export(archive)
        service(target, targetStore).restore(archive)

        val restored = targetStore.data.first()[stringPreferencesKey("map_coverage_${ids.territory1}")]
        assertEquals(encoded, restored)
        assertEquals(MapAreaReadResult.Present(area), MapAreaCodec.decode(restored))
    }

    /**
     * An archive written by the previous release persisted `NONE` for a thorax
     * mark and `RIGHT_WING` for a mark that was really placed on the abdomen.
     * Those tokens must be read through the compatibility mapping instead of
     * being rejected, and a legacy `LEFT_WING` row must survive untouched.
     */
    @Test fun archiveWrittenBeforeThoraxAbdomenMarkingKeepsLegacySemantics() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val base = zipEntries(archive)
        val currentRows = String(base.getValue("research/bees.json")).lines().filter(String::isNotBlank)
        val pointId = field(currentRows[0], "observationPointId")
        val createdAt = NOW.toEpochMilli()
        val legacyBees = listOf(
            legacyBeeRow(field(currentRows[0], "id"), pointId, "WHITE", "NONE", createdAt),
            legacyBeeRow(field(currentRows[1], "id"), pointId, "BLUE", "RIGHT_WING", createdAt),
            legacyBeeRow(UUID.randomUUID().toString(), pointId, "RED", "LEFT_WING", createdAt),
        ).joinToString("\n", postfix = "\n")

        service(target, targetStore).restore(replaceCollection(base, "bees", legacyBees))

        val restored = target.backupDao().bees().associateBy { it.markColor }
        assertEquals(MarkPosition.THORAX, restored.getValue("WHITE").markPosition)
        assertEquals(MarkPosition.ABDOMEN, restored.getValue("BLUE").markPosition)
        assertEquals(MarkPosition.LEFT_WING, restored.getValue("RED").markPosition)
        assertEquals(3, target.backupDao().beeCount())
        assertEquals(3, target.backupDao().flightCycleCount())
    }

    /**
     * A new archive uses the new marking semantics, keeps a legacy `LEFT_WING`
     * row honest, and leaves FlightCycle content untouched.
     */
    @Test fun archiveCarriesNewMarkSemanticsAndRoundTripsThem() = runBlocking {
        seed(source)
        val populatedPoint = source.backupDao().observationPoints()
            .first { it.beePresenceResult == BeePresenceResult.BEES_FOUND }
        source.backupDao().insertBees(
            listOf(BeeEntity(UUID.randomUUID(), populatedPoint.id, "RED", MarkPosition.ABDOMEN, NOW)),
        )
        val sourceCycles = source.backupDao().flightCycles().map { it.id to it.sequenceNumber }.sortedBy { it.first }

        service(source, sourceStore).export(archive)
        val beesJson = String(zipEntries(archive).getValue("research/bees.json"))
        assertTrue(beesJson.contains("\"markPosition\":\"THORAX\""))
        assertTrue(beesJson.contains("\"markPosition\":\"ABDOMEN\""))
        assertTrue(beesJson.contains("\"markPosition\":\"LEFT_WING\""))
        assertFalse(beesJson.contains("RIGHT_WING"))
        assertFalse(beesJson.contains("\"NONE\""))

        service(target, targetStore).restore(archive)
        val restoredMarks = target.backupDao().bees().map { it.markPosition }.toSet()
        assertEquals(
            setOf(MarkPosition.THORAX, MarkPosition.ABDOMEN, MarkPosition.LEFT_WING),
            restoredMarks,
        )
        assertEquals(3, target.backupDao().beeCount())
        assertEquals(sourceCycles, target.backupDao().flightCycles().map { it.id to it.sequenceNumber }.sortedBy { it.first })
    }

    private fun legacyBeeRow(
        id: String,
        pointId: String,
        color: String,
        position: String,
        createdAt: Long,
    ) = "{\"id\":\"$id\",\"observationPointId\":\"$pointId\",\"markColor\":\"$color\"," +
        "\"markPosition\":\"$position\",\"createdAt\":$createdAt,\"sourceObjectId\":null}"

    @Test fun integrityFailuresAreExplicit() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val original = zipEntries(archive)
        assertFailure<BackupIntegrityMismatch>(mutate(original) { it["research/bees.json"] = it.getValue("research/bees.json") + 1 })
        assertFailure<BackupIntegrityMismatch>(mutate(original) { it["manifest.json"] = replaceManifestNumber(it.getValue("manifest.json"), "territories", "byteLength", 999999) })
        assertFailure<BackupIntegrityMismatch>(mutate(original) { it["manifest.json"] = replaceManifestNumber(it.getValue("manifest.json"), "territories", "recordCount", 999) })
        archive.writeBytes(archive.readBytes().copyOf(40))
        assertThrows(MalformedBackup::class.java) { service(target, targetStore).validate(archive) }
        Unit
    }

    @Test fun identityAndEveryForeignKeyAreValidatedBeforeWrite() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        val territoryRows = String(base.getValue("research/territories.json")).lines().filter(String::isNotBlank)
        val firstTerritoryId = field(territoryRows[0], "id")
        val duplicateTerritories = territoryRows[0] + "\n" + territoryRows[1].replace(field(territoryRows[1], "id"), firstTerritoryId) + "\n"
        assertFailure<DuplicateBackupIdentity>(replaceCollection(base, "territories", duplicateTerritories))
        listOf(
            "observation-points" to "territoryId",
            "observation-points" to "observerId",
            "bees" to "observationPointId",
            "flight-cycles" to "beeId",
        ).forEach { (collection, key) ->
            val path = BackupContractV3.collections.getValue(collection); val text = String(base.getValue(path))
            assertFailure<BrokenBackupForeignKey>(replaceCollection(base, collection, text.replaceFirst(field(text.lineSequence().first(), key), UUID.randomUUID().toString())))
        }
        assertEquals(0, target.backupDao().total())
    }

    @Test fun formatEvolutionFailuresAreExplicit() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        assertFailure<UnsupportedBackupFormat>(mutate(base) { it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"backupFormatVersion\":5", "\"backupFormatVersion\":6").toByteArray() })
        assertFailure<UnsupportedArchiveSchema>(mutate(base) { it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"archiveSchemaVersion\":5", "\"archiveSchemaVersion\":7").toByteArray() })
        assertFailure<MissingBackupCollection>(mutate(base) { it.remove("research/bees.json") })
        assertFailure<UnknownRequiredBackupCollection>(mutate(base) {
            val text = String(it.getValue("manifest.json")); val descriptor = "{\"name\":\"future\",\"path\":\"future.json\",\"collectionSchemaVersion\":1,\"required\":true,\"recordCount\":0,\"byteLength\":0,\"sha256\":\"${"0".repeat(64)}\"}"
            it["manifest.json"] = text.replace("\"collections\":[", "\"collections\":[$descriptor,").toByteArray()
        })
    }

    @Test fun enumsRangesTimestampsAndFlightInvariantsAreRejected() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        val points = String(base.getValue("research/observation-points.json"))
        assertFailure<BackupDomainInvariantViolation>(replaceCollection(base, "observation-points", points.replaceFirst("\"latitude\":56.1", "\"latitude\":91.0")))
        val bees = String(base.getValue("research/bees.json"))
        assertFailure<BackupDomainInvariantViolation>(replaceCollection(base, "bees", bees.replaceFirst("\"markPosition\":\"", "\"markPosition\":\"UNKNOWN_")))
        val cycles = String(base.getValue("research/flight-cycles.json"))
        assertFailure<BackupDomainInvariantViolation>(replaceCollection(base, "flight-cycles", cycles.replaceFirst("\"azimuthDeg\":0.0", "\"azimuthDeg\":360.0")))
        val cycleLines = cycles.lines().filter(String::isNotBlank).toMutableList()
        val second = cycleLines.single { it.contains("\"sequenceNumber\":2") }
        val beeId = field(second, "beeId")
        val firstIndex = cycleLines.indexOfFirst { it.contains("\"beeId\":\"$beeId\"") && it.contains("\"sequenceNumber\":1") }
        cycleLines[firstIndex] = cycleLines[firstIndex].replace(Regex("\"returnTime\":\\d+"), "\"returnTime\":null")
        assertFailure<BackupDomainInvariantViolation>(replaceCollection(base, "flight-cycles", cycleLines.joinToString("\n", postfix = "\n")))
    }

    @Test fun unknownOptionalCollectionIsIgnoredAfterItsIntegrityIsChecked() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        val bytes = "future payload\n".toByteArray(); val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val descriptor = "{\"name\":\"future\",\"path\":\"future/data.json\",\"collectionSchemaVersion\":99,\"required\":false,\"recordCount\":1,\"byteLength\":${bytes.size},\"sha256\":\"$hash\"}"
        val file = mutate(base) {
            it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"collections\":[", "\"collections\":[$descriptor,").toByteArray()
            it["future/data.json"] = bytes
        }
        service(target, targetStore).validate(file)
        Unit
    }

    @Test fun injectedMidRestoreFailureRollsBackRoomAndDoesNotTouchSettings() = runBlocking {
        val ids = seed(source)
        val apiaryId = UUID.randomUUID()
        source.backupDao().insertPhysicalObjects(
            listOf(PhysicalObjectEntity(apiaryId, ids.territory2, PhysicalObjectType.APIARY, 1, 56.4, 43.0, NOW)),
        )
        source.backupDao().insertApiaries(listOf(ApiaryEntity(apiaryId, null)))
        service(source, sourceStore).export(archive)
        val fake = FakeSettings(PortableSettingsSnapshot(null, null, emptyMap()))
        val failing = BackupService(target, fake, checkpoint = RestoreCheckpoint { if (it == "observation-points") error("forced") })
        assertThrows(BackupDatabaseRestoreFailure::class.java) { runBlocking { failing.restore(archive) } }
        assertEquals(0, target.backupDao().total()); assertEquals(0, fake.replaceCalls)
    }

    @Test fun nonEmptyDestinationIsRejectedWithoutChangingExistingDataOrSettings() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val existing = TerritoryEntity(UUID.randomUUID(), "EXISTING", "Existing", "R", "D", NOW, NOW)
        target.backupDao().insertTerritories(listOf(existing))
        val fake = FakeSettings(PortableSettingsSnapshot(null, null, emptyMap()))
        assertThrows(BackupDestinationNotEmpty::class.java) { runBlocking { BackupService(target, fake).restore(archive) } }
        assertEquals(listOf(existing), target.backupDao().territories()); assertEquals(0, fake.replaceCalls)
    }

    @Test fun settingsFailureIsPostCommitAndCanBeRetriedWithoutResearchImport() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val fake = FakeSettings(PortableSettingsSnapshot(null, null, emptyMap()), fail = true)
        val targetService = BackupService(target, fake)
        assertThrows(BackupSettingsRestoreFailure::class.java) { runBlocking { targetService.restore(archive) } }
        assertEquals(2, target.backupDao().territoryCount())
        fake.fail = false; targetService.retrySettings(archive)
        assertEquals(2, target.backupDao().territoryCount()); assertEquals(2, fake.replaceCalls)
    }

    @Test fun missingCurrentIdsAreNotAppliedAndCorruptCoverageIsRejected() = runBlocking {
        val ids = seed(source)
        sourceStore.edit { it[stringPreferencesKey("current_territory_id")] = ids.territory1.toString(); it[stringPreferencesKey("current_observer_id")] = ids.observer1.toString(); it[stringPreferencesKey("map_coverage_${ids.territory1}")] = "v1|56.2,42.8,56.1,42.7" }
        service(source, sourceStore).export(archive); val base = zipEntries(archive)
        val portable = String(base.getValue("settings/portable-settings.json")).replace(ids.territory1.toString(), UUID.randomUUID().toString()).replace(ids.observer1.toString(), UUID.randomUUID().toString())
        service(target, targetStore).restore(replaceCollection(base, "portable-settings", portable + "\n"))
        assertNull(targetStore.data.first()[stringPreferencesKey("current_territory_id")])
        assertNull(targetStore.data.first()[stringPreferencesKey("current_observer_id")])

        val badCoverage = String(base.getValue("settings/map-coverage.json")).replace("v1|56.2,42.8,56.1,42.7", "v1|bad")
        assertFailure<MalformedBackup>(replaceCollection(base, "map-coverage", badCoverage))
    }

    @Test fun zipSlipDuplicateEntryAndUnlistedEntryAreRejected() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        assertFailure<MalformedBackup>(mutate(base) { it["../escape"] = byteArrayOf() })
        assertFailure<MalformedBackup>(mutate(base) { it["extra.txt"] = byteArrayOf() })
        val tracker = ZipEntryTracker(); tracker.accept("manifest.json")
        assertThrows(MalformedBackup::class.java) { tracker.accept("manifest.json") }
        Unit
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).allowMainThreadQueries().build()
    private fun objectRepository(target: BeeSearchDatabase, territoryId: UUID) =
        org.beesearch.app.data.repository.RoomPhysicalObjectRepository(
            target,
            target.physicalObjectDao(),
            target.physicalObjectSequenceDao(),
            target.territoryDao(),
            target.observerDao(),
            target.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
    private fun dataStore(file: File) = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private fun temp(suffix: String) = File(context.cacheDir, "${UUID.randomUUID()}-$suffix")
    private fun service(
        db: BeeSearchDatabase,
        store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        attachmentStore: ObservationAttachmentFileStore? = null,
        objectMediaStore: PhysicalObjectMediaFileStore? = null,
    ) = BackupService(db, store, Clock.fixed(NOW, ZoneOffset.UTC), "test", attachmentStore = attachmentStore, objectMediaStore = objectMediaStore)
    private fun write(entries: Map<String, ByteArray>): File { ZipOutputStream(archive.outputStream()).use { z -> entries.forEach { (n,b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }; return archive }
    private fun mutate(base: Map<String, ByteArray>, block: (MutableMap<String, ByteArray>) -> Unit): File { val copy = LinkedHashMap(base); block(copy); return write(copy) }
    private fun convertV4ToV3(base: Map<String, ByteArray>): File {
        val copy = LinkedHashMap<String, ByteArray>()
        BackupContractV3.collections.values.forEach { path ->
            val bytes = base.getValue(path)
            copy[path] = if (path == BackupContractV3.collections.getValue("physical-objects")) {
                String(bytes).lineSequence().filter(String::isNotBlank).map { line ->
                    line.replace(Regex(",\"creatorObserverId\":(?:\"[^\"]*\"|null)"), "")
                }.joinToString("\n", postfix = "\n").toByteArray()
            } else bytes
        }
        val descriptors = BackupContractV3.collections.entries.joinToString(",", "[", "]") { (name, path) ->
            val bytes = copy.getValue(path)
            val count = String(bytes).lineSequence().count(String::isNotBlank)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1," +
                "\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val manifest = String(base.getValue("manifest.json"))
        copy["manifest.json"] = (manifest.substringBefore("\"collections\":")
            .replace("\"backupFormatVersion\":5", "\"backupFormatVersion\":3")
            .replace("\"archiveSchemaVersion\":5", "\"archiveSchemaVersion\":3")
            .replace("\"roomSchemaVersion\":10", "\"roomSchemaVersion\":8") +
            "\"collections\":" + descriptors + "}").toByteArray()
        return write(copy)
    }

    /**
     * Rewrites an exported V5 archive as the format the release before this feature wrote.
     *
     * The naming scope of an archive is not part of that format, so the sequence collection is
     * removed and the allocation falls back to the highest stored number after such a restore.
     */
    private fun convertV5ToV4(base: Map<String, ByteArray>): File {
        val copy = LinkedHashMap<String, ByteArray>()
        BackupContractV4.collections.values.forEach { path -> copy[path] = base.getValue(path) }
        val descriptors = BackupContractV4.collections.entries.joinToString(",", "[", "]") { (name, path) ->
            val bytes = copy.getValue(path)
            val count = String(bytes).lineSequence().count(String::isNotBlank)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1," +
                "\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val manifest = String(base.getValue("manifest.json"))
        copy["manifest.json"] = (manifest.substringBefore("\"collections\":")
            .replace("\"backupFormatVersion\":5", "\"backupFormatVersion\":4")
            .replace("\"archiveSchemaVersion\":5", "\"archiveSchemaVersion\":4")
            .replace("\"roomSchemaVersion\":10", "\"roomSchemaVersion\":9") +
            "\"collections\":" + descriptors + "}").toByteArray()
        return write(copy)
    }

    private fun convertV3ToV2(base: Map<String, ByteArray>): File = write(v2Entries(base))

    private fun v2Entries(base: Map<String, ByteArray>): LinkedHashMap<String, ByteArray> {
        val copy = LinkedHashMap<String, ByteArray>()
        BackupContractV2.collections.values.forEach { path -> copy[path] = base.getValue(path) }
        val beePath = BackupContractV2.collections.getValue("bees")
        copy[beePath] = String(copy.getValue(beePath))
            .replace(Regex(",\"sourceObjectId\":(?:\"[^\"]*\"|null)"), "")
            .toByteArray()
        val descriptors = BackupContractV2.collections.entries.joinToString(",", "[", "]") { (name, path) ->
            val bytes = copy.getValue(path)
            val count = String(bytes).lineSequence().count(String::isNotBlank)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1," +
                "\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val manifest = String(base.getValue("manifest.json"))
        copy["manifest.json"] = (manifest.substringBefore("\"collections\":")
            .replace("\"backupFormatVersion\":5", "\"backupFormatVersion\":2")
            .replace("\"archiveSchemaVersion\":5", "\"archiveSchemaVersion\":2")
            .replace("\"roomSchemaVersion\":10", "\"roomSchemaVersion\":7") +
            "\"collections\":" + descriptors + "}").toByteArray()
        return copy
    }

    private fun convertV3ToV1(base: Map<String, ByteArray>): File {
        val v2 = v2Entries(base)
        val copy = LinkedHashMap<String, ByteArray>()
        BackupContractV1.collections.values.forEach { path -> copy[path] = v2.getValue(path) }
        val pointPath = BackupContractV1.collections.getValue("observation-points")
        copy[pointPath] = String(copy.getValue(pointPath)).replace(Regex(",\\\"description\\\":(?:\\\"[^\\\"]*\\\"|null)"), "").toByteArray()
        val descriptors = BackupContractV1.collections.entries.joinToString(",", "[", "]") { (name, path) ->
            val bytes = copy.getValue(path)
            val count = String(bytes).lineSequence().count(String::isNotBlank)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1," +
                "\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val manifest = String(v2.getValue("manifest.json"))
        val v1Manifest = manifest.substringBefore("\"collections\":")
            .replace("\"backupFormatVersion\":2", "\"backupFormatVersion\":1")
            .replace("\"archiveSchemaVersion\":2", "\"archiveSchemaVersion\":1")
            .replace("\"roomSchemaVersion\":7", "\"roomSchemaVersion\":6") + "\"collections\":" + descriptors + "}"
        copy["manifest.json"] = v1Manifest.toByteArray()
        return write(copy)
    }

    private inline fun <reified T : Throwable> assertFailure(file: File) { assertThrows(T::class.java) { service(target, targetStore).validate(file) } }
    private fun zipEntries(file: File): LinkedHashMap<String, ByteArray> { val out = linkedMapOf<String, ByteArray>(); ZipInputStream(file.inputStream()).use { z -> while (true) { val e=z.nextEntry?:break; out[e.name]=z.readBytes() } }; return out }
    private fun replaceCollection(base: Map<String, ByteArray>, name: String, text: String): File {
        val copy = LinkedHashMap(base); val bytes = text.toByteArray(); val path = BackupContractV5.collections.getValue(name); copy[path] = bytes
        val manifest = String(copy.getValue("manifest.json")); val start = manifest.indexOf("{\"name\":\"$name\"")
        check(start >= 0); val end = manifest.indexOf('}', start) + 1; val old = manifest.substring(start, end)
        val count = text.lineSequence().count(String::isNotBlank); val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val updated = old.replace(Regex("\\\"recordCount\\\":\\d+"), "\"recordCount\":$count").replace(Regex("\\\"byteLength\\\":\\d+"), "\"byteLength\":${bytes.size}").replace(Regex("\\\"sha256\\\":\\\"[0-9a-f]+\\\""), "\"sha256\":\"$hash\"")
        copy["manifest.json"] = manifest.replace(old, updated).toByteArray(); return write(copy)
    }
    private fun replaceManifestNumber(bytes: ByteArray, name: String, field: String, value: Int): ByteArray { val text=String(bytes); val regex=Regex("(\\{\\\"name\\\":\\\"${Regex.escape(name)}\\\"[^}]*?\\\"$field\\\":)\\d+"); return regex.replace(text, "${'$'}1$value").toByteArray() }
    private fun field(json: String, name: String) = Regex("\\\"$name\\\":\\\"([^\\\"]+)\\\"").find(json)!!.groupValues[1]

    private suspend fun seed(db: BeeSearchDatabase): Ids {
        val t1=UUID.randomUUID(); val t2=UUID.randomUUID(); val o1=UUID.randomUUID(); val o2=UUID.randomUUID(); val p1=UUID.randomUUID(); val p2=UUID.randomUUID(); val b1=UUID.randomUUID(); val b2=UUID.randomUUID()
        val d=db.backupDao(); d.insertTerritories(listOf(TerritoryEntity(t1,"T1","One","R","D",NOW,NOW),TerritoryEntity(t2,"T2","Two","R","D",NOW,NOW)))
        d.insertObservers(listOf(ObserverEntity(o1,"O1","Ivanov","Ivan",null,null,NOW,NOW),ObserverEntity(o2,"O2","Petrov","Petr","P", "contact",NOW,NOW)))
        d.insertObservationPoints(listOf(ObservationPointEntity(p1,t1,o1,2026,1,BeePresenceResult.NO_BEES_FOUND,null,56.1,42.7,null,null,null,NOW,null,NOW.plusSeconds(10)), ObservationPointEntity(p2,t2,o2,2026,1,BeePresenceResult.BEES_FOUND,"P2",56.2,42.8,56.19,42.79,4.5,NOW,NOW.plusSeconds(20),null,"description")))
        d.insertBees(listOf(BeeEntity(b1,p2,"WHITE",MarkPosition.THORAX,NOW),BeeEntity(b2,p2,"BLUE",MarkPosition.LEFT_WING,NOW)))
        d.insertFlightCycles(listOf(FlightCycleEntity(UUID.randomUUID(),b1,1,NOW.plusSeconds(20),NOW.plusSeconds(80),null,false,true,false,NOW.plusSeconds(20),NOW.plusSeconds(80)), FlightCycleEntity(UUID.randomUUID(),b1,2,NOW.plusSeconds(90),null,0.0,true,false,false,NOW.plusSeconds(90),NOW.plusSeconds(90)), FlightCycleEntity(UUID.randomUUID(),b2,1,NOW.plusSeconds(20),NOW.plusSeconds(70),247.5,true,true,false,NOW.plusSeconds(20),NOW.plusSeconds(70))))
        return Ids(t1,t2,o1,o2)
    }
    private data class Ids(val territory1: UUID,val territory2: UUID,val observer1: UUID,val observer2: UUID)
    private class FakeSettings(var value: PortableSettingsSnapshot, var fail: Boolean=false): PortableSettingsStore { var replaceCalls=0; override suspend fun snapshot()=value; override suspend fun replace(snapshot: PortableSettingsSnapshot){ replaceCalls++; if(fail) error("forced settings failure"); value=snapshot } }
    private suspend fun BackupDao.total() = territoryCount()+observerCount()+observationPointCount()+
        physicalObjectCount()+apiaries().size+beeCount()+flightCycleCount()
    private companion object { val NOW: Instant = Instant.parse("2026-09-10T06:00:00Z") }
}
