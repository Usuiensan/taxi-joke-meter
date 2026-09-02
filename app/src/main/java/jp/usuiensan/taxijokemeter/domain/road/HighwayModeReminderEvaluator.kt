package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

data class HighwayModeSample(
    val timestamp: Instant,
    val speedMps: Double,
    val isValid: Boolean = true,
)

class HighwayModeReminderEvaluator {
    fun evaluate(
        currentMode: RoadMode,
        samples: List<HighwayModeSample>,
        lastNotifiedAt: Instant?,
        now: Instant,
    ): Boolean {
        if (currentMode == RoadMode.GENERAL || lastNotifiedAt != null) return false

        val validSamples = samples.filter { it.isValid }.sortedBy { it.timestamp }
        val lowSpeedSeconds = validSamples.zipWithNext().sumOf { (sample, next) ->
            if (sample.speedMps < currentMode.safety.limitKmh / 3.6) {
                (next.timestamp.epochSecond - sample.timestamp.epochSecond).coerceAtLeast(0)
            } else 0
        } + validSamples.lastOrNull()?.let { sample ->
            if (sample.speedMps < currentMode.safety.limitKmh / 3.6) {
                (now.epochSecond - sample.timestamp.epochSecond).coerceAtLeast(0)
            } else 0
        }.orZero()

        return lowSpeedSeconds >= REMINDER_AFTER_SECONDS
    }

    private fun Long?.orZero() = this ?: 0L

    private companion object {
        const val REMINDER_AFTER_SECONDS = 60L
    }
}
