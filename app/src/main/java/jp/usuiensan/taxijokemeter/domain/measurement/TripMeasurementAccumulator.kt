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
    val mode: FareMeasurementMode = FareMeasurementMode.DISTANCE,
)

enum class FareMeasurementMode { DISTANCE, TIME, TIME_FALLBACK, UNAVAILABLE }

class TripMeasurementAccumulator(private val fareRule: FareRule) {
    private var distanceMeters = 0.0
    private var actualDistanceMeters = 0.0
    private var lowSpeedDuration = Duration.ZERO
    private var lastAcceptedSample: DrivingSample? = null

    fun add(previous: DrivingSample, current: DrivingSample, distanceOnly: Boolean = false): MeasurementAccumulation {
        if (!current.isFareContinuable) lastAcceptedSample = null
        val distancePrevious = lastAcceptedSample ?: previous
        val mode = when {
            !previous.isFareContinuable || !current.isFareContinuable -> FareMeasurementMode.UNAVAILABLE
            current.isTimeFallback -> {
                if (!distanceOnly) elapsedBetween(previous, current)?.let { if (!it.isZero) lowSpeedDuration = lowSpeedDuration.plus(it) }
                FareMeasurementMode.TIME_FALLBACK
            }
            previous.isTimeFallback -> FareMeasurementMode.DISTANCE
            previous.isValid && current.isValid -> {
            val elapsed = elapsedBetween(distancePrevious, current)
            val distance = segmentDistanceMeters(distancePrevious, current)
            val speedMps = current.speedMps ?: monotonicElapsed(distancePrevious, current)
                ?.takeIf { it > Duration.ZERO }
                ?.let { distance / it.toNanos() * 1_000_000_000.0 }
            val recoveryGap = elapsed?.toMillis()?.let { it > NORMAL_SAMPLE_GAP_MILLIS } == true &&
                distancePrevious.elapsedRealtimeNanos != null && current.elapsedRealtimeNanos != null
            val accepted = elapsed != null && elapsed.toMillis() <= MAX_GAP_MILLIS && !elapsed.isZero && speedMps != null &&
                (!recoveryGap || isPlausibleAverageSpeed(distance, elapsed)) &&
                (recoveryGap || isPlausibleDistance(distancePrevious, current, distance, elapsed))
            if (accepted) {
                actualDistanceMeters += distance
            }
            if (accepted || (elapsed != null && elapsed.toMillis() > MAX_GAP_MILLIS)) lastAcceptedSample = current
            if (recoveryGap && accepted) {
                distanceMeters += distance
            } else if (distanceOnly && accepted) {
                distanceMeters += distance
            } else if (accepted && speedMps * 3.6 <= fareRule.lowSpeedLimitKmh) {
                lowSpeedDuration = lowSpeedDuration.plus(elapsed)
            } else if (accepted) {
                distanceMeters += distance
            }
                if (current.speedMps != null && current.speedMps * 3.6 <= fareRule.lowSpeedLimitKmh) FareMeasurementMode.TIME else FareMeasurementMode.DISTANCE
            }
            else -> FareMeasurementMode.UNAVAILABLE
        }
        return MeasurementAccumulation(distanceMeters.roundToLong(), lowSpeedDuration.seconds, actualDistanceMeters.roundToLong(), mode)
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

    // ponytail: speed-based plausibility gate; use sensor fusion if GPS noise needs a richer model.
    private fun isPlausibleDistance(previous: DrivingSample, current: DrivingSample, distance: Double, elapsed: Duration): Boolean {
        val speed = current.speedMps ?: return true
        val previousSpeed = previous.speedMps ?: speed
        val expected = maxOf(speed, previousSpeed) * elapsed.toNanos() / 1_000_000_000.0
        return distance <= maxOf(100.0, expected * 4.0 + maxOf(previous.accuracyMeters, current.accuracyMeters) * 2.0)
    }

    private fun isPlausibleAverageSpeed(distance: Double, elapsed: Duration): Boolean =
        distance / elapsed.toNanos() * 3_600_000_000.0 <= MAX_PLAUSIBLE_AVERAGE_SPEED_KMH

    private companion object {
        const val NORMAL_SAMPLE_GAP_MILLIS = 2_000L
        const val MAX_GAP_MILLIS = 15_000L
        const val MAX_PLAUSIBLE_AVERAGE_SPEED_KMH = 200.0
    }

}
