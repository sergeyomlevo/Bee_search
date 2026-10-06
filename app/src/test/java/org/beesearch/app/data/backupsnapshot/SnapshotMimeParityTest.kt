package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backuprepository.CanonicalExtension
import org.beesearch.app.data.backuprepository.RepositoryException
import org.beesearch.app.data.backuprepository.RepositoryError
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Shared contract data; production implementations are exercised independently of PC code. */
class SnapshotMimeParityTest {
    @get:Rule val temp = TemporaryFolder()
    private val at = Instant.ofEpochMilli(0)
    private val territory = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val observer = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val owner = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private fun vectors(): JsonObject {
        val relative = "docs/test-vectors/repository-v1-mime-extensions.json"
        val file = listOf(File(relative),File("../$relative")).first { it.isFile }
        return Json.parseToJsonElement(file.readText()).jsonObject
    }
    private fun hint(value: JsonElement): String? = if (value == JsonNull) null else value.jsonPrimitive.content
    private fun graph(hints: List<String?>): Graph = Graph(
        territories = listOf(TerritoryEntity(territory,"T","Test","R","D",at,at)),
        observers = listOf(ObserverEntity(observer,"O","Test","Observer",null,null,at,at)),
        physicalObjects = listOf(PhysicalObjectEntity(owner,territory,PhysicalObjectType.APIARY,1,0.0,0.0,at,observer)),
        apiaries = listOf(ApiaryEntity(owner,null)), points = emptyList(), bees = emptyList(), cycles = emptyList(),
        objectMedia = hints.mapIndexed { index,mime -> PhysicalObjectMediaEntity(
            UUID.fromString("44444444-4444-4444-8444-${index.toString().padStart(12,'0')}"),owner,
            PhysicalObjectMediaType.IMAGE,"opaque",null,mime,10,"a".repeat(64),at) },
    )
    private fun parity(hints: List<String?>, expected: String, name: String) {
        assertEquals(expected,CanonicalExtension.resolve(hints))
        val entries = SnapshotDomainCodec.encode(graph(hints),PortableSettingsSnapshot(null,null,emptyMap()))
        if (hints.isEmpty()) assertTrue(entries.references.isEmpty())
        else assertEquals(expected,entries.references.single().canonicalExtension)
        val identity = SnapshotIdentity(UUID.randomUUID(),UUID.randomUUID(),"Dev",0)
        val file = File(temp.root,"$name.zip")
        SnapshotArchive().build(file,identity,entries)
        // Actual fixed final bytes, not a reserialized graph: reader checks reference reachability.
        SnapshotArchive().validate(file,identity.repositoryId,"Dev",identity.snapshotId)
    }
    @Test fun singleHintVectorsMatchRepositoryWriterAndReader() {
        for ((index,vector) in vectors().getValue("singleHints").jsonArray.withIndex()) {
            val row = vector.jsonObject
            parity(listOf(hint(row.getValue("hint"))),row.getValue("extension").jsonPrimitive.content,"single-$index")
        }
    }
    @Test fun sameShaAggregationVectorsMatchRepositoryWriterAndReader() {
        for ((index,vector) in vectors().getValue("merges").jsonArray.withIndex()) {
            val row = vector.jsonObject
            val hints = row.getValue("hints").jsonArray.map(::hint)
            if (row.containsKey("error")) {
                assertEquals(RepositoryError.METADATA_INCONSISTENCY,assertThrows(RepositoryException::class.java) { CanonicalExtension.resolve(hints) }.error)
                assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT,assertThrows(SnapshotException::class.java) {
                    SnapshotDomainCodec.encode(graph(hints),PortableSettingsSnapshot(null,null,emptyMap()))
                }.error)
            } else parity(hints,row.getValue("extension").jsonPrimitive.content,"merged-$index")
        }
    }
}
