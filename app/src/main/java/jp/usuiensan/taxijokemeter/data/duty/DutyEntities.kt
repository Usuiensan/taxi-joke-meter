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
    indices = [androidx.room.Index("sessionId")],
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
