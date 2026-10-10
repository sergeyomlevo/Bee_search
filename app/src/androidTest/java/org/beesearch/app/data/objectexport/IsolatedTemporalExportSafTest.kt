package org.beesearch.app.data.objectexport

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.beesearch.app.MainActivity
import org.beesearch.app.data.local.room.*
import org.beesearch.app.data.media.*
import org.beesearch.app.data.pointexport.*
import org.beesearch.app.data.repository.*
import org.beesearch.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in emulator fixture; real system SAF, no production binding or research database use. */
@RunWith(AndroidJUnit4::class)
class IsolatedTemporalExportSafTest {
    @Test fun threeV2ExportServicesPublishCanonicalDatesThroughSystemSaf() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals(29, Build.VERSION.SDK_INT)
        assertEquals("Android SDK built for x86_64", Build.MODEL)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("rt4Isolated"))
        val runId = UUID.randomUUID().toString()
        val relative = "Download/BeeSearch_RT4/$runId/Exports"
        val rootId = "primary:$relative"
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", rootId)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/$relative").use {
            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI,rootUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            },7314) }
            clickPicker("ALLOW ACCESS TO \"EXPORTS\"")
            clickPicker("Allow")
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (context.checkUriPermission(rootUri,android.os.Process.myPid(),android.os.Process.myUid(),flags)
                != PackageManager.PERMISSION_GRANTED && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertEquals(PackageManager.PERMISSION_GRANTED,context.checkUriPermission(rootUri,
                android.os.Process.myPid(),android.os.Process.myUid(),flags))
            val database = Room.inMemoryDatabaseBuilder(context,BeeSearchDatabase::class.java).build()
            val scratch = File(context.cacheDir,"rt4-$runId").also { it.mkdirs() }
            try {
                val at = Instant.parse("2026-09-10T12:00:00Z")
                val clock = Clock.fixed(at,ZoneOffset.UTC)
                val control = legacyObservationDate(at,ZoneOffset.UTC)
                assertEquals(LocalDate.of(2026,9,10),control)
                val date = LocalDate.of(2026,9,28)
                val fixation = LocalDate.of(2026,8,19)
                val otherFixation = LocalDate.of(2026,8,20)
                val territory = UUID.randomUUID(); val observer = UUID.randomUUID(); val point = UUID.randomUUID()
                val hollow = UUID.randomUUID(); val secondHollow = UUID.randomUUID(); val unknownHollow = UUID.randomUUID()
                val logHive = UUID.randomUUID(); val unknownLogHive = UUID.randomUUID(); val apiary = UUID.randomUUID()
                val dao = database.backupDao()
                dao.insertTerritories(listOf(TerritoryEntity(territory,"T","Fixture","R","D",at,at)))
                dao.insertObservers(listOf(ObserverEntity(observer,"O","Fixture","Observer",null,null,at,at)))
                dao.insertObservationPoints(listOf(ObservationPointEntity(date,point,territory,observer,
                    2026,7,BeePresenceResult.NO_BEES_FOUND,"P",56.0,42.0,null,null,null,at,null,at)))
                dao.insertObservationPointWeather(listOf(ObservationPointWeatherEntity(point,WeatherStatus.UNAVAILABLE,
                    null,null,null,null,null,null)))
                dao.insertPhysicalObjects(listOf(
                    PhysicalObjectEntity(hollow,territory,PhysicalObjectType.HOLLOW,1,56.0,42.0,at,observer,fixation),
                    PhysicalObjectEntity(secondHollow,territory,PhysicalObjectType.HOLLOW,2,56.0,42.0,at,observer,otherFixation),
                    PhysicalObjectEntity(unknownHollow,territory,PhysicalObjectType.HOLLOW,3,56.0,42.0,at,observer,null),
                    PhysicalObjectEntity(logHive,territory,PhysicalObjectType.LOG_HIVE,1,56.0,42.0,at,observer,fixation),
                    PhysicalObjectEntity(unknownLogHive,territory,PhysicalObjectType.LOG_HIVE,2,56.0,42.0,at,observer,null),
                    PhysicalObjectEntity(apiary,territory,PhysicalObjectType.APIARY,1,56.0,42.0,at,observer,null)))
                dao.insertHollows(listOf(hollow,secondHollow,unknownHollow).map {
                    HollowEntity(it,"oak",120.0,90,50.0,30.0,"fixture") })
                dao.insertLogHives(listOf(logHive,unknownLogHive).map {
                    LogHiveEntity(it,"pine",80.0,180,40.0,"linden",28.0,90.0,"fixture") })
                dao.insertApiaries(listOf(ApiaryEntity(apiary,"unsupported export fixture")))
                val territories = RoomTerritoryRepository(database,database.territoryDao(),clock)
                val observers = RoomObserverRepository(database.observerDao(),clock)
                val objects = RoomPhysicalObjectRepository(database,database.physicalObjectDao(),
                    database.physicalObjectSequenceDao(),database.territoryDao(),database.observerDao(),database.beeDao(),clock)
                val points = RoomObservationRepository(database,database.territoryDao(),database.observationPointDao(),
                    database.observerDao(),database.beeDao(),database.flightCycleDao(),clock)
                val pointSource = RepositoryObservationPointExportSource(points)
                val singleSource = RepositoryPhysicalObjectExportSource(objects,territories,observers)
                val collectionSource = RepositoryPhysicalObjectCollectionExportSource(objects,territories,observers)
                val attachments = ObservationAttachmentFileStore(File(scratch,"attachments"),File(scratch,"attachment-cache"))
                val media = PhysicalObjectMediaFileStore(File(scratch,"media"),File(scratch,"media-cache"))
                fun destination(name: String): Uri = DocumentsContract.createDocument(context.contentResolver,
                    rootUri,"application/zip",name) ?: error("SAF create failed")
                fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.also {
                    assertTrue("Empty final artifact",it.isNotEmpty()) }
                fun entries(value: ByteArray): Map<String,ByteArray> = ZipInputStream(value.inputStream()).use { zip ->
                    buildMap { while(true) { val entry = zip.nextEntry ?: break; put(entry.name,zip.readBytes()) } } }
                fun json(value: ByteArray) = Json.parseToJsonElement(value.decodeToString()).jsonObject
                fun verifyManifest(value: ByteArray,profile: String): Map<String,ByteArray> = entries(value).also {
                    val manifest = json(it.getValue("manifest.json"))
                    assertEquals(if (profile == "SINGLE_OBSERVATION_POINT") 2 else 3,manifest.getValue("formatVersion").jsonPrimitive.int)
                    assertEquals(profile,manifest.getValue("profile").jsonPrimitive.content)
                }
                val pointUri = destination("point-v2.zip")
                SafObservationPointDocumentExporter(ObservationPointExportService(pointSource,attachments),
                    context.contentResolver).export(point,pointUri)
                val pointBytes = bytes(pointUri)
                val pointEntries = verifyManifest(pointBytes,"SINGLE_OBSERVATION_POINT")
                assertEquals(date.toString(),json(pointEntries.getValue("point.json")).getValue("point")
                    .jsonObject.getValue("observationDate").jsonPrimitive.content)
                val decodedPoint = ObservationPointExportCodec.decode(pointBytes.inputStream()).use { it.graph }
                assertEquals(points.getObservationPointDetail(point)!!.point,decodedPoint.point)
                assertEquals(date,decodedPoint.point.observationDate)
                assertNotEquals(control,decodedPoint.point.observationDate)
                assertEquals(territory,decodedPoint.territory.id); assertEquals(observer,decodedPoint.observer.id)
                println("RT4 POINT uri=$pointUri bytes=${pointBytes.size} version=2 control=$control before=$date after=${decodedPoint.point.observationDate}")
                val singleExporter = SafPhysicalObjectDocumentExporter(PhysicalObjectExportService(singleSource,media),
                    context.contentResolver,scratch)
                for (id in listOf(hollow,logHive,unknownHollow)) {
                    val original = singleSource.load(id)!!
                    val uri = destination("single-$id.zip")
                    singleExporter.export(id,uri)
                    val value = bytes(uri)
                    val wire = verifyManifest(value,"SINGLE_PHYSICAL_OBJECT")
                    val objectJson = json(wire.getValue("object.json")).getValue("object").jsonObject
                    assertTrue(objectJson.containsKey("fixationDate"))
                    assertEquals(original.fixationDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull,
                        objectJson.getValue("fixationDate"))
                    PhysicalObjectExportCodec.decode(value.inputStream()).use { decoded ->
                        assertEquals(original,decoded.graph)
                        println("RT4 SINGLE id=$id type=${decoded.graph.type} uri=$uri bytes=${value.size} version=3 before=${original.fixationDate} after=${decoded.graph.fixationDate}")
                    }
                }
                val collectionExporter = SafPhysicalObjectCollectionDocumentExporter(
                    PhysicalObjectCollectionExportService(collectionSource,media),context.contentResolver,scratch)
                for(type in listOf(PhysicalObjectType.HOLLOW,PhysicalObjectType.LOG_HIVE)) {
                    val original = collectionSource.load(territory,type)
                    assertTrue(original.objects.size > 1)
                    val uri = destination("collection-$type.zip")
                    collectionExporter.export(territory,type,uri)
                    val value = bytes(uri)
                    val wire = verifyManifest(value,"PHYSICAL_OBJECT_COLLECTION")
                    PhysicalObjectCollectionExportCodec.decode(value.inputStream()).use { decoded ->
                    assertEquals(original.objects.associateBy { it.id },decoded.graph.objects.associateBy { it.id })
                    for(obj in original.objects) {
                        val row = json(wire.getValue(PhysicalObjectCollectionExportContract.objectEntry(obj.id))).getValue("object").jsonObject
                        assertTrue(row.containsKey("fixationDate"))
                        assertEquals(obj.fixationDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull,row.getValue("fixationDate"))
                        println("RT4 COLLECTION type=$type uri=$uri id=${obj.id} version=2 before=${obj.fixationDate} after=${decoded.graph.objects.single { it.id == obj.id }.fixationDate}")
                    }
                    }
                }
                // D093: exports are one supported concrete type; Apiary is intentionally unavailable.
                assertThrows(UnsupportedPhysicalObjectExportType::class.java) { runBlocking { singleSource.load(apiary) } }
                assertThrows(UnsupportedPhysicalObjectExportType::class.java) { runBlocking { collectionSource.load(territory,PhysicalObjectType.APIARY) } }
                assertFalse(scratch.listFiles()!!.any { it.name.startsWith("bee-search-object-") || it.name.startsWith("bee-search-object-collection-") })
                println("RT4 ALL THREE PASS root=$tree Apiary=refused tempArchives=clean")
            } finally { database.close() }
        }
    }

    private fun clickPicker(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalFlags = automation.serviceInfo.flags
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        try {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while(SystemClock.uptimeMillis() < deadline) {
                val node = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button1")
                    ?.firstOrNull { it.isEnabled && it.text?.toString()?.equals(text,ignoreCase=true) == true }
                if(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                SystemClock.sleep(100)
            }
            error("Picker node unavailable: $text")
        } finally {
            val restored = automation.serviceInfo; restored.flags = originalFlags; automation.serviceInfo = restored
        }
    }
}
