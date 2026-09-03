package jp.usuiensan.taxijokemeter.data.safety

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvent
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventSource
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType

@Entity(tableName = "safety_events", indices = [Index("startedAtEpochMillis"), Index("endedAtEpochMillis")])
data class SafetyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startedAtElapsedRealtimeMillis: Long,
    val endedAtElapsedRealtimeMillis: Long,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val peakValue: Double,
    val roadMode: String,
    val source: String,
)

fun SafetyEvent.toEntity() = SafetyEventEntity(
    id, type.name, startedAtElapsedRealtimeMillis, endedAtElapsedRealtimeMillis,
    startedAtEpochMillis, endedAtEpochMillis, peakValue, roadMode.name, source.name,
)

fun SafetyEventEntity.toDomain() = SafetyEvent(
    id, SafetyEventType.valueOf(type), startedAtElapsedRealtimeMillis, endedAtElapsedRealtimeMillis,
    startedAtEpochMillis, endedAtEpochMillis, peakValue, RoadMode.valueOf(roadMode), SafetyEventSource.valueOf(source),
)
