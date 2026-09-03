package jp.usuiensan.taxijokemeter.domain.safety

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import kotlin.math.abs
import kotlin.math.max

class SafetyEventDetector(private val thresholds: SafetyThresholds = SafetyThresholds()) {
    private data class ActiveAcceleration(
        val type: SafetyEventType,
        val started: DrivingSample,
        var peak: Double,
        var belowSinceMillis: Long? = null,
    )
    private data class ActiveOverspeed(
        val started: DrivingSample,
        var peak: Double,
        var belowSinceMillis: Long? = null,
    )

    private val history = ArrayDeque<DrivingSample>()
    private var activeAcceleration: ActiveAcceleration? = null
    private var activeOverspeed: ActiveOverspeed? = null
    private var overspeedAboveSinceMillis: Long? = null
    private var overspeedPeak = 0.0
    private var overspeedStartedSample: DrivingSample? = null

    fun consume(sample: DrivingSample): List<SafetyEventSignal> {
        if (!isUsable(sample)) {
            history.clear()
            activeAcceleration = null
            activeOverspeed = null
            overspeedAboveSinceMillis = null
            overspeedStartedSample = null
            overspeedPeak = 0.0
            return emptyList()
        }

        val signals = buildList {
            acceleration(sample)?.let { addAll(handleAcceleration(sample, it)) }
            addAll(handleOverspeed(sample))
        }
        history.addLast(sample)
        while (history.size > 8) history.removeFirst()
        return signals
    }

    private fun isUsable(sample: DrivingSample): Boolean {
        if (!sample.isValid || sample.speedMps == null || sample.speedSource == SpeedSource.UNAVAILABLE || !sample.speedMps.isFinite() || sample.speedMps < 0.0) return false
        val previous = history.lastOrNull() ?: return true
        val now = timeMillis(sample)
        val previousTime = timeMillis(previous)
        val delta = now - previousTime
        return delta > 0 && delta <= thresholds.maxSampleGapSeconds * 1_000.0
    }

    private fun acceleration(current: DrivingSample): Double? {
        val currentTime = timeMillis(current)
        val previous = history.minByOrNull { previous -> abs((currentTime - timeMillis(previous)) / 1_000.0 - 3.0) }
            ?: return null
        val seconds = (currentTime - timeMillis(previous)) / 1_000.0
        if (seconds !in thresholds.accelerationWindowMinSeconds..thresholds.accelerationWindowMaxSeconds) return null
        return (current.speedMps!! - previous.speedMps!!) / seconds
    }

    private fun handleAcceleration(sample: DrivingSample, value: Double): List<SafetyEventSignal> {
        val active = activeAcceleration
        if (active == null) {
            val type = when {
                value >= thresholds.hardAccelerationMps2 -> SafetyEventType.HARD_ACCELERATION
                value <= thresholds.hardDecelerationMps2 -> SafetyEventType.HARD_DECELERATION
                else -> return emptyList()
            }
            activeAcceleration = ActiveAcceleration(type, sample, abs(value))
            return listOf(SafetyEventSignal.Started(type, accelerationMps2 = value))
        }

        active.peak = max(active.peak, abs(value))
        val settled = when (active.type) {
            SafetyEventType.HARD_ACCELERATION -> value < thresholds.accelerationReleaseMps2
            SafetyEventType.HARD_DECELERATION -> value > thresholds.decelerationReleaseMps2
            else -> false
        }
        if (!settled) {
            active.belowSinceMillis = null
            return emptyList()
        }
        val now = timeMillis(sample)
        val belowSince = active.belowSinceMillis ?: now.also { active.belowSinceMillis = it }
        if (now - belowSince < thresholds.accelerationReleaseDurationSeconds * 1_000.0) return emptyList()
        activeAcceleration = null
        return listOf(SafetyEventSignal.Completed(event(active.type, active.started, sample, active.peak, active.started.roadMode)))
    }

    private fun handleOverspeed(sample: DrivingSample): List<SafetyEventSignal> {
        val excess = sample.speedMps!! * 3.6 - sample.roadMode.safety.limitKmh
        val active = activeOverspeed
        if (active != null) {
            active.peak = max(active.peak, excess)
            if (excess > thresholds.overspeedReleaseExcessKmh) {
                active.belowSinceMillis = null
                return emptyList()
            }
            val now = timeMillis(sample)
            val belowSince = active.belowSinceMillis ?: now.also { active.belowSinceMillis = it }
            if (now - belowSince < thresholds.overspeedReleaseDurationSeconds * 1_000.0) return emptyList()
            activeOverspeed = null
            return listOf(SafetyEventSignal.Completed(event(SafetyEventType.OVERSPEED, active.started, sample, active.peak, active.started.roadMode)))
        }

        if (excess < thresholds.overspeedStartExcessKmh) {
            overspeedAboveSinceMillis = null
            overspeedStartedSample = null
            overspeedPeak = 0.0
            return emptyList()
        }
        val now = timeMillis(sample)
        val since = overspeedAboveSinceMillis ?: now.also { overspeedAboveSinceMillis = it; overspeedStartedSample = sample }
        overspeedPeak = max(overspeedPeak, excess)
        if (now - since < thresholds.overspeedStartDurationSeconds * 1_000.0) return emptyList()
        val started = overspeedStartedSample ?: sample
        activeOverspeed = ActiveOverspeed(started, overspeedPeak)
        overspeedAboveSinceMillis = null
        overspeedStartedSample = null
        overspeedPeak = 0.0
        return listOf(SafetyEventSignal.Started(SafetyEventType.OVERSPEED, speedKmh = (sample.speedMps!! * 3.6).toInt()))
    }

    private fun event(type: SafetyEventType, started: DrivingSample, ended: DrivingSample, peak: Double, roadMode: jp.usuiensan.taxijokemeter.domain.model.RoadMode) = SafetyEvent(
        type = type,
        startedAtElapsedRealtimeMillis = timeMillis(started), endedAtElapsedRealtimeMillis = timeMillis(ended),
        startedAtEpochMillis = started.timestamp.toEpochMilli(), endedAtEpochMillis = ended.timestamp.toEpochMilli(),
        peakValue = peak, roadMode = roadMode, source = SafetyEventSource.GPS,
    )

    private fun timeMillis(sample: DrivingSample) = sample.elapsedRealtimeNanos?.div(1_000_000L) ?: sample.timestamp.toEpochMilli()
}
