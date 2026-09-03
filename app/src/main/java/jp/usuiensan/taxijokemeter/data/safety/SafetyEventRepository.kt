package jp.usuiensan.taxijokemeter.data.safety

import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvent

class SafetyEventRepository(private val database: DutyDatabase) {
    suspend fun insert(event: SafetyEvent) = database.safetyEventDao().insert(event.toEntity())

    suspend fun getEventsBetween(startEpochMillis: Long, endEpochMillis: Long): List<SafetyEvent> =
        database.safetyEventDao().findBetween(startEpochMillis, endEpochMillis).map(SafetyEventEntity::toDomain)
}
