package jp.usuiensan.taxijokemeter.data.measurement

import androidx.room.Entity

@Entity(tableName = "driving_samples", primaryKeys = ["timestampEpochSecond", "timestampNano"])
data class DrivingSampleEntity(
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
