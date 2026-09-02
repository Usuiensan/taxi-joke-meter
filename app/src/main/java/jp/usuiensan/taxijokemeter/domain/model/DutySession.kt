package jp.usuiensan.taxijokemeter.domain.model

import java.time.Instant

data class DutySession(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val trips: List<Trip> = emptyList(),
    val events: List<DutyEvent> = emptyList(),
)
