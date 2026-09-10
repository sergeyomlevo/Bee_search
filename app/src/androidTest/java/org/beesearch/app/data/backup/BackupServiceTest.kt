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
import org.beesearch.app.domain.backup.*
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
        assertFalse(names.any { it.contains("attachment") })
        assertFalse(zipEntries(archive).values.any { String(it).contains("source.pmtiles") || String(it).contains("/private/source/path") })
    }

    @Test fun manifestContainsAllSevenRequiredCollections() = runBlocking {
        seed(source); service(source, sourceStore).export(archive)
        val manifest = String(zipEntries(archive).getValue("manifest.json"))
        BackupContractV1.collections.forEach { (name, path) ->
            assertTrue(manifest.contains("\"name\":\"$name\"")); assertTrue(manifest.contains("\"path\":\"$path\""))
        }
        assertEquals(7, "\"collectionSchemaVersion\":1".toRegex().findAll(manifest).count())
    }

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
            val path = BackupContractV1.collections.getValue(collection); val text = String(base.getValue(path))
            assertFailure<BrokenBackupForeignKey>(replaceCollection(base, collection, text.replaceFirst(field(text.lineSequence().first(), key), UUID.randomUUID().toString())))
        }
        assertEquals(0, target.backupDao().total())
    }

    @Test fun formatEvolutionFailuresAreExplicit() = runBlocking {
        seed(source); service(source, sourceStore).export(archive); val base = zipEntries(archive)
        assertFailure<UnsupportedBackupFormat>(mutate(base) { it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"backupFormatVersion\":1", "\"backupFormatVersion\":2").toByteArray() })
        assertFailure<UnsupportedArchiveSchema>(mutate(base) { it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"archiveSchemaVersion\":1", "\"archiveSchemaVersion\":2").toByteArray() })
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
        seed(source); service(source, sourceStore).export(archive)
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
    private fun dataStore(file: File) = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private fun temp(suffix: String) = File(context.cacheDir, "${UUID.randomUUID()}-$suffix")
    private fun service(db: BeeSearchDatabase, store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) = BackupService(db, store, Clock.fixed(NOW, ZoneOffset.UTC), "test")
    private fun write(entries: Map<String, ByteArray>): File { ZipOutputStream(archive.outputStream()).use { z -> entries.forEach { (n,b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }; return archive }
    private fun mutate(base: Map<String, ByteArray>, block: (MutableMap<String, ByteArray>) -> Unit): File { val copy = LinkedHashMap(base); block(copy); return write(copy) }
    private inline fun <reified T : Throwable> assertFailure(file: File) { assertThrows(T::class.java) { service(target, targetStore).validate(file) } }
    private fun zipEntries(file: File): LinkedHashMap<String, ByteArray> { val out = linkedMapOf<String, ByteArray>(); ZipInputStream(file.inputStream()).use { z -> while (true) { val e=z.nextEntry?:break; out[e.name]=z.readBytes() } }; return out }
    private fun replaceCollection(base: Map<String, ByteArray>, name: String, text: String): File {
        val copy = LinkedHashMap(base); val bytes = text.toByteArray(); val path = BackupContractV1.collections.getValue(name); copy[path] = bytes
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
        d.insertObservationPoints(listOf(ObservationPointEntity(p1,t1,o1,2026,1,BeePresenceResult.NO_BEES_FOUND,null,56.1,42.7,null,null,null,NOW,null,NOW.plusSeconds(10)), ObservationPointEntity(p2,t2,o2,2026,1,BeePresenceResult.BEES_FOUND,"P2",56.2,42.8,56.19,42.79,4.5,NOW,NOW.plusSeconds(20),null)))
        d.insertBees(listOf(BeeEntity(b1,p2,"WHITE",MarkPosition.NONE,NOW),BeeEntity(b2,p2,"BLUE",MarkPosition.LEFT_WING,NOW)))
        d.insertFlightCycles(listOf(FlightCycleEntity(UUID.randomUUID(),b1,1,NOW.plusSeconds(20),NOW.plusSeconds(80),null,false,true,false,NOW.plusSeconds(20),NOW.plusSeconds(80)), FlightCycleEntity(UUID.randomUUID(),b1,2,NOW.plusSeconds(90),null,0.0,true,false,false,NOW.plusSeconds(90),NOW.plusSeconds(90)), FlightCycleEntity(UUID.randomUUID(),b2,1,NOW.plusSeconds(20),NOW.plusSeconds(70),247.5,true,true,false,NOW.plusSeconds(20),NOW.plusSeconds(70))))
        return Ids(t1,t2,o1,o2)
    }
    private data class Ids(val territory1: UUID,val territory2: UUID,val observer1: UUID,val observer2: UUID)
    private class FakeSettings(var value: PortableSettingsSnapshot, var fail: Boolean=false): PortableSettingsStore { var replaceCalls=0; override suspend fun snapshot()=value; override suspend fun replace(snapshot: PortableSettingsSnapshot){ replaceCalls++; if(fail) error("forced settings failure"); value=snapshot } }
    private suspend fun BackupDao.total() = territoryCount()+observerCount()+observationPointCount()+beeCount()+flightCycleCount()
    private companion object { val NOW: Instant = Instant.parse("2026-09-10T06:00:00Z") }
}
