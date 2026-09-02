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
)

class TripMeasurementAccumulator(private val fareRule: FareRule) {
    private var distanceMeters = 0.0
    private var lowSpeedDuration = Duration.ZERO

    fun add(previous: DrivingSample, current: DrivingSample): MeasurementAccumulation {
        val elapsed = Duration.between(previous.timestamp, current.timestamp)
        if (previous.isValid && current.isValid && !elapsed.isNegative && !elapsed.isZero) {
            val distance = haversineMeters(previous, current)
            if (current.speedMps * 3.6 < fareRule.lowSpeedLimitKmh) {
                lowSpeedDuration = lowSpeedDuration.plus(elapsed)
            } else {
                distanceMeters += distance
            }
        }
        return MeasurementAccumulation(distanceMeters.roundToLong(), lowSpeedDuration.seconds)
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
