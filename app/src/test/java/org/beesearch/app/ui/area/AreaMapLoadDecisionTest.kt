package org.beesearch.app.ui.area

import org.beesearch.app.data.exchange.AreaMapCandidate
import org.beesearch.app.data.exchange.AreaMapDiscoveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The step after automatic discovery.
 *
 * Whatever the search returns, the user either gets one offered package or the ordinary Android file
 * picker. In particular "nothing was found", "the pairing is unclear" and "the platform would not let
 * us look" are all ordinary outcomes that must not be turned into an error message.
 */
class AreaMapLoadDecisionTest {
    private val v1 = AreaMapCandidate(1, "Лух--7e82a310--map-v1.pmtiles", "m1.json")
    private val v2 = AreaMapCandidate(2, "Лух--7e82a310--map-v2.pmtiles", "m2.json")

    @Test
    fun `one found package is offered`() {
        val decision = areaMapLoadDecision(AreaMapDiscoveryResult.One(v1))

        assertEquals(AreaMapLoadDecision.OfferFound(v1, emptyList()), decision)
    }

    @Test
    fun `several packages offer the preferred one and keep the rest`() {
        val decision = areaMapLoadDecision(
            AreaMapDiscoveryResult.Several(preferred = v2, alternatives = listOf(v1)),
        )

        assertEquals(AreaMapLoadDecision.OfferFound(v2, listOf(v1)), decision)
    }

    @Test
    fun `nothing found opens the standard picker`() {
        assertEquals(AreaMapLoadDecision.OpenPicker, areaMapLoadDecision(AreaMapDiscoveryResult.None))
    }

    @Test
    fun `an unavailable folder opens the standard picker instead of reporting an error`() {
        val decision = areaMapLoadDecision(AreaMapDiscoveryResult.Unavailable("Система не позволила"))

        assertEquals(AreaMapLoadDecision.OpenPicker, decision)
    }

    @Test
    fun `an unclear pairing opens the standard picker and never picks a candidate`() {
        val decision = areaMapLoadDecision(AreaMapDiscoveryResult.Ambiguous("несколько описаний"))

        assertEquals(AreaMapLoadDecision.OpenPicker, decision)
    }

    @Test
    fun `a fallback decision never contains a package to activate`() {
        listOf(
            AreaMapDiscoveryResult.None,
            AreaMapDiscoveryResult.Unavailable("reason"),
            AreaMapDiscoveryResult.Ambiguous("reason"),
        ).forEach { result ->
            assertTrue(result.toString(), areaMapLoadDecision(result) is AreaMapLoadDecision.OpenPicker)
        }
    }
}
