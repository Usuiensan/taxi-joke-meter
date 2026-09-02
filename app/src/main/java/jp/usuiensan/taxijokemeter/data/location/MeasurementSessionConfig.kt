package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode

data class MeasurementSessionConfig(
    val sessionId: String,
    val roadMode: RoadMode,
    val occupancyState: OccupancyState,
    val fareRule: FareRule,
)
