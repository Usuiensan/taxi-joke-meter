package jp.usuiensan.taxijokemeter.domain.measurement

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

enum class OccupancyState { EMPTY, PICKUP, REST, OCCUPIED, DEADHEAD }

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

    val isDisplayValid: Boolean
        get() = measurementQuality != MeasurementQuality.UNAVAILABLE &&
            measurementQuality != MeasurementQuality.STALE &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    val isFareContinuable: Boolean
        get() = isValid || (measurementQuality == MeasurementQuality.POOR_ACCURACY && isDisplayValid)

    val isTimeFallback: Boolean
        get() = measurementQuality == MeasurementQuality.POOR_ACCURACY && isDisplayValid
}

val RoadMode.safetyLimitKmh: Int
    get() = safety.limitKmh

object RestDistanceAccumulator {
    fun add(previous: DrivingSample?, current: DrivingSample): Double =
        if (previous?.isValid == true && current.isValid) segmentDistanceMeters(previous, current) else 0.0
}

internal fun segmentDistanceMeters(first: DrivingSample, second: DrivingSample): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) +
        kotlin.math.cos(Math.toRadians(first.latitude)) * kotlin.math.cos(Math.toRadians(second.latitude)) *
        kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
    return 6_371_000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
}
