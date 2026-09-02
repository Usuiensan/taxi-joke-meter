package jp.usuiensan.taxijokemeter.data.location

import android.location.Location
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
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
            return DrivingSample(state.dutySessionId, now, 0.0, 0.0, 0.0, 0.0, null, state.roadMode, state.occupancyState, MeasurementQuality.UNAVAILABLE)
        }

        val timestamp = Instant.ofEpochMilli(location.time)
        val quality = when {
            !now.isBefore(timestamp.plus(qualityPolicy.staleAfter)) -> MeasurementQuality.STALE
            !location.hasAccuracy() || location.accuracy.toDouble() > qualityPolicy.accuracyThresholdMeters -> MeasurementQuality.POOR_ACCURACY
            else -> MeasurementQuality.VALID
        }
        return DrivingSample(
            state.dutySessionId, timestamp, location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY,
            if (location.hasSpeed()) location.speed.toDouble() else 0.0,
            if (location.hasBearing()) location.bearing.toDouble() else null,
            state.roadMode, state.occupancyState, quality,
        )
    }
}

class AndroidDrivingSampleSource(
    private val mapper: LocationSampleMapper,
    private val stateProvider: () -> DrivingSampleState,
) {
    fun fromLocation(location: Location?, now: Instant): DrivingSample = mapper.map(location, stateProvider(), now)
}
