package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.RestDistanceAccumulator
import jp.usuiensan.taxijokemeter.domain.model.RoadMode

data class MeasurementRuntimeState(
    val sampleState: DrivingSampleState,
    val previousSample: DrivingSample? = null,
    val restDistanceMeters: Double = 0.0,
) {
    fun updateState(roadMode: RoadMode, occupancyState: OccupancyState) =
        copy(
            sampleState = sampleState.copy(roadMode = roadMode, occupancyState = occupancyState),
            restDistanceMeters = if (sampleState.occupancyState == OccupancyState.REST && occupancyState == OccupancyState.REST) restDistanceMeters else 0.0,
        )

    fun advance(sample: DrivingSample) = copy(
        previousSample = sample,
        restDistanceMeters = if (sample.occupancyState == OccupancyState.REST) {
            restDistanceMeters + if (previousSample?.occupancyState == OccupancyState.REST) RestDistanceAccumulator.add(previousSample, sample) else 0.0
        } else 0.0,
    )
}
