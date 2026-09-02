package jp.usuiensan.taxijokemeter.data.measurement

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driving_samples")
data class DrivingSampleEntity(
    @PrimaryKey val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMps: Double,
    val bearingDegrees: Double?,
    val roadMode: String,
    val occupancyState: String,
    val measurementQuality: String,
)
