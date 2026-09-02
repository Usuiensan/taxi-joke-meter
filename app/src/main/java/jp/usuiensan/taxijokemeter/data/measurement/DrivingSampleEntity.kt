package jp.usuiensan.taxijokemeter.data.measurement

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import jp.usuiensan.taxijokemeter.data.duty.DutySessionEntity

@Entity(
    tableName = "driving_samples",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["dutySessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dutySessionId"), Index(value = ["timestampEpochSecond", "timestampNano"])]
)
data class DrivingSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dutySessionId: String,
    val timestampEpochSecond: Long,
    val timestampNano: Int,
    val elapsedRealtimeNanos: Long?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMps: Double?,
    val speedSource: String,
    val bearingDegrees: Double?,
    val roadMode: String,
    val occupancyState: String,
    val measurementQuality: String,
)
