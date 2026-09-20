package org.beesearch.app.data.exchange

import java.util.Locale
import java.util.UUID
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The portable Area file contract.
 *
 * This file leaves the device - it is mailed, sent through a messenger and later uploaded to a server
 * - so these tests fix what it promises: a versioned, complete, exact description of one Ареал and
 * nothing else. Anything unreadable must come back as an explicit invalid result, never as a partial
 * or repaired Ареал.
 */
class AreaExchangeCodecTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val northern = MapGeoBounds(
        north = 57.1116730,
        east = 39.0269180,
        south = 56.5621860,
        west = 38.4709940,
    )
    private val southern = MapGeoBounds(
        north = 56.9200000,
        east = 38.9100000,
        south = 56.8100000,
        west = 38.7200000,
    )

    private fun decode(text: String) = AreaExchangeCodec.decode(text)

    private fun decoded(text: String): MapArea =
        (decode(text) as AreaExchangeReadResult.Present).area

    private fun invalidReason(text: String): String =
        (decode(text) as AreaExchangeReadResult.Invalid).reason

    private fun payload(
        areaId: String = this.areaId.toString(),
        name: String = "Лух",
        bounds: String = """[{"north":57.111673,"east":39.026918,"south":56.562186,"west":38.470994}]""",
        version: String = "1",
    ) = """{"formatVersion":$version,"areaId":"$areaId","name":"$name","bounds":$bounds}"""

    @Test
    fun `an area survives an encode and decode round trip`() {
        val area = MapArea(id = areaId, name = "Лух", bounds = listOf(northern, southern))

        assertEquals(area, decoded(AreaExchangeCodec.encode(area)))
    }

    @Test
    fun `the full uuid is preserved and is the identity of the file`() {
        val area = MapArea(id = areaId, name = "Лух", bounds = listOf(northern))

        val text = AreaExchangeCodec.encode(area)

        assertTrue(text, text.contains(areaId.toString()))
        assertEquals(areaId, decoded(text).id)
    }

    @Test
    fun `a cyrillic name is written and read unchanged`() {
        val area = MapArea(id = areaId, name = "Лух", bounds = listOf(northern))

        assertEquals("Лух", decoded(AreaExchangeCodec.encode(area)).name)
    }

    @Test
    fun `quotes backslashes and emoji in a name survive the round trip`() {
        val name = """Лу\"х\\ \ "север" 🐝"""
        val area = MapArea(id = areaId, name = name, bounds = listOf(northern))

        assertEquals(name, decoded(AreaExchangeCodec.encode(area)).name)
    }

    @Test
    fun `a single bound is a complete area`() {
        val text = AreaExchangeCodec.encode(MapArea(id = areaId, name = "Лух", bounds = listOf(northern)))

        assertEquals(listOf(northern), decoded(text).bounds)
    }

    @Test
    fun `several bounds are all present`() {
        val text = AreaExchangeCodec.encode(
            MapArea(id = areaId, name = "Лух", bounds = listOf(northern, southern)),
        )

        assertEquals(listOf(northern, southern), decoded(text).bounds)
    }

    @Test
    fun `the stored order of bounds is preserved`() {
        val third = MapGeoBounds(north = 56.5, east = 38.5, south = 56.4, west = 38.4)
        val bounds = listOf(southern, northern, third)

        val text = AreaExchangeCodec.encode(MapArea(id = areaId, name = "Лух", bounds = bounds))

        assertEquals(bounds, decoded(text).bounds)
    }

    @Test
    fun `overlapping bounds stay separate and are not merged`() {
        val overlapping = MapGeoBounds(
            north = 57.0,
            east = 38.9,
            south = 56.5,
            west = 38.5,
        )
        val bounds = listOf(northern, overlapping)

        val text = AreaExchangeCodec.encode(MapArea(id = areaId, name = "Лух", bounds = bounds))

        assertEquals(bounds, decoded(text).bounds)
        assertEquals(2, decoded(text).bounds.size)
    }

    @Test
    fun `coordinates are written and read exactly`() {
        val awkward = MapGeoBounds(
            north = 56.199863,
            east = 42.7515419,
            south = 56.1921748,
            west = 42.7438171,
        )
        val area = MapArea(id = areaId, name = "Лух", bounds = listOf(awkward))

        val text = AreaExchangeCodec.encode(area)

        assertEquals(area, decoded(text))
        // Encoding what was decoded must be byte-identical: no rounding, no re-normalisation.
        assertEquals(text, AreaExchangeCodec.encode(decoded(text)))
    }

    @Test
    fun `an invalid uuid is rejected`() {
        assertTrue(invalidReason(payload(areaId = "7e82a310-1f4c")) .isNotEmpty())
        assertTrue(invalidReason(payload(areaId = "not-a-uuid")).isNotEmpty())
    }

    @Test
    fun `a blank name is rejected`() {
        assertTrue(invalidReason(payload(name = "   ")).isNotEmpty())
        assertTrue(invalidReason(payload(name = "")).isNotEmpty())
    }

    @Test
    fun `an empty bounds list is rejected`() {
        assertTrue(invalidReason(payload(bounds = "[]")).isNotEmpty())
    }

    @Test
    fun `an invalid coordinate is rejected`() {
        assertTrue(
            invalidReason(payload(bounds = """[{"north":91.0,"east":39.0,"south":56.0,"west":38.0}]"""))
                .isNotEmpty(),
        )
        // north below south describes no rectangle at all.
        assertTrue(
            invalidReason(payload(bounds = """[{"north":56.0,"east":39.0,"south":57.0,"west":38.0}]"""))
                .isNotEmpty(),
        )
        assertTrue(
            invalidReason(payload(bounds = """[{"north":57.0,"east":181.0,"south":56.0,"west":38.0}]"""))
                .isNotEmpty(),
        )
    }

    @Test
    fun `malformed json is rejected`() {
        assertTrue(invalidReason("{").isNotEmpty())
        assertTrue(invalidReason("null").isNotEmpty())
        assertTrue(invalidReason("").isNotEmpty())
        // A truncated file must not become a smaller Ареал.
        assertTrue(invalidReason("""{"formatVersion":1,"areaId":"$areaId","name":"Лух"}""").isNotEmpty())
        assertTrue(
            invalidReason(
                """{"formatVersion":1,"areaId":"$areaId","name":"Лух","bounds":[],"extra":1}""",
            ).isNotEmpty(),
        )
    }

    @Test
    fun `an unsupported format version is rejected`() {
        assertTrue(invalidReason(payload(version = "2")).isNotEmpty())
        assertTrue(invalidReason(payload(version = "0")).isNotEmpty())
        // A non-numeric version is a broken file, not a newer one.
        assertTrue(invalidReason(payload(version = "\"1\"")).isNotEmpty())
    }

    @Test
    fun `numbers do not depend on the device locale`() {
        val area = MapArea(id = areaId, name = "Лух", bounds = listOf(northern))
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val english = AreaExchangeCodec.encode(area)
            Locale.setDefault(Locale.GERMANY)
            val german = AreaExchangeCodec.encode(area)

            assertEquals(english, german)
            assertFalse(german, german.contains("57,111673"))
            assertEquals(area, decoded(german))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `the file carries nothing but the area itself`() {
        val text = AreaExchangeCodec.encode(MapArea(id = areaId, name = "Лух", bounds = listOf(northern)))

        // Device-local and derived data must not leak into a portable file.
        listOf("territory", "variant", "Dev", "package", "path", "areaKm", "timestamp").forEach { field ->
            assertFalse("the Area file must not contain «$field»", text.contains(field))
        }
    }
}
