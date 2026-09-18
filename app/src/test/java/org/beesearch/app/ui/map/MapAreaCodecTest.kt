package org.beesearch.app.ui.map

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persisted representation of the Ареал.
 *
 * The important property is that a damaged value can never be confused with "no Ареал": earlier
 * versions turned every decode failure into an empty selection, which silently hid data loss.
 */
class MapAreaCodecTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val northern = MapGeoBounds(north = 57.111673, east = 39.026918, south = 56.562186, west = 38.470994)
    private val southern = MapGeoBounds(north = 56.920000, east = 38.910000, south = 56.810000, west = 38.720000)

    private fun area(name: String = "Лух", bounds: List<MapGeoBounds> = listOf(northern)) =
        MapArea(id = areaId, name = name, bounds = bounds)

    private fun present(result: MapAreaReadResult): MapArea {
        assertTrue("expected a readable Ареал, got $result", result is MapAreaReadResult.Present)
        return (result as MapAreaReadResult.Present).area
    }

    private fun assertCorrupt(value: String?) {
        val result = MapAreaCodec.decode(value)
        assertTrue("expected corrupt for '$value', got $result", result is MapAreaReadResult.Corrupt)
    }

    @Test
    fun `v2 round trip preserves the area`() {
        val encoded = MapAreaCodec.encode(area(bounds = listOf(northern, southern)))

        assertEquals(area(bounds = listOf(northern, southern)), present(MapAreaCodec.decode(encoded)))
        assertTrue(encoded.startsWith("v2|"))
    }

    @Test
    fun `the stable uuid survives a round trip`() {
        assertEquals(areaId, present(MapAreaCodec.decode(MapAreaCodec.encode(area()))).id)
    }

    @Test
    fun `a cyrillic name survives a round trip`() {
        assertEquals("Лух", present(MapAreaCodec.decode(MapAreaCodec.encode(area(name = "Лух")))).name)
    }

    @Test
    fun `a name with separators and quotes survives a round trip`() {
        val name = "Лух | север, \"восточный\" — 1"

        assertEquals(name, present(MapAreaCodec.decode(MapAreaCodec.encode(area(name = name)))).name)
    }

    @Test
    fun `one and several bounds round trip`() {
        assertEquals(listOf(northern), present(MapAreaCodec.decode(MapAreaCodec.encode(area()))).bounds)
        assertEquals(
            listOf(northern, southern),
            present(MapAreaCodec.decode(MapAreaCodec.encode(area(bounds = listOf(northern, southern))))).bounds,
        )
    }

    @Test
    fun `overlapping and nested bounds are stored exactly as entered`() {
        val overlapping = MapGeoBounds(north = 57.000000, east = 38.900000, south = 56.600000, west = 38.500000)
        val nested = MapGeoBounds(north = 56.900000, east = 38.800000, south = 56.700000, west = 38.600000)

        val bounds = listOf(northern, overlapping, nested, northern)

        assertEquals(bounds, present(MapAreaCodec.decode(MapAreaCodec.encode(area(bounds = bounds)))).bounds)
    }

    @Test
    fun `bounds keep their order`() {
        val bounds = listOf(southern, northern, southern)

        assertEquals(bounds, present(MapAreaCodec.decode(MapAreaCodec.encode(area(bounds = bounds)))).bounds)
    }

    @Test
    fun `v2 coordinates are preserved numerically exactly`() {
        val precise = MapGeoBounds(
            north = 57.1116729999999,
            east = 39.0269180000001,
            south = 56.5621860000002,
            west = 38.4709940000003,
        )

        assertEquals(listOf(precise), present(MapAreaCodec.decode(MapAreaCodec.encode(area(bounds = listOf(precise))))).bounds)
    }

    @Test
    fun `a padded v2 name is trimmed on read`() {
        val encoded = "v2|{\"areaId\":\"$areaId\",\"name\":\"  Лух  \",\"bounds\":[${boundJson()}]}"

        assertEquals("Лух", present(MapAreaCodec.decode(encoded)).name)
    }

    @Test
    fun `an unknown format version is corrupt, not absent`() {
        assertCorrupt("v3|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[]}")
        assertCorrupt("v2")
        assertCorrupt("")
        assertCorrupt("not a coverage value")
    }

    @Test
    fun `an invalid uuid is corrupt`() {
        assertCorrupt("v2|{\"areaId\":\"not-a-uuid\",\"name\":\"Лух\",\"bounds\":[${boundJson()}]}")
        assertCorrupt("v2|{\"areaId\":\"\",\"name\":\"Лух\",\"bounds\":[${boundJson()}]}")
    }

    @Test
    fun `a blank name is corrupt`() {
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"   \",\"bounds\":[${boundJson()}]}")
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"bounds\":[${boundJson()}]}")
    }

    @Test
    fun `an empty bounds list is corrupt`() {
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[]}")
    }

    @Test
    fun `invalid coordinates are corrupt`() {
        // north < south
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[{\"north\":1.0,\"east\":1.0,\"south\":2.0,\"west\":0.0}]}")
        // latitude out of range
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[{\"north\":91.0,\"east\":1.0,\"south\":0.0,\"west\":0.0}]}")
        // not a number
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[{\"north\":\"x\",\"east\":1.0,\"south\":0.0,\"west\":0.0}]}")
        // a missing coordinate
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[{\"north\":1.0,\"east\":1.0,\"south\":0.0}]}")
    }

    @Test
    fun `broken json is corrupt`() {
        assertCorrupt("v2|{")
        assertCorrupt("v2|[]")
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":\"nope\"}")
        assertCorrupt("v2|")
    }

    @Test
    fun `an unexpected field is corrupt so a format change must bump the version`() {
        assertCorrupt("v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[${boundJson()}],\"extra\":1}")
        assertCorrupt(
            "v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\"," +
                "\"bounds\":[{\"north\":1.0,\"east\":1.0,\"south\":0.0,\"west\":0.0,\"extra\":1}]}",
        )
    }

    @Test
    fun `the legacy v1 encoding is still readable`() {
        val legacy = "v1|57.111673,39.026918,56.562186,38.470994|56.92,38.91,56.81,38.72"

        val result = MapAreaCodec.decode(legacy)

        assertEquals(MapAreaReadResult.Legacy(listOf(northern, southern)), result)
    }

    @Test
    fun `legacy coordinates are preserved and re-encode identically`() {
        val legacy = "v1|57.111673,39.026918,56.562186,38.470994|56.92,38.91,56.81,38.72"

        val bounds = (MapAreaCodec.decode(legacy) as MapAreaReadResult.Legacy).bounds

        assertEquals(listOf(northern, southern), bounds)
        assertEquals(legacy, MapAreaCodec.encodeLegacy(bounds))
    }

    @Test
    fun `an empty legacy selection means no area`() {
        assertEquals(MapAreaReadResult.Absent, MapAreaCodec.decode("v1"))
    }

    @Test
    fun `a missing value means no area`() {
        assertEquals(MapAreaReadResult.Absent, MapAreaCodec.decode(null))
    }

    @Test
    fun `a damaged legacy value is corrupt, not an empty selection`() {
        assertCorrupt("v1|bad")
        assertCorrupt("v1|")
        assertCorrupt("v1|56,42,57,41")
        assertCorrupt("v1|91,42,56,41")
    }

    @Test
    fun `corrupt and absent are always distinguishable`() {
        assertNotEquals(MapAreaCodec.decode("v1|bad"), MapAreaCodec.decode("v1"))
        assertNotEquals(MapAreaCodec.decode("v2|{"), MapAreaCodec.decode(null))
    }

    private fun boundJson(): String =
        "{\"north\":57.111673,\"east\":39.026918,\"south\":56.562186,\"west\":38.470994}"
}
