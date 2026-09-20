package org.beesearch.app.data.exchange

import java.util.UUID
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The managed file name a user sees in a file manager and in the share sheet.
 *
 * It has to be readable (`Лух--7e82a310.json`), deterministic (the same Ареал must not accumulate
 * versions), safe on a filesystem, and never the identity of the Ареал - that is the full `areaId`
 * inside the file.
 */
class AreaExchangeFileNameTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val bounds = listOf(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0))

    private fun area(name: String, id: UUID = areaId) = MapArea(id = id, name = name, bounds = bounds)

    @Test
    fun `cyrillic letters are preserved`() {
        assertEquals("Лух", AreaExchangeFileName.sanitizedAreaName("Лух"))
        assertEquals("Лух--7e82a310.json", AreaExchangeFileName.of(area("Лух")))
    }

    @Test
    fun `unsafe filename characters are replaced`() {
        val sanitized = AreaExchangeFileName.sanitizedAreaName("""Лу/х\се:вер*?"<>|""")

        assertFalse(sanitized, sanitized.contains('/'))
        assertFalse(sanitized, sanitized.contains('\\'))
        assertFalse(sanitized, sanitized.contains(':'))
        assertFalse(sanitized, sanitized.contains('*'))
        assertFalse(sanitized, sanitized.contains('?'))
        assertFalse(sanitized, sanitized.contains('"'))
        assertFalse(sanitized, sanitized.contains('<'))
        assertFalse(sanitized, sanitized.contains('>'))
        assertFalse(sanitized, sanitized.contains('|'))
        assertTrue(sanitized, sanitized.startsWith("Лу"))
    }

    @Test
    fun `control characters and stray dots and spaces are removed`() {
        val sanitized = AreaExchangeFileName.sanitizedAreaName("  ..Лу\u0000х\u001F.  ")

        assertEquals("Лух", sanitized)
        assertFalse(sanitized, sanitized.startsWith("."))
        assertFalse(sanitized, sanitized.endsWith("."))
        assertFalse(sanitized, sanitized.startsWith(" "))
        assertFalse(sanitized, sanitized.endsWith(" "))
    }

    @Test
    fun `repeated spaces and separators collapse`() {
        assertEquals("Лух - север", AreaExchangeFileName.sanitizedAreaName("Лух    -    север"))
        assertEquals("Лух-север", AreaExchangeFileName.sanitizedAreaName("Лух//север"))
    }

    @Test
    fun `an empty or unusable name falls back to areal`() {
        assertEquals("Ареал", AreaExchangeFileName.sanitizedAreaName(""))
        assertEquals("Ареал", AreaExchangeFileName.sanitizedAreaName("   "))
        assertEquals("Ареал", AreaExchangeFileName.sanitizedAreaName("..."))
        assertEquals("Ареал", AreaExchangeFileName.sanitizedAreaName("///"))
    }

    @Test
    fun `a long unicode name is truncated without breaking a character`() {
        // Every emoji here is a surrogate pair, so a naive substring would cut one in half.
        val longName = "🐝".repeat(200)

        val sanitized = AreaExchangeFileName.sanitizedAreaName(longName)

        assertEquals(AreaExchangeFileName.MAX_NAME_CODE_POINTS, sanitized.codePointCount(0, sanitized.length))
        assertEquals(sanitized, String(sanitized.toCharArray()))
        assertTrue(sanitized, sanitized.isNotEmpty())
    }

    @Test
    fun `a very long cyrillic name still produces a usable filename`() {
        val fileName = AreaExchangeFileName.of(area("Лух".repeat(200)))

        assertTrue(fileName, fileName.endsWith("--7e82a310.json"))
        assertTrue("file name is too long: ${fileName.length}", fileName.toByteArray(Charsets.UTF_8).size < 255)
    }

    @Test
    fun `the filename is deterministic`() {
        assertEquals(AreaExchangeFileName.of(area("Лух")), AreaExchangeFileName.of(area("Лух")))
    }

    @Test
    fun `the same area always produces the same filename`() {
        val first = AreaExchangeFileName.of(area("Лух"))
        val second = AreaExchangeFileName.of(area("Лух"))

        assertEquals(first, second)
        // Rebuilding the same Ареал from its file must not invent a second name.
        val fromFile = (AreaExchangeCodec.decode(AreaExchangeCodec.encode(area("Лух"))) as
            AreaExchangeReadResult.Present).area
        assertEquals(first, AreaExchangeFileName.of(fromFile))
    }

    @Test
    fun `a different uuid produces a different managed identity`() {
        val other = UUID.fromString("11111111-2222-3333-4444-555555555555")

        assertNotEquals(
            AreaExchangeFileName.of(area("Лух")),
            AreaExchangeFileName.of(area("Лух", id = other)),
        )
        assertFalse(
            AreaExchangeFileName.of(area("Лух")) == AreaExchangeFileName.of(area("Другое", id = other)),
        )
    }

    @Test
    fun `the json extension is always present`() {
        listOf("Лух", "", "...", "имя с / слэшем").forEach { name ->
            // An Ареал itself can never carry an empty name, so the fallback is exercised at the
            // filename level, which is where a name has to be made safe for a filesystem.
            assertTrue(name, AreaExchangeFileName.of(id = areaId, name = name).endsWith(".json"))
        }
    }
}
