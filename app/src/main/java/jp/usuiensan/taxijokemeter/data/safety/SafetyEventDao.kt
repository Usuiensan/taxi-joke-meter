package jp.usuiensan.taxijokemeter.data.safety

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SafetyEventDao {
    @Insert
    suspend fun insert(event: SafetyEventEntity): Long

    @Query("SELECT * FROM safety_events WHERE startedAtEpochMillis >= :startEpochMillis AND startedAtEpochMillis < :endEpochMillis ORDER BY startedAtEpochMillis ASC, id ASC")
    suspend fun findBetween(startEpochMillis: Long, endEpochMillis: Long): List<SafetyEventEntity>
}
