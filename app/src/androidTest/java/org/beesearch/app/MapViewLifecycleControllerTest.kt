package org.beesearch.app

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

@RunWith(AndroidJUnit4::class)
class MapViewLifecycleControllerTest {
    @Test
    fun normalAttachForwardsLifecycleExactlyOnce() = onMain {
        val fixture = LifecycleFixture()

        fixture.attach()
        fixture.owner.handle(Lifecycle.Event.ON_CREATE)
        fixture.owner.handle(Lifecycle.Event.ON_START)
        fixture.owner.handle(Lifecycle.Event.ON_RESUME)

        assertEquals(listOf("onCreate", "onStart", "onResume"), fixture.view.callbacks)

        fixture.destroyOwnerAndDispose()

        assertEquals(FULL_LIFECYCLE, fixture.view.callbacks)
        assertEquals(0, fixture.owner.registry.observerCount)
    }

    @Test
    fun lateAttachAtCreatedKeepsOnCreateAsSingleManualCallback() = onMain {
        val fixture = LifecycleFixture()
        fixture.owner.handle(Lifecycle.Event.ON_CREATE)

        fixture.attach()

        assertEquals(listOf("onCreate"), fixture.view.callbacks)

        fixture.owner.handle(Lifecycle.Event.ON_START)
        fixture.owner.handle(Lifecycle.Event.ON_RESUME)
        fixture.destroyOwnerAndDispose()

        assertEquals(FULL_LIFECYCLE, fixture.view.callbacks)
    }

    @Test
    fun lateAttachAtStartedCatchesUpBeforeFutureResume() = onMain {
        val fixture = LifecycleFixture()
        fixture.owner.handle(Lifecycle.Event.ON_CREATE)
        fixture.owner.handle(Lifecycle.Event.ON_START)

        fixture.attach()

        assertEquals(listOf("onCreate", "onStart"), fixture.view.callbacks)

        fixture.owner.handle(Lifecycle.Event.ON_RESUME)
        fixture.destroyOwnerAndDispose()

        assertEquals(FULL_LIFECYCLE, fixture.view.callbacks)
    }

    @Test
    fun lateAttachAtResumedCatchesUpAndCleanupRemainsExactlyOnce() = onMain {
        val fixture = LifecycleFixture()
        fixture.owner.handle(Lifecycle.Event.ON_CREATE)
        fixture.owner.handle(Lifecycle.Event.ON_START)
        fixture.owner.handle(Lifecycle.Event.ON_RESUME)

        fixture.attach()

        assertEquals(listOf("onCreate", "onStart", "onResume"), fixture.view.callbacks)

        fixture.destroyOwnerAndDispose()

        assertEquals(FULL_LIFECYCLE, fixture.view.callbacks)
        assertEquals(1, fixture.view.callbacks.count { it == "onPause" })
        assertEquals(1, fixture.view.callbacks.count { it == "onStop" })
        assertEquals(1, fixture.view.callbacks.count { it == "onDestroy" })
        assertEquals(0, fixture.owner.registry.observerCount)
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class LifecycleFixture {
        val owner = RecordingLifecycleOwner()
        val view: RecordingMapView
        private val controller = ReflectedMapViewLifecycleController()
        private var observer: LifecycleEventObserver? = null

        init {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            MapLibre.getInstance(context)
            view = RecordingMapView(context)
        }

        fun attach() {
            controller.attach(view)
            view.onCreate(null)
            observer = LifecycleEventObserver { _, event -> controller.onEvent(view, event) }
                .also(owner.registry::addObserver)
        }

        fun destroyOwnerAndDispose() {
            owner.handle(Lifecycle.Event.ON_PAUSE)
            owner.handle(Lifecycle.Event.ON_STOP)
            owner.handle(Lifecycle.Event.ON_DESTROY)
            observer?.let(owner.registry::removeObserver)
            controller.release(view)
        }
    }

    private class RecordingLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }

    private class RecordingMapView(context: Context) : MapView(context) {
        val callbacks = mutableListOf<String>()

        override fun onCreate(savedInstanceState: Bundle?) {
            callbacks += "onCreate"
        }

        override fun onStart() {
            callbacks += "onStart"
        }

        override fun onResume() {
            callbacks += "onResume"
        }

        override fun onPause() {
            callbacks += "onPause"
        }

        override fun onStop() {
            callbacks += "onStop"
        }

        override fun onDestroy() {
            callbacks += "onDestroy"
        }
    }

    private class ReflectedMapViewLifecycleController {
        private val type = Class.forName("org.beesearch.app.ui.map.MapViewLifecycleController")
        private val instance = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        private val attach = type.getDeclaredMethod("attach", MapView::class.java).apply { isAccessible = true }
        private val onEvent = type
            .getDeclaredMethod("onEvent", MapView::class.java, Lifecycle.Event::class.java)
            .apply { isAccessible = true }
        private val release = type.getDeclaredMethod("release", MapView::class.java).apply { isAccessible = true }

        fun attach(view: MapView) {
            attach.invoke(instance, view)
        }

        fun onEvent(view: MapView, event: Lifecycle.Event) {
            onEvent.invoke(instance, view, event)
        }

        fun release(view: MapView) {
            release.invoke(instance, view)
        }
    }

    private companion object {
        val FULL_LIFECYCLE = listOf(
            "onCreate",
            "onStart",
            "onResume",
            "onPause",
            "onStop",
            "onDestroy",
        )
    }
}
