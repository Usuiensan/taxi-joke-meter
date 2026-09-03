package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode

data class MeasurementRuntimeState(
    val sampleState: DrivingSampleState,
    val previousSample: DrivingSample? = null,
) {
    fun updateState(roadMode: RoadMode, occupancyState: OccupancyState) =
        copy(sampleState = sampleState.copy(roadMode = roadMode, occupancyState = occupancyState))

    fun advance(sample: DrivingSample) = copy(previousSample = sample)
}
