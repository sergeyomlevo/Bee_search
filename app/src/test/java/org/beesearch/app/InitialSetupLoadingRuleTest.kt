package org.beesearch.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The startup/route rule that keeps a settings write from tearing down the visible screen.
 *
 * The route is derived from the resolved setup state, so a `Loading` flash replaces whatever the
 * user is looking at — including an open «Данные на карте» panel, which is what the owner saw as
 * "the panel closes after every change". Only a genuinely new generation may start from `Loading`.
 */
class InitialSetupLoadingRuleTest {
    @Test
    fun theFirstResolutionStartsFromLoading() {
        // Nothing has been resolved yet, so the startup destination is still being decided.
        assertTrue(shouldShowInitialSetupLoading(resolvedGeneration = null, generation = 0))
        assertTrue(shouldShowInitialSetupLoading(resolvedGeneration = null, generation = 3))
    }

    @Test
    fun reReadingAnAlreadyResolvedGenerationDoesNotFlashLoading() {
        // This is the case every «Данные на карте» change produces: a settings write re-emits the
        // facts of the same generation. The route must stay where the user is.
        assertFalse(shouldShowInitialSetupLoading(resolvedGeneration = 0, generation = 0))
        assertFalse(shouldShowInitialSetupLoading(resolvedGeneration = 7, generation = 7))
    }

    @Test
    fun aNewGenerationStartsFromLoadingAgain() {
        // A deliberate setup refresh may change the destination, so it is allowed to show Loading.
        assertTrue(shouldShowInitialSetupLoading(resolvedGeneration = 0, generation = 1))
        assertTrue(shouldShowInitialSetupLoading(resolvedGeneration = 1, generation = 0))
    }
}
