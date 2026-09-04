package jp.usuiensan.taxijokemeter.domain.safety

import jp.usuiensan.taxijokemeter.domain.model.RoadMode

enum class SafetyEventType { OVERSPEED, HARD_ACCELERATION, HARD_DECELERATION }
enum class SafetyEventSource { GPS }

data class SafetyThresholds(
    val hardAccelerationMps2: Double = 2.5,
    val hardDecelerationMps2: Double = -3.0,
    val accelerationReleaseMps2: Double = 1.47,
    val decelerationReleaseMps2: Double = -1.47,
    val accelerationWindowMinSeconds: Double = 2.5,
    val accelerationWindowMaxSeconds: Double = 3.5,
    val maxSampleGapSeconds: Double = 5.0,
    val overspeedStartExcessKmh: Double = 5.0,
    val overspeedReleaseExcessKmh: Double = 2.0,
    val overspeedStartDurationSeconds: Double = 5.0,
    val overspeedReleaseDurationSeconds: Double = 2.0,
    val accelerationReleaseDurationSeconds: Double = 1.5,
)

/** peakValue is km/h excess for OVERSPEED, and absolute m/s² for acceleration events. */
data class SafetyEvent(
    val id: Long = 0,
    val type: SafetyEventType,
    val startedAtElapsedRealtimeMillis: Long,
    val endedAtElapsedRealtimeMillis: Long,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val peakValue: Double,
    val roadMode: RoadMode,
    val source: SafetyEventSource,
)

sealed interface SafetyEventSignal {
    data class Started(
        val type: SafetyEventType,
        val accelerationMps2: Double? = null,
        val speedKmh: Int? = null,
        val detectionId: String? = null,
        val currentSpeedMps: Double? = null,
        val previousSpeedMps: Double? = null,
        val threshold: Double? = null,
        val locationTimestampMillis: Long? = null,
        val elapsedRealtimeMillis: Long? = null,
    ) : SafetyEventSignal
    data class Completed(val event: SafetyEvent) : SafetyEventSignal
}
