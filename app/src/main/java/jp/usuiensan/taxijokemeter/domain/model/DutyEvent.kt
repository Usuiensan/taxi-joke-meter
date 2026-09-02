package jp.usuiensan.taxijokemeter.domain.model

import java.time.Instant

enum class DutyEventType {
    DEPARTURE,
    ARRIVAL,
    PICKUP,
    DROPOFF,
    DEADHEAD_START,
    DEADHEAD_END,
    BREAK_START,
    BREAK_END,
    SAFETY_ROAD_MODE_CHANGE,
}

data class DutyEvent(
    val id: String,
    val type: DutyEventType,
    val occurredAt: Instant,
    val place: RecordedPlace?,
    val note: String?,
    val safetyRoadModeChange: SafetyRoadModeChange? = null,
)
