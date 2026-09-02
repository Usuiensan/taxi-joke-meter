package jp.usuiensan.taxijokemeter.domain.measurement

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

enum class OccupancyState { EMPTY, OCCUPIED }

enum class MeasurementQuality { VALID, POOR_ACCURACY, STALE, UNAVAILABLE }

enum class SpeedSource { REPORTED, DERIVED, UNAVAILABLE }

data class DrivingSample(
    val dutySessionId: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val roadMode: RoadMode,
    val occupancyState: OccupancyState,
    val measurementQuality: MeasurementQuality,
    val elapsedRealtimeNanos: Long? = null,
    val speedSource: SpeedSource = if (speedMps == null) SpeedSource.UNAVAILABLE else SpeedSource.REPORTED,
) {
    val isValid: Boolean
        get() = measurementQuality == MeasurementQuality.VALID
}

val RoadMode.safetyLimitKmh: Int
    get() = safety.limitKmh
