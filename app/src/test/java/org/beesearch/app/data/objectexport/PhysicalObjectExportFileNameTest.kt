package org.beesearch.app.data.objectexport

import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggested file name of one object package.
 *
 * It is convenience metadata for the human choosing the file - the manifest and `object.json` are the
 * source of truth - so it only has to be readable, deterministic, unique enough, and safe to hand to
 * a file picker.
 */
class PhysicalObjectExportFileNameTest {
    @Test
    fun `hollow and log hive names name the object type and the designation`() {
        val hollow = physicalObjectExportFileName(
            type = PhysicalObjectType.HOLLOW,
            sequenceNumber = 4,
            territoryCode = "DEV-BENCH2",
            createdAt = AT,
            objectId = OBJECT_ID,
        )
        val logHive = physicalObjectExportFileName(
            type = PhysicalObjectType.LOG_HIVE,
            sequenceNumber = 2,
            territoryCode = "DEV-BENCH2",
            createdAt = AT,
            objectId = OBJECT_ID,
        )

        assertEquals("DEV-BENCH2--hollow-4--2026-09-20--11111111.zip", hollow)
        assertEquals("DEV-BENCH2--log-hive-2--2026-09-20--11111111.zip", logHive)
    }

    @Test
    fun `territory code is sanitized and a missing code degrades to a readable token`() {
        val hostile = physicalObjectExportFileName(
            type = PhysicalObjectType.HOLLOW,
            sequenceNumber = 1,
            territoryCode = "Лес/Юг:*?",
            createdAt = AT,
            objectId = OBJECT_ID,
        )
        val blank = physicalObjectExportFileName(
            type = PhysicalObjectType.HOLLOW,
            sequenceNumber = 1,
            territoryCode = "   ",
            createdAt = AT,
            objectId = OBJECT_ID,
        )

        assertTrue(hostile.startsWith("Лес-Юг--hollow-1--"))
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach { character ->
            assertFalse("file name must not contain $character", hostile.contains(character))
        }
        assertTrue(blank.startsWith("territory--hollow-1--"))
    }

    @Test
    fun `name is deterministic and distinguishes two objects of one scope`() {
        fun name(objectId: UUID) = physicalObjectExportFileName(
            PhysicalObjectType.HOLLOW, 4, "DEV", AT, objectId,
        )
        val other = UUID.fromString("99999999-9999-9999-9999-999999999999")

        assertEquals(name(OBJECT_ID), name(OBJECT_ID))
        assertFalse(name(OBJECT_ID) == name(other))
        assertTrue(name(OBJECT_ID).endsWith(".zip"))
    }

    @Test
    fun `collection names identify territory type collection and export date`() {
        assertEquals(
            "DEV--hollows--2026-09-20.zip",
            physicalObjectCollectionExportFileName(PhysicalObjectType.HOLLOW, "DEV", AT),
        )
        assertEquals(
            "DEV--log-hives--2026-09-20.zip",
            physicalObjectCollectionExportFileName(PhysicalObjectType.LOG_HIVE, "DEV", AT),
        )
        assertEquals(
            "Лес-Юг--hollows--2026-09-20.zip",
            physicalObjectCollectionExportFileName(PhysicalObjectType.HOLLOW, "Лес/Юг:*?", AT),
        )
    }

    private companion object {
        val AT: Instant = Instant.parse("2026-09-20T10:00:00Z")
        val OBJECT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
