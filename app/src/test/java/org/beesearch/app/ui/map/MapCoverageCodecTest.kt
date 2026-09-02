package org.beesearch.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCoverageCodecTest {
    private val first = MapCoverageFragment(MapGeoBounds(10.0, 20.0, 0.0, 0.0))
    private val second = MapCoverageFragment(MapGeoBounds(11.0, 21.0, 1.0, 1.0))

    @Test fun `round trip preserves order and overlap as separate fragments`() {
        assertEquals(listOf(first, second), MapCoverageCodec.decode(MapCoverageCodec.encode(listOf(first, second))))
    }

    @Test fun `corrupt data is treated as empty`() {
        assertEquals(emptyList<MapCoverageFragment>(), MapCoverageCodec.decode("v1|bad"))
        assertEquals(emptyList<MapCoverageFragment>(), MapCoverageCodec.decode("v2|1,2,3,4"))
    }
}
