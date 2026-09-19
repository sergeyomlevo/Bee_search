package org.beesearch.app.data.local.room

import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun readsThoraxAndAbdomenTokensWrittenByCompatibleDevBuilds() {
        assertEquals(MarkPosition.NONE, converters.stringToMarkPosition("THORAX"))
        assertEquals(MarkPosition.RIGHT_WING, converters.stringToMarkPosition("ABDOMEN"))
    }

    @Test
    fun currentMarkPositionTokensStillRoundTrip() {
        MarkPosition.entries.forEach { position ->
            assertEquals(
                position,
                converters.stringToMarkPosition(converters.markPositionToString(position)),
            )
        }
    }
}
