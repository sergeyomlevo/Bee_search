package org.beesearch.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the confirmed semantics of already collected field
 * data: `NONE` was a thorax mark, `RIGHT_WING` was really an abdomen mark, and
 * `LEFT_WING` has no confirmed meaning at all.
 */
class MarkPositionCompatibilityTest {
    @Test
    fun legacyNoneReadsAsThorax() {
        assertEquals(MarkPosition.THORAX, MarkPosition.fromPersistedToken("NONE"))
    }

    @Test
    fun legacyRightWingReadsAsAbdomenBecauseThatIsWhatFieldDataMeant() {
        assertEquals(MarkPosition.ABDOMEN, MarkPosition.fromPersistedToken("RIGHT_WING"))
    }

    @Test
    fun legacyLeftWingStaysItselfAndIsNotReinterpreted() {
        assertEquals(MarkPosition.LEFT_WING, MarkPosition.fromPersistedToken("LEFT_WING"))
    }

    @Test
    fun newTokensRoundTripToThemselves() {
        MarkPosition.newBeePositions.forEach { position ->
            assertEquals(position, MarkPosition.fromPersistedToken(position.name))
            assertEquals(position.name, position.persistedTokens.first())
        }
        assertEquals(MarkPosition.THORAX, MarkPosition.fromPersistedToken("THORAX"))
        assertEquals(MarkPosition.ABDOMEN, MarkPosition.fromPersistedToken("ABDOMEN"))
    }

    @Test
    fun unknownTokenIsRejectedInsteadOfGuessed() {
        assertNull(MarkPosition.fromPersistedToken("UNKNOWN"))
        assertNull(MarkPosition.fromPersistedToken(""))
        assertNull(MarkPosition.fromPersistedToken("right_wing"))
    }

    @Test
    fun everyPositionMatchesAllOfItsPersistedTokens() {
        MarkPosition.entries.forEach { position ->
            position.persistedTokens.forEach { token ->
                assertEquals(position, MarkPosition.fromPersistedToken(token))
            }
        }
        assertEquals(listOf("THORAX", "NONE"), MarkPosition.THORAX.persistedTokens)
        assertEquals(listOf("ABDOMEN", "RIGHT_WING"), MarkPosition.ABDOMEN.persistedTokens)
        assertEquals(listOf("LEFT_WING"), MarkPosition.LEFT_WING.persistedTokens)
    }
}
