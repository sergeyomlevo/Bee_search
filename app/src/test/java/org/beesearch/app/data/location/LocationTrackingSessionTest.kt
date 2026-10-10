package org.beesearch.app.data.location

import java.time.Instant
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.beesearch.app.domain.location.LocationReading
import org.beesearch.app.domain.location.LocationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTrackingSessionTest {
    @Test
    fun starts_off_and_recovers_when_provider_is_enabled_without_restart() = runBlocking {
        val platform = FakeLocationTrackingPlatform()
        val session = start(platform)

        assertEquals(
            LocationUiState.Unavailable("Служба местоположения отключена"),
            session.nextState(),
        )

        platform.setProviders(GPS_PROVIDER)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        platform.emit(GPS_PROVIDER, reading())

        assertEquals(LocationUiState.Available(reading()), session.nextState())
        assertEquals(1, platform.requestCount(GPS_PROVIDER))

        session.job.cancelAndJoin()
    }

    @Test
    fun turns_off_and_on_again_reconciles_the_same_session() = runBlocking {
        val platform = FakeLocationTrackingPlatform(providers = linkedSetOf(GPS_PROVIDER))
        val session = start(platform)

        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        platform.emit(GPS_PROVIDER, reading())
        assertTrue(session.nextState() is LocationUiState.Available)

        platform.setProviders()
        assertEquals(
            LocationUiState.Unavailable("Служба местоположения отключена"),
            session.nextState(),
        )
        assertEquals(2, platform.removeCount)

        platform.setProviders(GPS_PROVIDER)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        assertEquals(2, platform.requestCount(GPS_PROVIDER))

        session.job.cancelAndJoin()
    }

    @Test
    fun duplicate_provider_notifications_do_not_duplicate_requests_or_removals() = runBlocking {
        val platform = FakeLocationTrackingPlatform()
        val session = start(platform)
        assertTrue(session.nextState() is LocationUiState.Unavailable)

        platform.setProviders(GPS_PROVIDER)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        platform.notifyStateChanged()
        platform.notifyStateChanged()
        assertEquals(1, platform.requestCount(GPS_PROVIDER))

        platform.setProviders()
        assertTrue(session.nextState() is LocationUiState.Unavailable)
        platform.notifyStateChanged()
        assertEquals(3, platform.removeCount)

        platform.setProviders(GPS_PROVIDER)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        assertEquals(2, platform.requestCount(GPS_PROVIDER))

        session.job.cancelAndJoin()
    }

    @Test
    fun starts_immediately_when_location_is_already_on() = runBlocking {
        val platform = FakeLocationTrackingPlatform(providers = linkedSetOf(NETWORK_PROVIDER))
        val session = start(platform)

        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        assertEquals(1, platform.requestCount(NETWORK_PROVIDER))
        platform.emit(NETWORK_PROVIDER, reading())
        assertTrue(session.nextState() is LocationUiState.Available)

        session.job.cancelAndJoin()
    }

    @Test
    fun network_provider_can_supply_the_first_fix_without_gps() = runBlocking {
        val platform = FakeLocationTrackingPlatform(providers = linkedSetOf(NETWORK_PROVIDER))
        val session = start(platform)

        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        platform.emit(NETWORK_PROVIDER, reading())
        assertTrue(session.nextState() is LocationUiState.Available)
        assertEquals(0, platform.requestCount(GPS_PROVIDER))

        session.job.cancelAndJoin()
    }

    @Test
    fun cancellation_removes_requests_and_state_receiver() = runBlocking {
        val platform = FakeLocationTrackingPlatform(providers = linkedSetOf(GPS_PROVIDER))
        val session = start(platform)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())

        session.job.cancelAndJoin()

        assertTrue(platform.unregistered)
        assertEquals(2, platform.removeCount)
        assertFalse(platform.hasStateReceiver)
    }

    @Test
    fun missing_permission_reports_permission_required_and_does_not_register_receiver() = runBlocking {
        val platform = FakeLocationTrackingPlatform(permission = false)
        val session = start(platform)

        assertEquals(LocationUiState.PermissionRequired, session.nextState())
        session.job.join()

        assertEquals(0, platform.registerCount)
        assertEquals(0, platform.requestCount(GPS_PROVIDER))
        assertEquals(1, platform.removeCount)
    }

    @Test
    fun registration_before_initial_query_closes_the_registration_gap() = runBlocking {
        val platform = FakeLocationTrackingPlatform(
            providers = linkedSetOf(GPS_PROVIDER),
            notifyImmediatelyWhenRegistered = true,
        )
        val session = start(platform)

        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        assertEquals(1, platform.requestCount(GPS_PROVIDER))
        assertEquals(null, session.nextStateOrNull())

        session.job.cancelAndJoin()
    }

    @Test
    fun partially_registered_requests_are_removed_when_a_later_request_fails() = runBlocking {
        val platform = FakeLocationTrackingPlatform(
            providers = linkedSetOf(
                GPS_PROVIDER,
                NETWORK_PROVIDER,
            ),
            failProvider = NETWORK_PROVIDER,
        )
        val session = start(platform)

        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        val failure = withTimeout(1_000) { session.errors.receive() }
        assertEquals("request failed: network", failure.message)
        session.job.join()

        assertTrue(platform.unregistered)
        assertEquals(2, platform.removeCount)
        assertEquals(1, platform.requestCount(GPS_PROVIDER))
        assertEquals(1, platform.requestCount(NETWORK_PROVIDER))
    }

    @Test
    fun callback_queued_before_off_is_discarded_after_requests_are_reconciled() = runBlocking {
        val platform = FakeLocationTrackingPlatform(providers = linkedSetOf(GPS_PROVIDER))
        val session = start(platform)
        assertEquals(LocationUiState.WaitingForFix, session.nextState())
        val oldCallback = platform.callback(GPS_PROVIDER)

        platform.setProviders()
        assertTrue(session.nextState() is LocationUiState.Unavailable)
        oldCallback(reading())

        assertEquals(null, session.nextStateOrNull())
        session.job.cancelAndJoin()
    }

    private fun CoroutineScope.start(platform: LocationTrackingPlatform): Session {
        val states = Channel<LocationUiState>(Channel.UNLIMITED)
        val errors = Channel<Throwable>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) {
            try {
                locationTrackingUpdates(platform).collect(states::send)
            } catch (error: Throwable) {
                errors.send(error)
            }
        }
        return Session(job, states, errors)
    }

    private data class Session(
        val job: Job,
        val states: Channel<LocationUiState>,
        val errors: Channel<Throwable>,
    ) {
        suspend fun nextState(): LocationUiState = withTimeout(1_000) { states.receive() }

        suspend fun nextStateOrNull(): LocationUiState? = withTimeoutOrNull(100) { states.receive() }
    }

    private class FakeLocationTrackingPlatform(
        permission: Boolean = true,
        providers: Set<String> = emptySet(),
        private val notifyImmediatelyWhenRegistered: Boolean = false,
        private val failProvider: String? = null,
    ) : LocationTrackingPlatform {
        private var permission = permission
        private val enabled = LinkedHashSet(providers)
        private var onChange: (() -> Unit)? = null
        private val callbacks = LinkedHashMap<String, (LocationReading) -> Unit>()
        private val requests = LinkedHashMap<String, AtomicInteger>()
        var registerCount = 0
            private set
        var removeCount = 0
            private set
        var unregistered = false
            private set

        val hasStateReceiver: Boolean
            get() = onChange != null

        override fun hasPermission(): Boolean = permission

        override fun enabledProviders(): Set<String> = synchronized(enabled) { enabled.toSet() }

        override fun registerStateChanges(onChange: () -> Unit) {
            registerCount++
            this.onChange = onChange
            if (notifyImmediatelyWhenRegistered) onChange()
        }

        override fun unregisterStateChanges() {
            unregistered = true
            onChange = null
        }

        override fun requestUpdates(provider: String, onLocation: (LocationReading) -> Unit) {
            requests.getOrPut(provider) { AtomicInteger() }.incrementAndGet()
            callbacks[provider] = onLocation
            if (provider == failProvider) throw IllegalStateException("request failed: $provider")
        }

        override fun removeUpdates() {
            removeCount++
            callbacks.clear()
        }

        fun setProviders(vararg providers: String) {
            synchronized(enabled) {
                enabled.clear()
                enabled.addAll(providers)
            }
            notifyStateChanged()
        }

        fun notifyStateChanged() {
            onChange?.invoke()
        }

        fun emit(provider: String, reading: LocationReading) {
            callbacks[provider]?.invoke(reading)
        }

        fun callback(provider: String): (LocationReading) -> Unit =
            requireNotNull(callbacks[provider])

        fun requestCount(provider: String): Int = requests[provider]?.get() ?: 0
    }

    private fun reading(): LocationReading = LocationReading(
        latitude = 55.75,
        longitude = 37.61,
        accuracyMeters = 4.0,
        timestamp = Instant.ofEpochMilli(1234),
    )

    private companion object {
        const val GPS_PROVIDER = "gps"
        const val NETWORK_PROVIDER = "network"
    }
}
