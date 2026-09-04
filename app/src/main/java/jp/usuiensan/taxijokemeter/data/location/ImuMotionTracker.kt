package jp.usuiensan.taxijokemeter.data.location

import android.hardware.Sensor
import android.hardware.SensorEvent
import kotlin.math.abs
import kotlin.math.sqrt

enum class ImuMountState { UNSTABLE, CALIBRATING, STABLE }

class ImuMotionTracker {
    var state: ImuMountState = ImuMountState.UNSTABLE
        private set
    private var rotation = FloatArray(9)
    private var acceleration = FloatArray(3)
    private var gyroMagnitude = Float.POSITIVE_INFINITY
    private var stableSinceNanos: Long? = null

    fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> { rotation = FloatArray(9); SensorManagerCompat.rotationMatrixFromVector(rotation, event.values) }
            Sensor.TYPE_ACCELEROMETER -> acceleration = event.values.copyOf()
            Sensor.TYPE_GYROSCOPE -> gyroMagnitude = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
        }
        val norm = sqrt(acceleration.sumOf { (it * it).toDouble() }).toFloat()
        val stable = gyroMagnitude < 0.20f && abs(norm - 9.80665f) < 1.25f
        if (!stable) { state = ImuMountState.UNSTABLE; stableSinceNanos = null }
        else if (state == ImuMountState.UNSTABLE) { state = ImuMountState.CALIBRATING; stableSinceNanos = event.timestamp }
        else if (state == ImuMountState.CALIBRATING && event.timestamp - (stableSinceNanos ?: event.timestamp) >= 3_000_000_000L) state = ImuMountState.STABLE
    }

    fun longitudinalAcceleration(bearingDegrees: Double?): Double? {
        if (state != ImuMountState.STABLE || bearingDegrees == null) return null
        val worldX = rotation[0] * acceleration[0] + rotation[1] * acceleration[1] + rotation[2] * acceleration[2]
        val worldY = rotation[3] * acceleration[0] + rotation[4] * acceleration[1] + rotation[5] * acceleration[2]
        val radians = Math.toRadians(bearingDegrees)
        return (worldX * kotlin.math.sin(radians) + worldY * kotlin.math.cos(radians)).toDouble()
    }
}

private object SensorManagerCompat {
    fun rotationMatrixFromVector(matrix: FloatArray, vector: FloatArray) {
        android.hardware.SensorManager.getRotationMatrixFromVector(matrix, vector)
    }
}
