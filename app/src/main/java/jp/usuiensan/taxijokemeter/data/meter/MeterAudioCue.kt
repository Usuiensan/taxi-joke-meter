package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType

enum class MeterAudioCue {
    OCCUPIED,
    PAYMENT,
    OVERSPEED,
    RETURN_TO_GENERAL,
    HARD_ACCELERATION,
    HARD_DECELERATION,
}

internal fun MeterAudioCue.isSafetyNotification() =
    this == MeterAudioCue.OVERSPEED ||
        this == MeterAudioCue.HARD_ACCELERATION ||
        this == MeterAudioCue.HARD_DECELERATION

internal fun SafetyEventType.toMeterAudioCue() = when (this) {
    SafetyEventType.OVERSPEED -> MeterAudioCue.OVERSPEED
    SafetyEventType.HARD_ACCELERATION -> MeterAudioCue.HARD_ACCELERATION
    SafetyEventType.HARD_DECELERATION -> MeterAudioCue.HARD_DECELERATION
}
