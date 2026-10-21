package org.beesearch.app.ui.map

import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.beesearch.app.BeeMapProfile
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.Source

/** One MapView host. No SDK reference is retained in a ViewModel or application owner. */
internal class MapLibreOverlaySession(
    view: MapView,
    map: MapLibreMap,
    private val styleImages: List<MapStyleImage> = emptyList(),
) {
    val registry = MapOverlayRegistry<Source, Layer>()
    var onPresentationChanged: (Any?, Map<String, OverlayPresentation>) -> Unit = { _, _ -> }
    private fun notifyPresentation() = onPresentationChanged(contentScope, registry.presentation())
    private var hostView: MapView? = view
    private var hostMap: MapLibreMap? = map
    private val correlation = OverlayStyleCorrelation()
    private var requestingStyle = false
    private var supplied = emptyMap<String, MapOverlay<Source, Layer>>()
    private var contentScope: Any? = null
    private var imagesStyle: Style? = null
    private val addedImageIds = mutableSetOf<String>()
    val isReleased: Boolean get() = hostMap == null
    val currentStyle: Style? get() = hostMap?.style
    val currentGeneration: Long get() = registry.currentGeneration

    // Both completion paths require the request marker read back from the actual native Style.
    private val loadedListener = MapView.OnDidFinishLoadingStyleListener { restoreIfCurrent() }
    private val failedListener = MapView.OnDidFailLoadingMapListener { error ->
        if (!isReleased) report(registry.cleanupRetired())
        if (!isReleased && hostMap?.style?.isFullyLoaded != true) {
            // SDK failure event has no request identity. Keep the ownership journal and pending
            // request; do not falsely fail a newer request on an old event. No automatic retry.
            Log.w(TAG, "Style unavailable; restore deferred: $error")
            notifyPresentation()
        }
    }

    init {
        checkMainThread()
        view.addOnDidFinishLoadingStyleListener(loadedListener)
        view.addOnDidFailLoadingMapListener(failedListener)
    }

    fun owns(view: MapView): Boolean = hostView === view

    fun requestStyle(profile: BeeMapProfile) {
        checkMainThread()
        val map = hostMap ?: return
        check(!requestingStyle) { "Nested style request" }
        // Equality includes the actual JSON and package-derived profile fields, not a hash.
        val request = registry.beginStyleRequest(profile)
        notifyPresentation()
        val marker = correlation.begin(request)
        requestingStyle = true
        try {
            val json = JSONObject(profile.styleJson)
            val metadata = if (json.has("metadata")) json.getJSONObject("metadata") else JSONObject()
            check(!metadata.has(REQUEST_METADATA)) { "Reserved overlay request metadata collision" }
            metadata.put(REQUEST_METADATA, marker)
            json.put("metadata", metadata)
            map.setStyle(Style.Builder().fromJson(json.toString())) { style ->
                restoreIfCurrent(request, style)
            }
        } catch (failure: Exception) {
            correlation.clear()
            registry.styleRequestFailed(request)
            Log.w(TAG, "Style request failed generation=${request.generation}", failure)
        } finally {
            requestingStyle = false
        }
    }

    fun restoreIfCurrent(
        callbackRequest: OverlayStyleRequest? = null,
        callbackStyle: Style? = null,
    ): OverlayResult {
        checkMainThread()
        val map = hostMap ?: return OverlayResult.RELEASED
        if (callbackRequest == null) {
            val cleanup = registry.cleanupRetired()
            if (cleanup == OverlayResult.SESSION_UNSAFE) return cleanup
        }
        val style = callbackStyle ?: map.style ?: return OverlayResult.DEFERRED
        if (!style.isFullyLoaded || map.style !== style) return OverlayResult.DEFERRED
        // Native 13.5.1 getStyleJson returns the original loaded JSON, including metadata.
        val marker = try {
            JSONObject(style.json).optJSONObject("metadata")?.optString(REQUEST_METADATA)
        } catch (_: Exception) { null }
        val request = correlation.correlate(marker, style, map.style, style.isFullyLoaded, callbackRequest)
            ?: return OverlayResult.STALE
        ensureStyleImages(style)
        val result = registry.restoreIfCurrent(request, MapLibreTarget(map, style))
        report(result)
        notifyPresentation()
        return result
    }

    /** Feature owner supplies scope and immutable snapshots; no domain knowledge enters Registry. */
    fun sync(scope: Any?, overlays: List<MapOverlay<Source, Layer>>) {
        checkMainThread()
        if (isReleased) return
        if (contentScope != scope) {
            report(registry.changeScope(scope))
            supplied = emptyMap()
            contentScope = scope
        }
        // Reject duplicates before partially applying the input list.
        if (overlays.map { it.key }.distinct().size != overlays.size) {
            report(OverlayResult.DUPLICATE_KEY)
            return
        }
        val next = overlays.associateBy { it.key }
        supplied.keys.filter { it !in next }.forEach { report(registry.unregister(it)) }
        overlays.sortedWith(compareBy({ it.order }, { it.key })).forEach { overlay ->
            val previous = supplied[overlay.key]
            when {
                previous == null -> report(registry.register(overlay))
                overlay.revision > previous.revision -> {
                    val token = registry.preparationToken(overlay.key, overlay.revision)
                    if (token != null) report(registry.refresh(token, overlay))
                }
                overlay.revision == previous.revision -> report(registry.setVisible(overlay.key, overlay.visible))
                else -> report(OverlayResult.STALE)
            }
        }
        supplied = (supplied + next.filterValues { registry.registeredRevision(it.key) == it.revision })
            .filterKeys { it in next }
        notifyPresentation()
    }

    /**
     * Installs shared style images on the actual loaded Style before any overlay layer references
     * them. Images are not Registry-owned: the Registry intentionally journals only sources/layers.
     */
    private fun ensureStyleImages(style: Style) {
        if (imagesStyle !== style) {
            imagesStyle = style
            addedImageIds.clear()
        }
        for (image in styleImages) {
            if (!addedImageIds.add(image.id)) continue
            if (style.getImage(image.id) == null) {
                runCatching { style.addImage(image.id, image.create()) }
                    .onFailure { Log.w(TAG, "Style image ${image.id} unavailable", it) }
            }
        }
    }

    fun release() {
        checkMainThread()
        if (isReleased) return
        hostView?.removeOnDidFinishLoadingStyleListener(loadedListener)
        hostView?.removeOnDidFailLoadingMapListener(failedListener)
        report(registry.release())
        correlation.clear()
        supplied = emptyMap()
        contentScope = null
        imagesStyle = null
        addedImageIds.clear()
        onPresentationChanged = { _, _ -> }
        hostMap = null
        hostView = null
    }

    private fun report(result: OverlayResult) {
        if (result !in listOf(OverlayResult.APPLIED, OverlayResult.NO_CHANGE, OverlayResult.DEFERRED)) {
            Log.w(TAG, "Overlay operation: $result; failures=${registry.failures}")
        }
    }

    private inner class MapLibreTarget(
        private val map: MapLibreMap,
        private val style: Style,
    ) : OverlayMutationTarget<Source, Layer> {
        override val identity: Any get() = style
        override fun isCurrentAndReady() = hostMap === map && style.isFullyLoaded && map.style === style
        private fun validate() { checkMainThread(); check(isCurrentAndReady()) }
        override fun hasSource(id: String): Boolean { validate(); return style.getSource(id) != null }
        override fun hasLayer(id: String): Boolean { validate(); return style.getLayer(id) != null }
        override fun ownsSource(id: String, source: Source): Boolean { validate(); return style.getSource(id) === source }
        override fun ownsLayer(id: String, layer: Layer): Boolean { validate(); return style.getLayer(id) === layer }
        override fun addSource(id: String, source: Source) {
            validate(); check(source.id == id); style.addSource(source)
        }
        override fun addLayer(id: String, layer: Layer) {
            validate(); check(layer.id == id); style.addLayer(layer)
        }
        override fun setVisibility(id: String, visible: Boolean) {
            validate()
            checkNotNull(style.getLayer(id)).setProperties(
                PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
            )
        }
        override fun removeLayer(id: String): Boolean { validate(); return style.removeLayer(id) }
        override fun removeSource(id: String): Boolean { validate(); return style.removeSource(id) }
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
    internal companion object {
        const val TAG = "MapOverlayRegistry"
        const val REQUEST_METADATA = "beesearch:overlay-request"
    }
}
