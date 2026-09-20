package org.beesearch.app.data.exchange

import org.beesearch.app.ui.map.MapArea

/**
 * Sends the saved Ареал to whichever destination the user picks.
 *
 * The action is transport-independent on purpose. Today the only implementation is the Android
 * share sheet, and a later one can upload the same Area file to a server without changing the Area
 * file format, the canonical Ареал or the meaning of the «Отправить ареал» action in the interface.
 * Nothing here knows about a specific receiving application.
 */
internal interface AreaTransport {
    suspend fun send(area: MapArea): AreaSendResult
}

/** Outcome of one send attempt. A failure never changes the Ареал. */
internal sealed interface AreaSendResult {
    /** The Area file was handed to the system for the user's chosen application. */
    data object HandedOff : AreaSendResult

    /** Nothing was shown to the user; the Ареал stays exactly as it was. */
    data class Failed(val message: String) : AreaSendResult
}
