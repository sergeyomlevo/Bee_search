package org.beesearch.app.data.backupsnapshot

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.jsonObject
import org.beesearch.app.MainActivity
import org.beesearch.app.data.backup.*
import org.beesearch.app.data.backuprepository.*
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only fixture, genuine system DocumentsProvider grant, no application binding/data use. */
@RunWith(AndroidJUnit4::class)
class IsolatedSnapshotSafTemporalTest {
    @Test fun correctedDatesSurviveSystemSafPublicationAndProductionReadBack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals(29, Build.VERSION.SDK_INT)
        assertEquals("Android SDK built for x86_64", Build.MODEL)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("rt3aIsolated"))
        val runId = UUID.randomUUID().toString()
        val relative = "Download/BeeSearch_RT3A/$runId/Backup"
        val rootId = "primary:$relative"
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", rootId)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        // Only the generated UUID fixture directory; never an existing repository.
        automation.executeShellCommand("mkdir -p /sdcard/$relative").use {
            java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { host ->
                host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }, 7313)
            }
            clickPickerNode("android:id/button1", "ALLOW ACCESS TO \"BACKUP\"")
            clickPickerNode("android:id/button1", "Allow")
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val permissionDeadline = SystemClock.uptimeMillis() + 10_000
            while (context.checkUriPermission(rootUri, android.os.Process.myPid(), android.os.Process.myUid(), flags)
                != PackageManager.PERMISSION_GRANTED && SystemClock.uptimeMillis() < permissionDeadline) {
                SystemClock.sleep(100)
            }
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkUriPermission(
                rootUri, android.os.Process.myPid(), android.os.Process.myUid(), flags))
            val store = object : RepositoryBindingStore {
                var binding: RepositoryBinding? = null
                override suspend fun read() = binding
                override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
                    assertEquals(expected, binding); binding = next
                }
            }
            val repository = BoundRepository(store, AndroidRepositoryRoots(context, "Dev", Mutex()))
            repository.initializeNew(tree.toString()).valueOrThrow()
            val database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
            val workspace = File(context.cacheDir, "rt3a-$runId").also { it.mkdirs() }
            try {
                val at = Instant.parse("2026-09-10T12:00:00Z")
                // Fixed test reference only; production legacy materialization keeps systemDefault().
                val legacyDate = legacyObservationDate(at, ZoneOffset.UTC)
                assertEquals(LocalDate.of(2026, 9, 10), legacyDate)
                val systemLegacyDate = legacyObservationDate(at)
                val canonical = LocalDate.of(2026, 9, 28)
                assertNotEquals(legacyDate, canonical)
                assertNotEquals(systemLegacyDate, canonical)
                val fixation = LocalDate.of(2026, 8, 19)
                val territory = UUID.randomUUID(); val observer = UUID.randomUUID()
                val point = UUID.randomUUID(); val hollow = UUID.randomUUID()
                val logHive = UUID.randomUUID(); val apiary = UUID.randomUUID()
                val dao = database.backupDao()
                dao.insertTerritories(listOf(TerritoryEntity(territory,"T","Fixture","R","D",at,at)))
                dao.insertObservers(listOf(ObserverEntity(observer,"O","Fixture","Observer",null,null,at,at)))
                dao.insertObservationPoints(listOf(ObservationPointEntity(canonical,point,territory,observer,
                    2026,1,BeePresenceResult.NO_BEES_FOUND,"P",56.0,42.0,null,null,null,at,null,at)))
                dao.insertObservationPointWeather(listOf(ObservationPointWeatherEntity(point,WeatherStatus.UNAVAILABLE,
                    null,null,null,null,null,null)))
                dao.insertPhysicalObjects(listOf(
                    PhysicalObjectEntity(hollow,territory,PhysicalObjectType.HOLLOW,1,56.0,42.0,at,observer,fixation),
                    PhysicalObjectEntity(logHive,territory,PhysicalObjectType.LOG_HIVE,1,56.0,42.0,at,observer,fixation),
                    PhysicalObjectEntity(apiary,territory,PhysicalObjectType.APIARY,1,56.0,42.0,at,observer,null)))
                dao.insertHollows(listOf(HollowEntity(hollow,null,null,null,null,null,null)))
                dao.insertLogHives(listOf(LogHiveEntity(logHive,null,null,null,null,null,null,null,null)))
                dao.insertApiaries(listOf(ApiaryEntity(apiary,"Fixture apiary")))
                val settings = object : PortableSettingsStore {
                    override suspend fun snapshot() = PortableSettingsSnapshot(territory,observer,emptyMap())
                    override suspend fun replace(snapshot: PortableSettingsSnapshot) = error("not a restore test")
                }
                val service = RepositorySnapshotService(repository, SnapshotCapture(database,settings),workspace)
                val committed = service.create().valueOrThrow()
                assertEquals(3, committed.snapshot.formatVersion)
                assertTrue(committed.snapshot.byteSize > 0)
                val discovered = service.discover().valueOrThrow()
                assertEquals(1, discovered.candidates.size)
                assertEquals(committed.snapshot.wholeSha256, discovered.latest!!.wholeSha256)
                assertEquals(committed.path, discovered.candidates.single().path)
                val finalUri = DocumentsContract.buildDocumentUriUsingTree(tree,"$rootId/${committed.path}")
                val readBack = File(workspace,"final-readback.zip")
                context.contentResolver.openInputStream(finalUri)!!.use { input ->
                    readBack.outputStream().use { output -> input.copyTo(output) }
                }
                val validated = SnapshotArchive().validate(readBack, store.binding!!.expectedRepositoryId,
                    "Dev", committed.snapshot.identity.snapshotId, committed.snapshot.wholeSha256)
                assertEquals(3,validated.formatVersion)
                ZipFile(readBack).use { zip ->
                    assertEquals(SnapshotContract.paths.toSet(),zip.entries().asSequence().map { it.name }.toSet())
                    assertEquals(17,zip.size())
                    val rows = SnapshotContract.paths.filter { it.startsWith("data/") && it.endsWith(".jsonl") }
                        .associateWith { path -> zip.getInputStream(zip.getEntry(path)).bufferedReader().useLines { lines ->
                            lines.filter { it.isNotBlank() }.map { SnapshotJson.parse(it.toByteArray(),false).jsonObject }.toList() } }
                    val graph = snapshotGraphFromRows(rows,version=validated.formatVersion)
                    assertEquals(point,graph.points.single().id)
                    assertEquals(territory,graph.points.single().territoryId)
                    assertEquals(observer,graph.points.single().observerId)
                    assertEquals(canonical,graph.points.single().observationDate)
                    assertNotEquals(legacyDate,graph.points.single().observationDate)
                    assertNotEquals(systemLegacyDate,graph.points.single().observationDate)
                    assertEquals(dao.physicalObjects(),graph.physicalObjects.sortedBy { it.id.toString() }.let { restored ->
                        dao.physicalObjects().map { original -> restored.single { it.id == original.id } } })
                    assertEquals(fixation,graph.physicalObjects.single { it.id == hollow }.fixationDate)
                    assertEquals(fixation,graph.physicalObjects.single { it.id == logHive }.fixationDate)
                    assertNull(graph.physicalObjects.single { it.id == apiary }.fixationDate)
                    assertEquals(hollow,graph.hollows.single().physicalObjectId)
                    assertEquals(logHive,graph.logHives.single().physicalObjectId)
                    assertEquals(apiary,graph.apiaries.single().physicalObjectId)
                }
                val stagingUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree,"$rootId/Staging")
                context.contentResolver.query(stagingUri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),null,null,null)!!.use {
                    assertEquals(0,it.count)
                }
                println("RT3A PASS tree=$tree final=$finalUri bytes=${validated.byteSize} sha=${validated.wholeSha256} " +
                    "version=${validated.formatVersion} entries=17 fixedUtcLegacyDate=$legacyDate systemLegacyDate=$systemLegacyDate " +
                    "canonical=$canonical restored=$canonical fixation=$fixation apiary=null staging=empty")
            } finally { database.close() }
        }
    }

    private fun clickPickerNode(id: String, text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalFlags = automation.serviceInfo.flags
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        val deadline = SystemClock.uptimeMillis() + 15_000
        try {
            while (SystemClock.uptimeMillis() < deadline) {
                val root = automation.rootInActiveWindow
                val node = root?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull {
                    it.isEnabled && it.text?.toString()?.equals(text, ignoreCase = true) == true
                }
                if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                SystemClock.sleep(100)
            }
            error("Picker node unavailable: $id")
        } finally {
            val restoredInfo = automation.serviceInfo
            restoredInfo.flags = originalFlags
            automation.serviceInfo = restoredInfo
        }
    }
}
