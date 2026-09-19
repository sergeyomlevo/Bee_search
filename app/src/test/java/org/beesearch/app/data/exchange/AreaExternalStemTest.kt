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
 * The shared external stem of an Ареал.
 *
 * One stem is used twice: the Area JSON is `<stem>.json` and a generated map package for that Ареал
 * is `<stem>--map-v<N>.pmtiles`. These tests fix the stem itself and its identity with the already
 * shipped Area file name, because a change here would rename existing files on user devices.
 */
class AreaExternalStemTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val bounds = listOf(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0))

    private fun area(name: String, id: UUID = areaId) = MapArea(id = id, name = name, bounds = bounds)

    @Test
    fun `the area stem is the D083 area file name without its extension`() {
        val area = area("Лух")

        assertEquals("Лух--7e82a310", AreaExchangeFileName.externalAreaStem(area))
        assertEquals(
            "${AreaExchangeFileName.externalAreaStem(area)}${AreaExchangeFileName.EXTENSION}",
            AreaExchangeFileName.of(area),
        )
    }

    @Test
    fun `an existing D083 area file name is not changed`() {
        // Exactly the file names an already shipped version writes: they must stay byte-identical.
        assertEquals("Лух--7e82a310.json", AreaExchangeFileName.of(area("Лух")))
        assertEquals(
            "DEV Territory--e6b55f2d.json",
            AreaExchangeFileName.of(
                area("DEV Territory", id = UUID.fromString("e6b55f2d-05f0-4561-ab39-195bab7ae32c")),
            ),
        )
    }

    @Test
    fun `a cyrillic name is preserved in the stem`() {
        assertEquals("Лух--7e82a310", AreaExchangeFileName.externalAreaStem(area("Лух")))
        assertEquals("Большие Луга--7e82a310", AreaExchangeFileName.externalAreaStem(area("Большие Луга")))
    }

    @Test
    fun `unsafe characters are sanitised in the stem exactly as in the area file`() {
        val stem = AreaExchangeFileName.externalAreaStem(area("""Лу/х:се*вер"""))

        assertFalse(stem, stem.contains('/'))
        assertFalse(stem, stem.contains(':'))
        assertFalse(stem, stem.contains('*'))
        assertEquals(AreaExchangeFileName.of(area("""Лу/х:се*вер""")).removeSuffix(".json"), stem)
    }

    @Test
    fun `surrounding whitespace does not reach the stem`() {
        assertEquals("Лух--7e82a310", AreaExchangeFileName.externalAreaStem(area("  Лух  ")))
        assertEquals("Лух--7e82a310", AreaExchangeFileName.externalAreaStem(area("Лух    ")))
    }

    @Test
    fun `the short id is stable for an area`() {
        val stem = AreaExchangeFileName.externalAreaStem(area("Лух"))

        assertTrue(stem, stem.endsWith("--7e82a310"))
        assertEquals("7e82a310", AreaExchangeFileName.shortId(areaId))
        assertEquals(stem, AreaExchangeFileName.externalAreaStem(area("Лух")))
    }

    @Test
    fun `the same area always produces the same stem`() {
        val first = AreaExchangeFileName.externalAreaStem(area("Лух"))
        val second = AreaExchangeFileName.externalAreaStem(area("Лух"))

        assertEquals(first, second)
        // Rebuilding the Ареал from its own file must not invent another stem either.
        val fromFile = (AreaExchangeCodec.decode(AreaExchangeCodec.encode(area("Лух"))) as
            AreaExchangeReadResult.Present).area
        assertEquals(first, AreaExchangeFileName.externalAreaStem(fromFile))
    }

    @Test
    fun `a different uuid produces a different stem even for the same name`() {
        val other = UUID.fromString("11111111-2222-3333-4444-555555555555")

        assertNotEquals(
            AreaExchangeFileName.externalAreaStem(area("Лух")),
            AreaExchangeFileName.externalAreaStem(area("Лух", id = other)),
        )
    }

    @Test
    fun `the map package name is built from the same stem`() {
        val stem = AreaExchangeFileName.externalAreaStem(area("Лух"))

        assertEquals("Лух--7e82a310--map-v1.pmtiles", canonicalMapPackageFileName(stem, 1))
        assertEquals("Лух--7e82a310--map-v12.pmtiles", canonicalMapPackageFileName(stem, 12))
    }
}
