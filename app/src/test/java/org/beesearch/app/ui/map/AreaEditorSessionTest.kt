package org.beesearch.app.ui.map

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lifetime of the request that opens the участки editor.
 *
 * The editor may appear only because the user asked for it. Treating a request as still pending after
 * the map handled it is what made the panel reappear on its own: the map reloads the Ареал on every
 * visit, so an old request looked like a fresh command and ordinary navigation opened the editor.
 */
class AreaEditorSessionTest {
    private val territoryId = UUID.fromString("48ef6a6c-59d4-4405-838a-b9a40bbe32c0")

    @Test
    fun `nothing is pending before the user asks for the editor`() {
        val request = AreaEditorRequest()

        assertFalse(request.isPending)
        assertEquals(AreaEditorRequest.NO_REQUEST, request.token.value)
        // A plain entry to the map therefore cannot open the editor: no request, no editor.
        assertFalse(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true))
    }

    @Test
    fun `an explicit user action makes the request pending`() {
        val request = AreaEditorRequest()

        request.request()

        assertTrue(request.isPending)
        assertTrue(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true))
    }

    @Test
    fun `a handled request is finished and is never replayed`() {
        val request = AreaEditorRequest()
        request.request()

        request.consume()

        assertFalse(request.isPending)
        assertEquals(AreaEditorRequest.NO_REQUEST, request.token.value)
        // This is the regression: the Ареал is reloaded on every visit, and a stale request used to
        // open the editor again on a plain return to the map.
        assertFalse(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true))
    }

    @Test
    fun `handling an already handled request is harmless`() {
        val request = AreaEditorRequest()
        request.request()
        request.consume()

        request.consume()

        assertEquals(AreaEditorRequest.NO_REQUEST, request.token.value)
        assertFalse(request.isPending)
    }

    @Test
    fun `a new explicit action after a finished session opens the editor again`() {
        val request = AreaEditorRequest()
        request.request()
        request.consume()

        request.request()

        assertTrue(request.isPending)
        assertTrue(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true))
    }

    @Test
    fun `the editor waits for the territory and its stored areal`() {
        val request = AreaEditorRequest()
        request.request()

        // A request without a territory, or before the stored Ареал is loaded, must not open an editor
        // over a map that does not know what it would edit.
        assertFalse(shouldOpenAreaEditor(request.token.value, territoryId = null, areaLoaded = true))
        assertFalse(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = false))
        assertTrue(shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true))
    }

    @Test
    fun `navigation alone never satisfies the request condition`() {
        // Every navigation round-trip reaches the map with either no request at all, or with a request
        // that was already consumed by the map that handled it.
        val freshRequest = AreaEditorRequest()
        val handledRequest = AreaEditorRequest().apply {
            request()
            consume()
        }

        listOf(freshRequest, handledRequest).forEach { request ->
            assertFalse(
                "A plain return to the map must not open the editor",
                shouldOpenAreaEditor(request.token.value, territoryId, areaLoaded = true),
            )
        }
    }
}
