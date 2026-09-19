package org.beesearch.app.ui.map

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-shot request that opens the участки editor of the Ареал.
 *
 * Opening the editor is a *command*, not a place the map can be in: the editor may appear only after
 * the user asked for it (`Создать ареал`, `Изменить участки`, or the offline-map screen's
 * `Изменить участки`). The map therefore has to distinguish "the user just asked for the editor" from
 * "the map is being shown again", and that distinction is exactly the lifetime of this request.
 *
 * A request is consumed by the map that handles it. Keeping a request pending after it was handled is
 * what made the editor reappear on its own: every return to the map reloads the Ареал, and a stale
 * request then looked like a fresh command.
 *
 * The request is deliberately *not* a second map mode: the mode itself stays where the map owns it
 * (the editor session inside the map screen), and this object only carries the pending user intent.
 */
internal class AreaEditorRequest {
    private val state = MutableStateFlow(NO_REQUEST)

    /** `0` when nothing is pending; a positive value while the user's request waits to be handled. */
    val token: StateFlow<Int> = state.asStateFlow()

    val isPending: Boolean get() = state.value != NO_REQUEST

    /** Records an explicit user request for the editor. Never called by navigation. */
    fun request() {
        state.value += 1
    }

    /** Marks the request handled. Idempotent, so a repeated call cannot corrupt the state. */
    fun consume() {
        state.value = NO_REQUEST
    }

    companion object {
        const val NO_REQUEST = 0
    }
}

/**
 * Whether the map must open the участки editor now.
 *
 * Every input has to be true: a request that is still pending, a Territory, and its stored Ареал
 * loaded. A plain return to the map satisfies none of them, so no navigation can ever open the editor
 * by itself.
 */
internal fun shouldOpenAreaEditor(
    requestToken: Int,
    territoryId: UUID?,
    areaLoaded: Boolean,
): Boolean = requestToken != AreaEditorRequest.NO_REQUEST && territoryId != null && areaLoaded
