package org.beesearch.app.ui.map

/** Factories capture an immutable prepared snapshot; they never receive a mutable Style. */
internal data class OverlaySourceFactory<S : Any>(val key: String, val create: (String) -> S)
internal data class OverlayLayerFactory<L : Any>(
    val key: String,
    val sourceKey: String,
    val create: (layerId: String, sourceId: String) -> L,
)
internal data class MapOverlay<S : Any, L : Any>(
    val key: String,
    val revision: Long,
    val sources: List<OverlaySourceFactory<S>>,
    val layers: List<OverlayLayerFactory<L>>,
    val visible: Boolean = true,
    val order: Int = 0,
)

/** All calls are synchronous on the host thread. Presence is never ownership. */
internal interface OverlayMutationTarget<S : Any, L : Any> {
    val identity: Any
    fun isCurrentAndReady(): Boolean
    fun hasSource(id: String): Boolean
    fun hasLayer(id: String): Boolean
    fun ownsSource(id: String, source: S): Boolean
    fun ownsLayer(id: String, layer: L): Boolean
    fun addSource(id: String, source: S)
    fun addLayer(id: String, layer: L)
    fun setVisibility(id: String, visible: Boolean)
    fun removeLayer(id: String): Boolean
    fun removeSource(id: String): Boolean
}

internal enum class OverlayResult {
    APPLIED, NO_CHANGE, DEFERRED, STALE, INVALID_DECLARATION, DUPLICATE_KEY,
    ID_COLLISION, FACTORY_FAILED, MUTATION_FAILED, SESSION_UNSAFE, RELEASED,
}
internal data class OverlayStyleRequest(val generation: Long, val identity: Any)
internal class OverlayWorkToken internal constructor(
    internal val host: Any,
    internal val scopeEpoch: Long,
    internal val styleGeneration: Long,
    internal val key: String,
    internal val registration: Long,
    internal val revision: Long,
)

/** Runtime decisions only: no Territory, Area, persistence or SDK types. */
internal class MapOverlayRegistry<S : Any, L : Any> {
    private val ownerThread = Thread.currentThread()
    private val host = Any()
    private var active = true
    private var scope: Any? = null
    private var scopeEpoch = 0L
    private var registrationSequence = 0L
    private var generation = 0L
    private var request: OverlayStyleRequest? = null
    private var confirmed: OverlayStyleRequest? = null
    private val desired = linkedMapOf<String, Entry<S, L>>()
    // A new request does not discard this journal. Only a confirmed new Style or release does.
    private var attachment: Attachment<S, L>? = null
    private var mutating = false
    private val failureState = mutableMapOf<String, OverlayResult>()
    val failures: Map<String, OverlayResult> get() = failureState.toMap()
    val isReleased: Boolean get() = !active
    val currentGeneration: Long get() = generation

    fun beginStyleRequest(identity: Any): OverlayStyleRequest {
        checkOwner()
        check(active && !mutating) { "Inactive host or nested style switch" }
        return OverlayStyleRequest(++generation, identity).also { request = it; confirmed = null }
    }

    fun registeredRevision(key: String): Long? = desired[key]?.overlay?.revision

    fun styleRequestFailed(failed: OverlayStyleRequest): OverlayResult {
        checkOwner()
        return when {
            !active -> OverlayResult.RELEASED
            failed != request -> OverlayResult.STALE
            else -> { confirmed = null; OverlayResult.DEFERRED }
        }
    }

