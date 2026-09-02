package jp.usuiensan.taxijokemeter.data.measurement

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "driving_samples", indices = [Index(value = ["timestampEpochSecond", "timestampNano"])])
data class DrivingSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochSecond: Long,
    val timestampNano: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMps: Double,
    val bearingDegrees: Double?,
    val roadMode: String,
    val occupancyState: String,
    val measurementQuality: String,
)
