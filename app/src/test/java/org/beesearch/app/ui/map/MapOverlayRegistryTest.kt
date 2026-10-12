package org.beesearch.app.ui.map

import java.io.File
import org.beesearch.app.beeSearchDevHybridMapProfile
import org.beesearch.app.beeSearchDevSentinelMapProfile
import org.beesearch.app.beeSearchFieldMapProfile
import org.beesearch.app.localVectorPmtilesStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayRegistryTest {
    @Test
    fun styleListenerWithCurrentMarkerAndStyleCorrelatesWithoutSetterCallback() {
        val registry = MapOverlayRegistry<String, String>()
        val request = registry.beginStyleRequest("profile")
        val correlation = OverlayStyleCorrelation()
        val marker = correlation.begin(request)
        val style = Any()

        assertEquals(request, correlation.correlate(marker, style, style, ready = true))
    }

    @Test
    fun oldMarkerDoesNotCorrelateAfterNewerStyleRequest() {
        val registry = MapOverlayRegistry<String, String>()
        val first = registry.beginStyleRequest("profile")
        val correlation = OverlayStyleCorrelation()
        val oldMarker = correlation.begin(first)
        val second = registry.beginStyleRequest("profile")
        val newMarker = correlation.begin(second)
        val style = Any()

        assertNotEquals(oldMarker, newMarker)
        assertNull(correlation.correlate(oldMarker, style, style, ready = true))
        assertEquals(second, correlation.correlate(newMarker, style, style, ready = true))
    }

    @Test
    fun currentStyleIdentityRejectsOldCallbackStyleEvenWhenReady() {
        val registry = MapOverlayRegistry<String, String>()
        val first = registry.beginStyleRequest("profile-a")
        val correlation = OverlayStyleCorrelation()
        val firstMarker = correlation.begin(first)
        val second = registry.beginStyleRequest("profile-b")
        correlation.begin(second)
        val oldStyle = Any()
        val currentStyle = Any()

        assertNull(correlation.correlate(firstMarker, oldStyle, currentStyle, ready = true))
        assertNull(correlation.correlate(firstMarker, currentStyle, currentStyle, ready = true))
    }

    @Test
    fun identicalProfilesStillReceiveDifferentGenerationMarkers() {
        val registry = MapOverlayRegistry<String, String>()
        val correlation = OverlayStyleCorrelation()
        val firstMarker = correlation.begin(registry.beginStyleRequest("same-profile"))
        val secondMarker = correlation.begin(registry.beginStyleRequest("same-profile"))

        assertNotEquals(firstMarker, secondMarker)
    }

    @Test
    fun readinessAndActualStyleIdentityAreRequired() {
        val registry = MapOverlayRegistry<String, String>()
        val request = registry.beginStyleRequest("profile")
        val correlation = OverlayStyleCorrelation()
        val marker = correlation.begin(request)
        val style = Any()

        assertNull(correlation.correlate(marker, style, style, ready = false))
        assertNull(correlation.correlate(marker, Any(), style, ready = true))
        assertNull(correlation.correlate(marker, style, null, ready = true))
    }

    @Test
    fun callbackRequestMustMatchWhenProvided() {
        val registry = MapOverlayRegistry<String, String>()
        val request = registry.beginStyleRequest("profile")
        val other = registry.beginStyleRequest("other")
        val correlation = OverlayStyleCorrelation()
        val marker = correlation.begin(other)
        val style = Any()

        assertNull(correlation.correlate(marker, style, style, ready = true, callbackRequest = request))
        assertEquals(other, correlation.correlate(marker, style, style, ready = true, callbackRequest = other))
    }

    @Test
    fun clearAndForeignHostMarkersCannotCorrelate() {
        val registry = MapOverlayRegistry<String, String>()
        val request = registry.beginStyleRequest("profile")
        val own = OverlayStyleCorrelation()
        val foreign = OverlayStyleCorrelation()
        val ownMarker = own.begin(request)
        val foreignMarker = foreign.begin(request)
        val style = Any()

        assertNull(own.correlate(foreignMarker, style, style, ready = true))
        own.clear()
        assertNull(own.correlate(ownMarker, style, style, ready = true))
    }

    @Test
    fun registrationBeforeReadyIsDeferredAndAttachesWhenReady() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget(ready = false)
        val request = registry.beginStyleRequest("style-a")

        assertEquals(OverlayResult.DEFERRED, registry.register(overlay("alpha")))
        assertTrue(target.sources.isEmpty())
        target.ready = true

        assertEquals(OverlayResult.APPLIED, registry.restoreIfCurrent(request, target))
        assertEquals(
            listOf(
                "source:${MapOverlayRegistry.sourceId("alpha", "source")}",
                "layer:${MapOverlayRegistry.layerId("alpha", "layer")}",
            ),
            target.events,
        )
    }

    @Test
    fun registrationAfterReadyAttachesImmediately() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        assertEquals(OverlayResult.NO_CHANGE, registry.restoreIfCurrent(request, target))

        assertEquals(OverlayResult.APPLIED, registry.register(overlay("alpha")))
        assertEquals(1, target.sources.size)
        assertEquals(1, target.layers.size)
    }

    @Test
    fun duplicateOverlayAndLocalKeysAreRejectedBeforeMutation() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        assertEquals(OverlayResult.APPLIED, registry.register(overlay("alpha")))
        assertEquals(OverlayResult.DUPLICATE_KEY, registry.register(overlay("alpha")))
        assertEquals(OverlayResult.INVALID_DECLARATION, registry.register(overlay("bad", sourceKeys = listOf("same", "same"))))
        assertEquals(OverlayResult.INVALID_DECLARATION, registry.register(overlay("bad-layer", layerKeys = listOf("same", "same"))))
        assertEquals(OverlayResult.INVALID_DECLARATION, registry.register(overlay("bad-key", sourceKeys = listOf("Bad"))))
    }

    @Test
    fun namespaceIsDeterministicAndDoesNotUseBeeMapStyleIds() {
        val source = MapOverlayRegistry.sourceId("alpha", "points")
        val layer = MapOverlayRegistry.layerId("alpha", "points")
        assertEquals(source, MapOverlayRegistry.sourceId("alpha", "points"))
        assertEquals(layer, MapOverlayRegistry.layerId("alpha", "points"))
        assertTrue(source.startsWith("beesearch-overlay-"))
        assertTrue(layer.startsWith("beesearch-overlay-"))

        val sentinel = File("build/tmp/sentinel-area-z10-13-linear-b.pmtiles")
        val vector = File("build/tmp/central-volga-260830.pmtiles")
        val styleJsons = listOf(
            beeSearchFieldMapProfile().styleJson,
            beeSearchDevSentinelMapProfile(sentinel).styleJson,
            beeSearchDevHybridMapProfile(sentinel, vector).styleJson,
            localVectorPmtilesStyle("pmtiles://file://fixture", "fixture"),
        )
        styleJsons.forEach { styleJson ->
            assertFalse(styleJson.contains("beesearch-overlay-"))
            assertFalse(styleJson.contains(source))
            assertFalse(styleJson.contains(layer))
        }
    }

    @Test
    fun foreignSourceOrLayerCollisionFailsBeforeAnyMutation() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        target.sources[MapOverlayRegistry.sourceId("alpha", "source")] = "foreign"

        assertEquals(OverlayResult.ID_COLLISION, registry.register(overlay("alpha")))
        assertTrue(target.events.isEmpty())

        val second = MapOverlayRegistry<String, String>()
        val layerTarget = FakeTarget()
        val layerRequest = second.beginStyleRequest("style-a")
        second.restoreIfCurrent(layerRequest, layerTarget)
        layerTarget.layers[MapOverlayRegistry.layerId("alpha", "layer")] = "foreign"
        assertEquals(OverlayResult.ID_COLLISION, second.register(overlay("alpha")))
        assertTrue(layerTarget.events.isEmpty())
    }

    @Test
    fun multipleOverlaysUseStableOrderAndSourcesBeforeLayers() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        registry.register(overlay("zulu", order = 2))
        registry.register(overlay("alpha", order = 1))
        assertEquals(
            listOf(
                "source:${MapOverlayRegistry.sourceId("zulu", "source")}",
                "layer:${MapOverlayRegistry.layerId("zulu", "layer")}",
                "removeLayer:${MapOverlayRegistry.layerId("zulu", "layer")}",
                "removeSource:${MapOverlayRegistry.sourceId("zulu", "source")}",
                "source:${MapOverlayRegistry.sourceId("alpha", "source")}",
                "layer:${MapOverlayRegistry.layerId("alpha", "layer")}",
                "source:${MapOverlayRegistry.sourceId("zulu", "source")}",
                "layer:${MapOverlayRegistry.layerId("zulu", "layer")}",
            ),
            target.events,
        )
    }

    @Test
    fun addingLowerOrderOverlayRebuildsAttachmentInDeterministicOrder() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("late", order = 10))
        target.events.clear()

        assertEquals(OverlayResult.APPLIED, registry.register(overlay("early", order = 1)))
        assertEquals(
            listOf(
                "removeLayer:${MapOverlayRegistry.layerId("late", "layer")}",
                "removeSource:${MapOverlayRegistry.sourceId("late", "source")}",
                "source:${MapOverlayRegistry.sourceId("early", "source")}",
                "layer:${MapOverlayRegistry.layerId("early", "layer")}",
                "source:${MapOverlayRegistry.sourceId("late", "source")}",
                "layer:${MapOverlayRegistry.layerId("late", "layer")}",
            ),
            target.events,
        )
    }

    @Test
    fun repeatedReadySignalsAreNoOpsAndReloadUsesFreshResources() {
        val registry = MapOverlayRegistry<String, String>()
        val first = FakeTarget(identity = "style-a")
        val firstRequest = registry.beginStyleRequest("request-a")
        registry.restoreIfCurrent(firstRequest, first)
        var sourceObjectsCreated = 0
        registry.register(overlay("alpha", sourceFactory = { sourceObjectsCreated++; "source-$sourceObjectsCreated" }))
        val firstAdds = first.events.size

        assertEquals(OverlayResult.NO_CHANGE, registry.restoreIfCurrent(firstRequest, first))
        assertEquals(firstAdds, first.events.size)

        val second = FakeTarget(identity = "style-b")
        val secondRequest = registry.beginStyleRequest("request-b")
        assertEquals(OverlayResult.APPLIED, registry.restoreIfCurrent(secondRequest, second))
        assertEquals(1, second.sources.size)
        assertEquals(2, sourceObjectsCreated)
    }

    @Test
    fun visibilityUpdatesAndRestoresWithoutDuplicateResources() {
        val registry = MapOverlayRegistry<String, String>()
        val first = FakeTarget(identity = "style-a")
        val request = registry.beginStyleRequest("request-a")
        registry.restoreIfCurrent(request, first)
        registry.register(overlay("alpha"))

        assertEquals(OverlayResult.APPLIED, registry.setVisible("alpha", false))
        assertFalse(first.visibility.values.single())
        assertEquals(1, first.sources.size)
        assertEquals(1, first.layers.size)

        val second = FakeTarget(identity = "style-b")
        val reload = registry.beginStyleRequest("request-b")
        registry.restoreIfCurrent(reload, second)
        assertFalse(second.visibility.values.single())
        assertEquals(1, second.sources.size)
        assertEquals(1, second.layers.size)
    }

    @Test
    fun latestRevisionWinsAndStalePreparationCannotPublish() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("alpha", revision = 1))

        val token = registry.preparationToken("alpha", 2)
        assertTrue(token != null)
        assertEquals(OverlayResult.STALE, registry.refresh(token!!, overlay("alpha", revision = 3)))
        assertEquals(OverlayResult.APPLIED, registry.refresh(token, overlay("alpha", revision = 2)))
        assertEquals("alpha-2", target.sources.values.single())
    }

    @Test
    fun staleGenerationScopeAndHostWorkAreRejected() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val first = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(first, target)
        registry.register(overlay("alpha", revision = 1))
        val token = registry.preparationToken("alpha", 2)
        val second = registry.beginStyleRequest("style-b")

        assertEquals(OverlayResult.STALE, registry.restoreIfCurrent(first, target))
        assertEquals(OverlayResult.STALE, registry.refresh(token!!, overlay("alpha", revision = 2)))
        assertEquals(OverlayResult.NO_CHANGE, registry.restoreIfCurrent(second, target))
        registry.changeScope("territory-b")
        assertEquals(OverlayResult.STALE, registry.refresh(token, overlay("alpha", revision = 2)))
    }

    @Test
    fun scopeChangeRemovesOldResourcesWithoutStyleReplacement() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("alpha"))

        assertEquals(OverlayResult.APPLIED, registry.changeScope("territory-a"))
        assertTrue(target.sources.isEmpty())
        assertTrue(target.layers.isEmpty())
        assertEquals(OverlayResult.APPLIED, registry.register(overlay("bravo")))
        assertTrue(target.sources.keys.single().contains("bravo"))
    }

    @Test
    fun unregisterRemovesLayersBeforeSourcesAndNeverForeignResources() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("alpha"))
        val foreignSource = "foreign-source"
        val foreignLayer = "foreign-layer"
        target.sources[foreignSource] = "foreign"
        target.layers[foreignLayer] = "foreign"

        assertEquals(OverlayResult.APPLIED, registry.unregister("alpha"))
        assertTrue(target.sources.containsKey(foreignSource))
        assertTrue(target.layers.containsKey(foreignLayer))
        assertEquals(
            listOf(
                "removeLayer:${MapOverlayRegistry.layerId("alpha", "layer")}",
                "removeSource:${MapOverlayRegistry.sourceId("alpha", "source")}",
            ),
            target.events.takeLast(2),
        )
    }

    @Test
    fun partialAttachFailureCleansUpAndReportsPerOverlayFailure() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget(failLayer = MapOverlayRegistry.layerId("alpha", "layer"))
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        assertEquals(OverlayResult.MUTATION_FAILED, registry.register(overlay("alpha")))
        assertTrue(target.sources.isEmpty())
        assertTrue(target.layers.isEmpty())
        assertEquals(OverlayResult.MUTATION_FAILED, registry.failures["alpha"])
    }

    @Test
    fun failedCleanupMakesAttachmentUnsafeButOtherOverlayFailureIsolated() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget(failLayer = MapOverlayRegistry.layerId("alpha", "layer"), failRemoveSource = true)
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        assertEquals(OverlayResult.SESSION_UNSAFE, registry.register(overlay("alpha")))
        assertEquals(OverlayResult.SESSION_UNSAFE, registry.register(overlay("bravo")))

        val isolated = MapOverlayRegistry<String, String>()
        val isolatedTarget = FakeTarget()
        val isolatedRequest = isolated.beginStyleRequest("style-a")
        isolated.restoreIfCurrent(isolatedRequest, isolatedTarget)
        isolatedTarget.sources[MapOverlayRegistry.sourceId("bad", "source")] = "foreign"
        assertEquals(OverlayResult.ID_COLLISION, isolated.register(overlay("bad")))
        assertEquals(OverlayResult.ID_COLLISION, isolated.register(overlay("good")))
        assertTrue(isolatedTarget.sources.containsKey(MapOverlayRegistry.sourceId("good", "source")))
    }

    @Test
    fun failedNewRequestKeepsOldOwnershipJournalForSafeCleanup() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget(identity = "style-a")
        val first = registry.beginStyleRequest("request-a")
        registry.restoreIfCurrent(first, target)
        registry.register(overlay("alpha"))

        val failed = registry.beginStyleRequest("request-b")
        assertEquals(OverlayResult.DEFERRED, registry.styleRequestFailed(failed))
        assertEquals(OverlayResult.APPLIED, registry.changeScope("territory-b"))
        assertTrue(target.sources.isEmpty())
        assertTrue(target.layers.isEmpty())
    }

    @Test
    fun retiredScopeIsCleanedWhenOldStyleBecomesReadyBeforeNewRequestSucceeds() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        registry.changeScope("scope-a")
        val requestA = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(requestA, target)
        registry.register(overlay("alpha", revision = 1, sourceFactory = { "A-data" }))

        val requestB = registry.beginStyleRequest("style-b")
        target.ready = false
        assertEquals(OverlayResult.DEFERRED, registry.changeScope("scope-b"))
        assertEquals(
            OverlayResult.DEFERRED,
            registry.register(overlay("alpha", revision = 1, sourceFactory = { "B-data" })),
        )
        assertEquals(OverlayResult.DEFERRED, registry.styleRequestFailed(requestB))

        target.ready = true
        assertEquals(OverlayResult.APPLIED, registry.cleanupRetired())
        assertTrue(target.sources.isEmpty())
        assertTrue(target.layers.isEmpty())

        val requestC = registry.beginStyleRequest("style-c")
        assertEquals(OverlayResult.APPLIED, registry.restoreIfCurrent(requestC, target))
        assertEquals("B-data", target.sources.values.single())
    }

    @Test
    fun failedFactoryDoesNotMutateTargetAndReleaseInvalidatesFutureWork() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        val broken = overlay("broken", sourceFactory = { error("factory") })
        assertEquals(OverlayResult.FACTORY_FAILED, registry.register(broken))
        assertTrue(target.events.isEmpty())

        assertEquals(OverlayResult.APPLIED, registry.release())
        assertEquals(OverlayResult.RELEASED, registry.restoreIfCurrent(request, target))
        assertEquals(OverlayResult.RELEASED, registry.register(overlay("later")))
    }

    @Test
    fun reentrantRegistryCallFromFactoryIsRefusedBeforeNestedDesiredStateChange() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        val result = registry.register(overlay("alpha", sourceFactory = {
            registry.register(overlay("nested"))
            "never-published"
        }))

        assertEquals(OverlayResult.FACTORY_FAILED, result)
        assertNull(registry.registeredRevision("nested"))
        assertEquals(1L, registry.registeredRevision("alpha"))
    }

    @Test
    fun targetMutationThatAddsThenThrowsMakesSessionUnsafeAndLeavesUnjournaledResource() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget(failAfterInsertLayer = MapOverlayRegistry.layerId("alpha", "layer"))
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)

        assertEquals(OverlayResult.SESSION_UNSAFE, registry.register(overlay("alpha")))
        assertTrue(target.layers.containsKey(MapOverlayRegistry.layerId("alpha", "layer")))
        assertEquals(1, target.sources.size)
        assertEquals(OverlayResult.SESSION_UNSAFE, registry.setVisible("alpha", false))

        val newTarget = FakeTarget(identity = "style-b")
        val newRequest = registry.beginStyleRequest("style-b")
        assertEquals(OverlayResult.APPLIED, registry.restoreIfCurrent(newRequest, newTarget))
        assertTrue(newTarget.sources.containsKey(MapOverlayRegistry.sourceId("alpha", "source")))
    }

    @Test
    fun replacingOwnedResourceWithForeignValueFailsClosedWithoutDeletingForeignResource() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("alpha"))
        val sourceId = MapOverlayRegistry.sourceId("alpha", "source")
        target.sources[sourceId] = "foreign-value"

        assertEquals(OverlayResult.SESSION_UNSAFE, registry.unregister("alpha"))
        assertEquals("foreign-value", target.sources[sourceId])
        assertEquals(OverlayResult.SESSION_UNSAFE, registry.release())
        assertTrue(registry.isReleased)
        assertEquals("foreign-value", target.sources[sourceId])
    }

    @Test
    fun outOfOrderStyleCompletionAndFailedSupersededRequestDoNotRestoreStaleStyle() {
        val registry = MapOverlayRegistry<String, String>()
        val targetA = FakeTarget(identity = "style-a")
        val targetB = FakeTarget(identity = "style-b")
        val requestA = registry.beginStyleRequest("request-a")
        val requestB = registry.beginStyleRequest("request-b")
        registry.register(overlay("alpha"))

        assertEquals(OverlayResult.STALE, registry.restoreIfCurrent(requestA, targetA))
        assertEquals(OverlayResult.DEFERRED, registry.styleRequestFailed(requestB))
        assertEquals(OverlayResult.STALE, registry.restoreIfCurrent(requestA, targetA))
        assertEquals(OverlayResult.APPLIED, registry.restoreIfCurrent(requestB, targetB))
        assertTrue(targetA.sources.isEmpty())
        assertTrue(targetB.sources.containsKey(MapOverlayRegistry.sourceId("alpha", "source")))
    }

    @Test
    fun releaseRemovesOwnedResourcesAndRepeatedReleaseIsSafe() {
        val registry = MapOverlayRegistry<String, String>()
        val target = FakeTarget()
        val request = registry.beginStyleRequest("style-a")
        registry.restoreIfCurrent(request, target)
        registry.register(overlay("alpha"))

        assertEquals(OverlayResult.APPLIED, registry.release())
        assertTrue(target.sources.isEmpty())
        assertTrue(target.layers.isEmpty())
        assertEquals(OverlayResult.NO_CHANGE, registry.release())
        assertTrue(registry.isReleased)
    }

    private fun overlay(
        key: String,
        revision: Long = 1,
        visible: Boolean = true,
        order: Int = 0,
        sourceKeys: List<String> = listOf("source"),
        layerKeys: List<String> = listOf("layer"),
        sourceFactory: ((String) -> String)? = null,
    ): MapOverlay<String, String> {
        val sources = sourceKeys.map { local ->
            OverlaySourceFactory(local) { "${key}-${revision}" }.let { factory ->
                if (sourceFactory != null && local == sourceKeys.first()) OverlaySourceFactory(local, sourceFactory)
                else factory
            }
        }
        return MapOverlay(
            key = key,
            revision = revision,
            sources = sources,
            layers = layerKeys.map { local -> OverlayLayerFactory(local, sourceKeys.first()) { _, _ -> "$key-layer-$revision" } },
            visible = visible,
            order = order,
        )
    }

    private class FakeTarget(
        var ready: Boolean = true,
        override val identity: Any = Any(),
        private val failSource: String? = null,
        private val failLayer: String? = null,
        private val failRemoveSource: Boolean = false,
        private val failAfterInsertLayer: String? = null,
    ) : OverlayMutationTarget<String, String> {
        val sources = linkedMapOf<String, String>()
        val layers = linkedMapOf<String, String>()
        val visibility = linkedMapOf<String, Boolean>()
        val events = mutableListOf<String>()

        override fun isCurrentAndReady() = ready
        override fun hasSource(id: String) = id in sources
        override fun hasLayer(id: String) = id in layers
        override fun ownsSource(id: String, source: String) = sources[id] == source
        override fun ownsLayer(id: String, layer: String) = layers[id] == layer
        override fun addSource(id: String, source: String) {
            if (id == failSource) error("source mutation")
            check(id !in sources)
            sources[id] = source
            events += "source:$id"
        }
        override fun addLayer(id: String, layer: String) {
            if (id == failLayer) error("layer mutation")
            check(id !in layers)
            layers[id] = layer
            events += "layer:$id"
            if (id == failAfterInsertLayer) error("layer mutation after insert")
        }
        override fun setVisibility(id: String, visible: Boolean) {
            visibility[id] = visible
        }
        override fun removeLayer(id: String): Boolean {
            events += "removeLayer:$id"
            return layers.remove(id) != null
        }
        override fun removeSource(id: String): Boolean {
            if (failRemoveSource) error("source cleanup")
            events += "removeSource:$id"
            return sources.remove(id) != null
        }
    }
}
