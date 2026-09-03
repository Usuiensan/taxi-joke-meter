package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant

@Dao
interface DutyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(session: DutySessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrips(trips: List<TripEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrip(trip: TripEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(events: List<DutyEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: DutyEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTripPlace(place: TripPlaceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTripCharge(charge: TripChargeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRestInterval(interval: RestIntervalEntity)
    @Query("DELETE FROM trips WHERE sessionId = :sessionId") suspend fun deleteTrips(sessionId: String)
    @Query("DELETE FROM duty_events WHERE sessionId = :sessionId") suspend fun deleteEvents(sessionId: String)
    @Query("SELECT * FROM duty_sessions WHERE id = :id") suspend fun findSession(id: String): DutySessionEntity?
    @Query("SELECT * FROM trips WHERE sessionId = :sessionId ORDER BY pickupAt ASC") suspend fun findTrips(sessionId: String): List<TripEntity>
    @Query("SELECT * FROM duty_events WHERE sessionId = :sessionId ORDER BY occurredAt ASC") suspend fun findEvents(sessionId: String): List<DutyEventEntity>
    @Query("UPDATE duty_sessions SET endedAt = :endedAt WHERE id = :id") suspend fun updateEndedAt(id: String, endedAt: Instant)
    @Query("SELECT MAX(tripNumber) FROM trips WHERE serviceDate = :serviceDate") suspend fun maxTripNumber(serviceDate: String): Int?
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun findTrip(id: String): TripEntity?
    @Query("SELECT * FROM trip_places WHERE tripId = :tripId AND type = :type AND sequence = :sequence")
    suspend fun findTripPlace(tripId: String, type: String, sequence: Int): TripPlaceEntity?
    @Query("SELECT MAX(sequence) FROM trip_places WHERE tripId = :tripId AND type = 'WAYPOINT'")
    suspend fun maxWaypointSequence(tripId: String): Int?
    @Query("SELECT * FROM trip_places WHERE tripId = :tripId ORDER BY type, sequence")
    suspend fun findTripPlaces(tripId: String): List<TripPlaceEntity>
    @Query("SELECT * FROM trip_charges WHERE tripId = :tripId ORDER BY appliedAt")
    suspend fun findTripCharges(tripId: String): List<TripChargeEntity>
    @Query("SELECT * FROM rest_intervals WHERE id = :id")
    suspend fun findRestInterval(id: String): RestIntervalEntity?
    @Query("UPDATE trip_places SET canonicalAddress = COALESCE(:canonicalAddress, canonicalAddress), reportAddress = COALESCE(:reportAddress, reportAddress), landmarkName = COALESCE(:landmarkName, landmarkName), landmarkDetail = COALESCE(:landmarkDetail, landmarkDetail) WHERE id = :id")
    suspend fun updateTripPlaceAddresses(id: String, canonicalAddress: String?, reportAddress: String?, landmarkName: String?, landmarkDetail: String?)
    @Query("UPDATE trips SET passengerCount = :passengerCount WHERE id = :id")
    suspend fun updatePassengerCount(id: String, passengerCount: Int)
    @Query("UPDATE rest_intervals SET endedAt = :endedAt, endReason = :endReason WHERE id = :id")
    suspend fun finishRestInterval(id: String, endedAt: Instant, endReason: String)
    @Query("UPDATE rest_intervals SET distanceMeters = :distanceMeters WHERE id = :id")
    suspend fun updateRestDistance(id: String, distanceMeters: Long)
    @Query("UPDATE trips SET actualDistanceMeters = :actualDistanceMeters, chargeableDistanceMeters = :chargeableDistanceMeters, lowSpeedSeconds = :lowSpeedSeconds, meteredFareYen = :meteredFareYen, totalFareYen = :totalFareYen WHERE id = :id")
    suspend fun updateTripMeasurement(id: String, actualDistanceMeters: Long, chargeableDistanceMeters: Long, lowSpeedSeconds: Long, meteredFareYen: Long, totalFareYen: Long)
    @Query("UPDATE trips SET dropoffAt = :dropoffAt, status = :status, revenueIncluded = :revenueIncluded WHERE id = :id")
    suspend fun finalizeTrip(id: String, dropoffAt: Instant?, status: String, revenueIncluded: Boolean)
}
