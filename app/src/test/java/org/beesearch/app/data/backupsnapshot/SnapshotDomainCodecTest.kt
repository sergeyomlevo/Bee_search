package org.beesearch.app.data.backupsnapshot

import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDomainCodecTest {
    private val t1 = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val t2 = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val o1 = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val o2 = UUID.fromString("44444444-4444-4444-8444-444444444444")
    private val p1 = UUID.fromString("55555555-5555-4555-8555-555555555555")
    private val p2 = UUID.fromString("66666666-6666-4666-8666-666666666666")
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun graph(media: List<PhysicalObjectMediaEntity> = emptyList()) = Graph(
        listOf(TerritoryEntity(t1, "T1", "One", "R", "D", now, now), TerritoryEntity(t2, "T2", "Two", "R", "D", now, now)),
        listOf(ObserverEntity(o1, "O1", "A", "B", null, null, now, now), ObserverEntity(o2, "O2", "C", "D", null, null, now, now)),
        physicalObjects = listOf(PhysicalObjectEntity(p1, t1, PhysicalObjectType.APIARY, 1, 55.75, 37.61, now, o1), PhysicalObjectEntity(p2, t2, PhysicalObjectType.HOLLOW, 1, 55.76, 37.62, now, o2)),
        apiaries = listOf(ApiaryEntity(p1, "A")), sequences = listOf(PhysicalObjectSequenceEntity(t1, PhysicalObjectType.APIARY, 1), PhysicalObjectSequenceEntity(t2, PhysicalObjectType.HOLLOW, 1)),
        points = emptyList(), bees = emptyList(), cycles = emptyList(), objectMedia = media,
        hollows = listOf(HollowEntity(p2, null, null, null, null, null, null)),
    )
    @Test fun emptyGraphProducesAllStableEntries() {
        val e = SnapshotDomainCodec.encode(Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()), PortableSettingsSnapshot(null, null, emptyMap()))
        assertEquals(14, e.records.size); assertEquals(0, e.references.size)
    }
    @Test fun settingsForeignKeysFailClosed() {
        try { SnapshotDomainCodec.encode(Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()), PortableSettingsSnapshot(t1, null, emptyMap())); error("expected logical inconsistency") }
        catch (error: SnapshotException) { assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error) }
    }
    @Test fun nonEmptyGraphIsDeterministicAndDeduplicatesSharedMedia() {
        val sha = "a".repeat(64); val m = listOf(
            PhysicalObjectMediaEntity(UUID.randomUUID(), p1, PhysicalObjectMediaType.IMAGE, "a", "a.jpg", "image/jpeg", 10, sha, now),
            PhysicalObjectMediaEntity(UUID.randomUUID(), p2, PhysicalObjectMediaType.IMAGE, "b", "b.jpg", "application/octet-stream", 10, sha, now))
        val source = graph(m)
        val reversed = source.copy(territories = source.territories.asReversed(), observers = source.observers.asReversed(),
            physicalObjects = source.physicalObjects.asReversed(), sequences = source.sequences.asReversed(), objectMedia = m.asReversed())
        val a = SnapshotDomainCodec.encode(source, PortableSettingsSnapshot(t1, o1, emptyMap())); val b = SnapshotDomainCodec.encode(reversed, PortableSettingsSnapshot(t1, o1, emptyMap()))
        assertEquals(a.records, b.records); assertEquals(a.portable.toList(), b.portable.toList()); assertEquals(a.references, b.references); assertEquals(1, a.references.size)
        assertEquals("jpg", a.references.single().canonicalExtension)
        val root = java.nio.file.Files.createTempDirectory("snapshot-domain-roundtrip").toFile()
        try { SnapshotArchive().build(java.io.File(root, "domain.zip"), SnapshotIdentity(UUID.randomUUID(), UUID.randomUUID(), "Dev", 0), a) }
        finally { root.deleteRecursively() }
    }
    @Test fun conflictingSharedMediaMetadataFailsClosed() {
        val sha = "b".repeat(64); val m = listOf(
            PhysicalObjectMediaEntity(UUID.randomUUID(), p1, PhysicalObjectMediaType.IMAGE, "a", "a.jpg", "image/jpeg", 10, sha, now),
            PhysicalObjectMediaEntity(UUID.randomUUID(), p2, PhysicalObjectMediaType.IMAGE, "b", "b.mp4", "video/mp4", 10, sha, now))
        try { SnapshotDomainCodec.encode(graph(m), PortableSettingsSnapshot(null, null, emptyMap())); error("expected conflict") }
        catch (error: SnapshotException) { assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error) }
    }
    @Test fun duplicateSubtypeAndMediaKeysFailClosed() {
        val source = graph()
        val invalid = listOf(source.copy(hollows = source.hollows + source.hollows),
            source.copy(sequences = source.sequences + source.sequences),
            source.copy(territories = source.territories + source.territories))
        invalid.forEach { candidate ->
            val error = org.junit.Assert.assertThrows(SnapshotException::class.java) {
                SnapshotDomainCodec.encode(candidate, PortableSettingsSnapshot(null, null, emptyMap()))
            }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        }
    }
    @Test fun invalidObserverAndCoverageReferencesFailClosed() {
        for (settings in listOf(PortableSettingsSnapshot(null, UUID.randomUUID(), emptyMap()),
            PortableSettingsSnapshot(null, null, mapOf(UUID.randomUUID() to "{}")))) {
            val error = org.junit.Assert.assertThrows(SnapshotException::class.java) { SnapshotDomainCodec.encode(graph(), settings) }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        }
    }
    @Test fun sizeConflictAndDuplicateMediaIdentityFailClosed() {
        val media = PhysicalObjectMediaEntity(UUID.randomUUID(), p1, PhysicalObjectMediaType.IMAGE, "a", "a.jpg", "image/jpeg", 10, "a".repeat(64), now)
        for (rows in listOf(listOf(media, media), listOf(media, media.copy(id = UUID.randomUUID(), byteSize = 11)))) {
            val error = org.junit.Assert.assertThrows(SnapshotException::class.java) { SnapshotDomainCodec.encode(graph(rows), PortableSettingsSnapshot(null, null, emptyMap())) }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        }
    }
}
