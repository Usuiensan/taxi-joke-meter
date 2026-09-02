package jp.usuiensan.taxijokemeter.data.location

import android.location.Location
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

data class DrivingSampleState(
    val dutySessionId: String,
    val roadMode: RoadMode,
    val occupancyState: OccupancyState,
)

class LocationSampleMapper(private val qualityPolicy: MeasurementQualityPolicy) {
    fun map(location: Location?, state: DrivingSampleState, now: Instant): DrivingSample {
        if (location == null) {
            return DrivingSample(
                dutySessionId = state.dutySessionId, timestamp = now, latitude = 0.0, longitude = 0.0,
                accuracyMeters = 0.0, speedMps = null, bearingDegrees = null, roadMode = state.roadMode,
                occupancyState = state.occupancyState, measurementQuality = MeasurementQuality.UNAVAILABLE,
                speedSource = SpeedSource.UNAVAILABLE,
            )
        }

        val timestamp = Instant.ofEpochMilli(location.time)
        val quality = when {
            !now.isBefore(timestamp.plus(qualityPolicy.staleAfter)) -> MeasurementQuality.STALE
            !location.hasAccuracy() || location.accuracy.toDouble() > qualityPolicy.accuracyThresholdMeters -> MeasurementQuality.POOR_ACCURACY
            else -> MeasurementQuality.VALID
        }
        val hasReportedSpeed = location.hasSpeed()
        return DrivingSample(
            dutySessionId = state.dutySessionId,
            timestamp = timestamp,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos.takeIf { it > 0L },
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY,
            speedMps = location.speed.toDouble().takeIf { hasReportedSpeed },
            speedSource = if (hasReportedSpeed) SpeedSource.REPORTED else SpeedSource.UNAVAILABLE,
            bearingDegrees = location.bearing.toDouble().takeIf { location.hasBearing() },
            roadMode = state.roadMode,
            occupancyState = state.occupancyState,
            measurementQuality = quality,
        )
    }
}

class AndroidDrivingSampleSource(
    private val mapper: LocationSampleMapper,
    private val stateProvider: () -> DrivingSampleState,
) {
    fun fromLocation(location: Location?, now: Instant): DrivingSample = mapper.map(location, stateProvider(), now)
}