    /** Caller must correlate the actual SDK Style with this request, not merely a loaded event. */
    fun restoreIfCurrent(
        readyRequest: OverlayStyleRequest,
        target: OverlayMutationTarget<S, L>,
    ): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        if (readyRequest != request) return OverlayResult.STALE
        if (!target.isCurrentAndReady()) return OverlayResult.DEFERRED
        if (attachment?.target?.identity !== target.identity) attachment = Attachment(target)
        confirmed = readyRequest
        if (attachment!!.unsafe) return OverlayResult.SESSION_UNSAFE
        return controlled { reconcile() }
    }

    fun changeScope(next: Any?): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        if (scope == next) return OverlayResult.NO_CHANGE
        scope = next
        scopeEpoch++
        desired.clear()
        failureState.clear()
        return controlled {
            val old = attachment ?: return@controlled OverlayResult.APPLIED
            if (old.unsafe) return@controlled OverlayResult.SESSION_UNSAFE
            // Cleanup may belong to an earlier request but must still be the actual live Style.
            if (!old.target.isCurrentAndReady()) return@controlled OverlayResult.DEFERRED
            old.owned.keys.toList().forEach { if (!detach(old, it)) return@controlled OverlayResult.SESSION_UNSAFE }
            OverlayResult.APPLIED
        }
    }

    fun register(overlay: MapOverlay<S, L>): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        if (!valid(overlay)) return OverlayResult.INVALID_DECLARATION
        if (overlay.key in desired) return OverlayResult.DUPLICATE_KEY
        desired[overlay.key] = Entry(copySnapshot(overlay), ++registrationSequence)
        return controlled { reconcile() }
    }

    /** Token precedes preparation; publication cannot revive an old host/scope/request/revision. */
    fun preparationToken(key: String, nextRevision: Long): OverlayWorkToken? {
        checkOwner()
        val entry = desired[key] ?: return null
        if (!active || nextRevision <= entry.overlay.revision) return null
        return OverlayWorkToken(host, scopeEpoch, generation, key, entry.registration, nextRevision)
    }

    fun refresh(token: OverlayWorkToken, overlay: MapOverlay<S, L>): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        val entry = desired[token.key]
        if (token.host !== host || token.scopeEpoch != scopeEpoch || token.styleGeneration != generation ||
            entry == null || entry.registration != token.registration || overlay.key != token.key ||
            overlay.revision != token.revision || token.revision <= entry.overlay.revision
        ) return OverlayResult.STALE
        if (!valid(overlay)) return OverlayResult.INVALID_DECLARATION
        desired[overlay.key] = entry.copy(overlay = copySnapshot(overlay))
        failureState.remove(overlay.key)
        return controlled { reconcile() }
    }

    fun setVisible(key: String, visible: Boolean): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        val entry = desired[key] ?: return OverlayResult.STALE
        if (entry.overlay.visible == visible) return OverlayResult.NO_CHANGE
        desired[key] = entry.copy(overlay = entry.overlay.copy(visible = visible))
        return controlled { reconcile() }
    }

    /** Retired-scope cleanup is independent of successful completion of a newer style request. */
    fun cleanupRetired(): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        return controlled {
            val current = attachment ?: return@controlled OverlayResult.NO_CHANGE
            if (current.unsafe) return@controlled OverlayResult.SESSION_UNSAFE
            if (!current.target.isCurrentAndReady()) return@controlled OverlayResult.DEFERRED
            val retired = current.owned.filter { (key, owned) ->
                owned.scopeEpoch != scopeEpoch || key !in desired
            }.keys.toList()
            for (key in retired) if (!detach(current, key)) return@controlled OverlayResult.SESSION_UNSAFE
            if (retired.isEmpty()) OverlayResult.NO_CHANGE else OverlayResult.APPLIED
        }
    }

    fun unregister(key: String): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.RELEASED
        if (desired.remove(key) == null) return OverlayResult.NO_CHANGE
        failureState.remove(key)
        return controlled {
            val current = attachment ?: return@controlled OverlayResult.APPLIED
            if (current.unsafe) return@controlled OverlayResult.SESSION_UNSAFE
            if (!current.target.isCurrentAndReady()) return@controlled OverlayResult.DEFERRED
            if (detach(current, key)) OverlayResult.APPLIED else OverlayResult.SESSION_UNSAFE
        }
    }

    fun release(): OverlayResult {
        checkOwner()
        if (!active) return OverlayResult.NO_CHANGE
        val result = controlled {
            val current = attachment
            if (current?.unsafe == true) return@controlled OverlayResult.SESSION_UNSAFE
            if (current != null && current.target.isCurrentAndReady()) {
                current.owned.keys.toList().forEach { if (!detach(current, it)) return@controlled OverlayResult.SESSION_UNSAFE }
            }
            OverlayResult.APPLIED
        }
        active = false
        generation++
        scopeEpoch++
        attachment = null
        confirmed = null
        request = null
        desired.clear()
        failureState.clear()
        return result
    }

    private fun reconcile(): OverlayResult {
        val current = attachment ?: return OverlayResult.DEFERRED
        if (current.unsafe) return OverlayResult.SESSION_UNSAFE
        if (confirmed != request || !current.target.isCurrentAndReady()) return OverlayResult.DEFERRED
        // Pending unregister/scope changes are completed before any new attach.
        current.owned.filter { (key, owned) -> key !in desired || owned.scopeEpoch != scopeEpoch }.keys.toList().forEach {
            if (!detach(current, it)) return OverlayResult.SESSION_UNSAFE
        }
        val ordered = desired.values.sortedWith(compareBy({ it.overlay.order }, { it.overlay.key }))
        val layout = ordered.map { Triple(it.overlay.key, it.overlay.revision, it.overlay.order) }
        if (current.layout != layout) {
            // Rebuild only when geometry/order membership changes, so append-only SDK adds still
            // produce the same total ordering even when a lower-order overlay arrives later.
            current.owned.keys.toList().asReversed().forEach {
                if (!detach(current, it)) return OverlayResult.SESSION_UNSAFE
            }
            current.layout = layout
        }
        var result = OverlayResult.NO_CHANGE
        for (entry in ordered) {
            val overlay = entry.overlay
            var owned = current.owned[overlay.key]
            if (owned != null && owned.revision != overlay.revision) {
                if (!detach(current, overlay.key)) return OverlayResult.SESSION_UNSAFE
                owned = null
            }
            if (owned == null) {
                val attached = attach(current, overlay)
                if (attached == OverlayResult.SESSION_UNSAFE) return attached
                if (attached != OverlayResult.APPLIED) failureState[overlay.key] = attached
                else failureState.remove(overlay.key)
                if (result in listOf(OverlayResult.NO_CHANGE, OverlayResult.APPLIED)) result = attached
            } else if (owned.visible != overlay.visible) {
                try {
                    owned.layers.forEach { (id, layer) ->
                        if (!current.target.ownsLayer(id, layer)) { current.unsafe = true; return OverlayResult.SESSION_UNSAFE }
                        current.target.setVisibility(id, overlay.visible)
                    }
                    owned.visible = overlay.visible
                    if (result == OverlayResult.NO_CHANGE) result = OverlayResult.APPLIED
                } catch (_: Exception) {
                    failureState[overlay.key] = OverlayResult.MUTATION_FAILED
                    if (!detach(current, overlay.key)) return OverlayResult.SESSION_UNSAFE
                    result = OverlayResult.MUTATION_FAILED
                }
            }
        }
        return result
    }

    private fun attach(current: Attachment<S, L>, overlay: MapOverlay<S, L>): OverlayResult {
        val target = current.target
        val sourceIds = overlay.sources.map { sourceId(overlay.key, it.key) }
        val layerIds = overlay.layers.map { layerId(overlay.key, it.key) }
        if (sourceIds.any(target::hasSource) || layerIds.any(target::hasLayer)) return OverlayResult.ID_COLLISION
        val sources: List<S>
        val layers: List<L>
        try {
            sources = overlay.sources.mapIndexed { i, factory -> factory.create(sourceIds[i]) }
            layers = overlay.layers.mapIndexed { i, factory ->
                factory.create(layerIds[i], sourceId(overlay.key, factory.sourceKey))
            }
        } catch (_: Exception) { return OverlayResult.FACTORY_FAILED }
        val owned = Owned<S, L>(overlay.revision, overlay.visible, scopeEpoch)
        current.owned[overlay.key] = owned
        try {
            sources.forEachIndexed { i, source -> target.addSource(sourceIds[i], source); owned.sources[sourceIds[i]] = source }
            layers.forEachIndexed { i, layer ->
                target.addLayer(layerIds[i], layer)
                owned.layers[layerIds[i]] = layer
                target.setVisibility(layerIds[i], overlay.visible)
            }
        } catch (_: Exception) {
            // A throw after an unjournaled native change cannot establish ownership safely.
            val unknownMutation = try {
                !target.isCurrentAndReady() ||
                    sourceIds.any { it !in owned.sources && target.hasSource(it) } ||
                    layerIds.any { it !in owned.layers && target.hasLayer(it) }
            } catch (_: Exception) { true }
            if (unknownMutation) { current.unsafe = true; return OverlayResult.SESSION_UNSAFE }
            return if (detach(current, overlay.key)) OverlayResult.MUTATION_FAILED else OverlayResult.SESSION_UNSAFE
        }
        return OverlayResult.APPLIED
    }

    private fun detach(current: Attachment<S, L>, key: String): Boolean {
        val owned = current.owned[key] ?: return true
        try {
            if (!current.target.isCurrentAndReady()) { current.unsafe = true; return false }
            for ((id, layer) in owned.layers.toList().asReversed()) {
                if (!current.target.ownsLayer(id, layer)) { current.unsafe = true; return false }
                if (!current.target.removeLayer(id)) { current.unsafe = true; return false }
                owned.layers.remove(id)
            }
            for ((id, source) in owned.sources.toList().asReversed()) {
                if (!current.target.ownsSource(id, source)) { current.unsafe = true; return false }
                if (!current.target.removeSource(id)) { current.unsafe = true; return false }
                owned.sources.remove(id)
            }
            current.owned.remove(key)
            return true
        } catch (_: Exception) { current.unsafe = true; return false }
    }

    private fun checkOwner() {
        check(Thread.currentThread() === ownerThread && !mutating) { "Wrong thread or reentrant Registry call" }
    }

    private inline fun controlled(block: () -> OverlayResult): OverlayResult {
        check(!mutating) { "Reentrant overlay mutation" }
        mutating = true
        return try { block() } finally { mutating = false }
    }

    private fun valid(overlay: MapOverlay<S, L>): Boolean = validKey(overlay.key) && overlay.revision >= 0 &&
        overlay.sources.isNotEmpty() && overlay.layers.isNotEmpty() &&
        overlay.sources.all { validKey(it.key) } && overlay.layers.all { validKey(it.key) } &&
        overlay.sources.map { it.key }.distinct().size == overlay.sources.size &&
        overlay.layers.map { it.key }.distinct().size == overlay.layers.size &&
        overlay.layers.all { layer -> overlay.sources.any { it.key == layer.sourceKey } }

    private fun copySnapshot(overlay: MapOverlay<S, L>) = overlay.copy(
        sources = overlay.sources.toList(), layers = overlay.layers.toList(),
    )
    private data class Entry<S : Any, L : Any>(val overlay: MapOverlay<S, L>, val registration: Long)
    private class Attachment<S : Any, L : Any>(val target: OverlayMutationTarget<S, L>) {
        val owned = linkedMapOf<String, Owned<S, L>>()
        var unsafe = false
        var layout = emptyList<Triple<String, Long, Int>>()
    }
    private class Owned<S : Any, L : Any>(val revision: Long, var visible: Boolean, val scopeEpoch: Long) {
        val sources = linkedMapOf<String, S>()
        val layers = linkedMapOf<String, L>()
    }

    companion object {
        // Length-prefixed keys avoid ambiguities when keys themselves contain separators.
        fun sourceId(overlay: String, local: String) = "beesearch-overlay-${overlay.length}-$overlay-source-$local"
        fun layerId(overlay: String, local: String) = "beesearch-overlay-${overlay.length}-$overlay-layer-$local"
        private fun validKey(key: String) = key.length in 1..64 && key.all {
            it in 'a'..'z' || it in '0'..'9' || it == '-'
        }
    }
}

/** App request proof, carried by non-rendering Style metadata, not an invented SDK generation. */
internal class OverlayStyleCorrelation {
    private val hostId = java.util.UUID.randomUUID().toString()
    private var current: Pair<OverlayStyleRequest, String>? = null
    fun begin(request: OverlayStyleRequest): String = "$hostId:${request.generation}".also {
        current = request to it
    }
    fun correlate(
        marker: String?,
        actualStyle: Any,
        currentStyle: Any?,
        ready: Boolean,
        callbackRequest: OverlayStyleRequest? = null,
    ): OverlayStyleRequest? {
        val (request, expectedMarker) = current ?: return null
        return request.takeIf {
            ready && actualStyle === currentStyle && marker == expectedMarker &&
                (callbackRequest == null || callbackRequest == request)
        }
    }
    fun clear() { current = null }
}
