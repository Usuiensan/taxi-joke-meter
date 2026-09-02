package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

data class HighwayModeSample(
    val timestamp: Instant,
    val speedMps: Double,
    val isValid: Boolean = true,
)

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
        samples: List<HighwayModeSample>,
        lastNotifiedAt: Instant?,
        now: Instant,
    ): Boolean {
        if (currentMode == RoadMode.GENERAL || lastNotifiedAt != null) return false

        val lowSpeedSeconds = samples.zipWithNext().sumOf { (sample, next) ->
            if (sample.isValid && next.isValid && averageSpeedKmh(sample, next) < averageSpeedThresholdKmh) {
                (next.timestamp.epochSecond - sample.timestamp.epochSecond).coerceAtLeast(0)
            } else 0
        } + samples.lastOrNull()?.let { sample ->
            if (sample.isValid && sample.speedMps * 3.6 < averageSpeedThresholdKmh) {
                (now.epochSecond - sample.timestamp.epochSecond).coerceAtLeast(0)
            } else 0
        }.orZero()

        return lowSpeedSeconds >= reminderWindowSeconds
    }

    private fun averageSpeedKmh(first: HighwayModeSample, second: HighwayModeSample) =
        (first.speedMps + second.speedMps) * 3.6 / 2

    private fun Long?.orZero() = this ?: 0L

    private companion object {
    }
}
