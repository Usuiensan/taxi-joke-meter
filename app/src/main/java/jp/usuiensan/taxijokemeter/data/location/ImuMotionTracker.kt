package jp.usuiensan.taxijokemeter.data.location

import android.hardware.Sensor
import android.hardware.SensorEvent
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.acos

enum class ImuMountState { UNSTABLE, CALIBRATING, STABLE }

class ImuMotionTracker {
    var state: ImuMountState = ImuMountState.UNSTABLE
        private set
    private var rotation = FloatArray(9)
    private var acceleration = FloatArray(3)
    private var gyroMagnitude = Float.POSITIVE_INFINITY
    private var stableSinceNanos: Long? = null
    private var stableRotation: FloatArray? = null

    fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val next = FloatArray(9)
                SensorManagerCompat.rotationMatrixFromVector(next, event.values)
                if (state == ImuMountState.STABLE && stableRotation != null && rotationAngle(stableRotation!!, next) > 20.0) {
                    state = ImuMountState.UNSTABLE
                    stableSinceNanos = null
                    stableRotation = null
                }
                rotation = next
            }
            Sensor.TYPE_ACCELEROMETER -> acceleration = event.values.copyOf()
            Sensor.TYPE_GYROSCOPE -> gyroMagnitude = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
        }
        val norm = sqrt(acceleration.sumOf { (it * it).toDouble() }).toFloat()
        val stable = gyroMagnitude < 0.20f && abs(norm - 9.80665f) < 1.25f
        if (!stable) { state = ImuMountState.UNSTABLE; stableSinceNanos = null }
        else if (state == ImuMountState.UNSTABLE) { state = ImuMountState.CALIBRATING; stableSinceNanos = event.timestamp }
        else if (state == ImuMountState.CALIBRATING && event.timestamp - (stableSinceNanos ?: event.timestamp) >= 3_000_000_000L) { state = ImuMountState.STABLE; stableRotation = rotation.copyOf() }
    }

    fun longitudinalAcceleration(bearingDegrees: Double?): Double? {
        if (state != ImuMountState.STABLE || bearingDegrees == null) return null
        val worldX = rotation[0] * acceleration[0] + rotation[1] * acceleration[1] + rotation[2] * acceleration[2]
        val worldY = rotation[3] * acceleration[0] + rotation[4] * acceleration[1] + rotation[5] * acceleration[2]
        val radians = Math.toRadians(bearingDegrees)
        return (worldX * kotlin.math.sin(radians) + worldY * kotlin.math.cos(radians)).toDouble()
    }

    private fun rotationAngle(first: FloatArray, second: FloatArray): Double {
        val trace = (0..2).sumOf { row -> (0..2).sumOf { col -> first[row * 3 + col].toDouble() * second[row * 3 + col] } }
        return Math.toDegrees(acos(((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)))
    }
}

private object SensorManagerCompat {
    fun rotationMatrixFromVector(matrix: FloatArray, vector: FloatArray) {
        android.hardware.SensorManager.getRotationMatrixFromVector(matrix, vector)
    }
}
