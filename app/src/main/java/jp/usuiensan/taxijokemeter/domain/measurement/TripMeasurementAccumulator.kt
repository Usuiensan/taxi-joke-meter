package jp.usuiensan.taxijokemeter.domain.measurement

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.Duration

data class MeasurementAccumulation(
    val chargeableDistanceMeters: Long,
    val lowSpeedSeconds: Long,
    val actualDistanceMeters: Long = 0,
)

class TripMeasurementAccumulator(private val fareRule: FareRule) {
    private var distanceMeters = 0.0
    private var actualDistanceMeters = 0.0
    private var lowSpeedDuration = Duration.ZERO

    fun add(previous: DrivingSample, current: DrivingSample, distanceOnly: Boolean = false): MeasurementAccumulation {
        if (previous.isValid && current.isValid) {
            val elapsed = elapsedBetween(previous, current)
            val distance = haversineMeters(previous, current)
            val speedMps = current.speedMps ?: monotonicElapsed(previous, current)
                ?.takeIf { it > Duration.ZERO }
                ?.let { distance / it.toNanos() * 1_000_000_000.0 }
            if (elapsed != null && !elapsed.isZero && speedMps != null) {
                actualDistanceMeters += distance
            }
            if (distanceOnly && elapsed != null && !elapsed.isZero && speedMps != null) {
                distanceMeters += distance
            } else if (elapsed != null && !elapsed.isZero && speedMps != null && speedMps * 3.6 < fareRule.lowSpeedLimitKmh) {
                lowSpeedDuration = lowSpeedDuration.plus(elapsed)
            } else if (elapsed != null && !elapsed.isZero && speedMps != null) {
                distanceMeters += distance
            }
        }
        return MeasurementAccumulation(distanceMeters.roundToLong(), lowSpeedDuration.seconds, actualDistanceMeters.roundToLong())
    }

    private fun elapsedBetween(previous: DrivingSample, current: DrivingSample): Duration? {
        monotonicElapsed(previous, current)?.let { return it }
        if (previous.elapsedRealtimeNanos != null && current.elapsedRealtimeNanos != null) return null
        return Duration.between(previous.timestamp, current.timestamp).takeIf { !it.isNegative && !it.isZero }
    }

    private fun monotonicElapsed(previous: DrivingSample, current: DrivingSample): Duration? {
        if (previous.elapsedRealtimeNanos == null || current.elapsedRealtimeNanos == null) return null
        return (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos).takeIf { it > 0 }?.let(Duration::ofNanos)
    }

    private fun haversineMeters(first: DrivingSample, second: DrivingSample): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
