package org.beesearch.app.data.heading

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.heading.roundedHeadingDegrees
import org.beesearch.app.domain.heading.trueHeadingDegrees
import java.time.Clock

internal class AndroidHeadingProvider(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : HeadingProvider {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val windowManager = appContext.getSystemService(WindowManager::class.java)

    override fun updates(reference: HeadingReference): Flow<HeadingState> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensor == null) {
            trySend(HeadingState.Unavailable("Датчик направления недоступен"))
            awaitClose { }
            return@callbackFlow
        }

        var accuracy = HeadingAccuracy.UNKNOWN
        var lastReading: HeadingState.Available? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val displayAdjusted = rotationMatrixForDisplay(
                    rotationMatrix = rotationMatrix,
                    displayRotation = currentDisplayRotation(),
                ) ?: return
                val orientation = FloatArray(3)
                SensorManager.getOrientation(displayAdjusted, orientation)

                val calculatedAt = clock.instant()
                val magneticHeading = Math.toDegrees(orientation[0].toDouble())
                val declination = GeomagneticField(
                    reference.latitude.toFloat(),
                    reference.longitude.toFloat(),
                    reference.altitudeMeters.toFloat(),
                    calculatedAt.toEpochMilli(),
                ).declination.toDouble()
                val reading = HeadingState.Available(
                    trueHeadingDeg = roundedHeadingDegrees(
                        trueHeadingDegrees(magneticHeading, declination),
                    ),
                    accuracy = accuracy,
                    calculatedAt = calculatedAt,
                )
                lastReading = reading
                trySend(reading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
                accuracy = value.toHeadingAccuracy()
                lastReading?.let { reading ->
                    val updated = reading.copy(accuracy = accuracy)
                    lastReading = updated
                    trySend(updated)
                }
            }
        }

        if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) {
            trySend(HeadingState.Unavailable("Не удалось включить датчик направления"))
            awaitClose { }
            return@callbackFlow
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
        .buffer(Channel.CONFLATED)
        .distinctUntilChangedBy { state ->
            when (state) {
                HeadingState.Initializing -> "initializing"
                is HeadingState.Unavailable -> "unavailable:${state.message}"
                is HeadingState.Available -> "${state.trueHeadingDeg}:${state.accuracy}"
            }
        }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int = windowManager.defaultDisplay.rotation

    private fun rotationMatrixForDisplay(
        rotationMatrix: FloatArray,
        displayRotation: Int,
    ): FloatArray? {
        if (displayRotation == Surface.ROTATION_0) return rotationMatrix

        val axes = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> return rotationMatrix
        }
        return FloatArray(9).takeIf { remapped ->
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                axes.first,
                axes.second,
                remapped,
            )
        }
    }
}

private fun Int.toHeadingAccuracy(): HeadingAccuracy = when (this) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HeadingAccuracy.HIGH
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> HeadingAccuracy.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> HeadingAccuracy.LOW
    SensorManager.SENSOR_STATUS_UNRELIABLE,
    SensorManager.SENSOR_STATUS_NO_CONTACT,
    -> HeadingAccuracy.UNRELIABLE
    else -> HeadingAccuracy.UNKNOWN
}
