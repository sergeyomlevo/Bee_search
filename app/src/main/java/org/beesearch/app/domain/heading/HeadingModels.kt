package org.beesearch.app.domain.heading

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class HeadingReference(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
)

enum class HeadingAccuracy {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    UNRELIABLE,
}

sealed interface HeadingState {
    data object Initializing : HeadingState

    data class Available(
        val trueHeadingDeg: Int,
        val accuracy: HeadingAccuracy,
        val calculatedAt: Instant,
    ) : HeadingState

    data class Unavailable(val message: String) : HeadingState
}

fun interface HeadingProvider {
    fun updates(reference: HeadingReference): Flow<HeadingState>
}
