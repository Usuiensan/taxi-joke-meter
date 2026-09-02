package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DutyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(session: DutySessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrips(trips: List<TripEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(events: List<DutyEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: DutyEventEntity)
    @Query("DELETE FROM trips WHERE sessionId = :sessionId") suspend fun deleteTrips(sessionId: String)
    @Query("DELETE FROM duty_events WHERE sessionId = :sessionId") suspend fun deleteEvents(sessionId: String)
    @Query("SELECT * FROM duty_sessions WHERE id = :id") suspend fun findSession(id: String): DutySessionEntity?
    @Query("SELECT * FROM trips WHERE sessionId = :sessionId ORDER BY pickupAt ASC") suspend fun findTrips(sessionId: String): List<TripEntity>
    @Query("SELECT * FROM duty_events WHERE sessionId = :sessionId ORDER BY occurredAt ASC") suspend fun findEvents(sessionId: String): List<DutyEventEntity>
}
