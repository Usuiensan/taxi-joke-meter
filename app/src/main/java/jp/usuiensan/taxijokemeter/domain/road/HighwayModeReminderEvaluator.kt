package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Duration
import java.time.Instant

class HighwayModeReminderEvaluator(
    private val reminderWindowSeconds: Long = 60,
    private val averageSpeedThresholdKmh: Double = 40.0,
) {
    init {
        require(reminderWindowSeconds > 0)
        require(averageSpeedThresholdKmh >= 0)
    }

    fun evaluate(
        currentMode: RoadMode,
        samples: List<DrivingSample>,
        lastNotifiedAt: Instant?,
        now: Instant,
    ): Boolean {
        if (currentMode == RoadMode.GENERAL || lastNotifiedAt != null) return false

        val windowStart = now.minusSeconds(reminderWindowSeconds)
        val lowSpeedSeconds = samples.sortedBy { it.timestamp }.zipWithNext().sumOf { (sample, next) ->
            if (sample.measurementQuality == MeasurementQuality.VALID &&
                next.measurementQuality == MeasurementQuality.VALID &&
                averageSpeedKmh(sample, next) < averageSpeedThresholdKmh
            ) {
                val overlapStart = maxOf(sample.timestamp, windowStart)
                val overlapEnd = minOf(next.timestamp, now)
                Duration.between(overlapStart, overlapEnd).seconds.coerceAtLeast(0)
            } else 0
        }

        return lowSpeedSeconds >= reminderWindowSeconds
    }

    private fun averageSpeedKmh(first: DrivingSample, second: DrivingSample) =
        (first.speedMps + second.speedMps) * 3.6 / 2

}
