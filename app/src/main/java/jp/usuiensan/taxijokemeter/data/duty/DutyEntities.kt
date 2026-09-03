package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "duty_sessions")
data class DutySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
)

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("sessionId"), androidx.room.Index(value = ["serviceDate", "tripNumber"], unique = true)],
)
data class TripEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pickupAt: Instant,
    val dropoffAt: Instant?,
    val pickupLatitude: Double?, val pickupLongitude: Double?, val pickupAccuracyMeters: Double?, val pickupRecordedAt: Instant?, val pickupAddressText: String?, val pickupLocationPrecision: String?, val pickupSource: String?,
    val dropoffLatitude: Double?, val dropoffLongitude: Double?, val dropoffAccuracyMeters: Double?, val dropoffRecordedAt: Instant?, val dropoffAddressText: String?, val dropoffLocationPrecision: String?, val dropoffSource: String?,
    val passengerCount: Int,
    val operatingDistanceMeters: Long,
    val fareYen: Long,
    val additionalChargeYen: Long,
    val paymentMethod: String,
    val note: String?,
    val status: String = "COMPLETED",
    val lowSpeedSeconds: Long = 0,
    val revenueIncluded: Boolean = true,
    val serviceDate: String? = null,
    val tripNumber: Int? = null,
    val actualDistanceMeters: Long = 0,
    val chargeableDistanceMeters: Long = 0,
    val meteredFareYen: Long = 0,
    val totalFareYen: Long = 0,
    val fareInitialDistanceMeters: Long? = null,
    val fareInitialFareYen: Long? = null,
    val fareDistanceIncrementMeters: Long? = null,
    val fareDistanceIncrementFareYen: Long? = null,
    val fareLowSpeedLimitKmh: Int? = null,
    val fareLowSpeedTimeIncrementSeconds: Int? = null,
)

@Entity(
    tableName = "trip_places",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("tripId"), androidx.room.Index(value = ["tripId", "type", "sequence"], unique = true)],
)
data class TripPlaceEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val sequence: Int,
    val recordedAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val canonicalAddress: String?,
    val reportAddress: String?,
    val landmarkName: String?,
    val landmarkDetail: String?,
    val locationPrecision: String?,
    val source: String?,
)

@Entity(
    tableName = "trip_charges",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("tripId")],
)
data class TripChargeEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val amountYen: Long,
    val appliedAt: Instant,
    val label: String?,
)

@Entity(
    tableName = "rest_intervals",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("sessionId")],
)
data class RestIntervalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val startAccuracyMeters: Double?,
    val distanceMeters: Long = 0,
    val endReason: String?,
)

@Entity(
    tableName = "duty_events",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("sessionId")],
)
data class DutyEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val occurredAt: Instant,
    val latitude: Double?, val longitude: Double?, val accuracyMeters: Double?, val recordedAt: Instant?, val addressText: String?, val locationPrecision: String?, val source: String?,
    val safetyRoadModeFrom: String?,
    val safetyRoadModeTo: String?,
    val note: String?,
)
