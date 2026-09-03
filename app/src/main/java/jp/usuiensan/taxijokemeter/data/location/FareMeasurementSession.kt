package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementAccumulation
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.TripMeasurementAccumulator
import jp.usuiensan.taxijokemeter.domain.model.FareRule

class FareMeasurementSession(fareRule: FareRule) {
    private val fareRule = fareRule
    private var accumulator: TripMeasurementAccumulator? = null
    private var anchor: DrivingSample? = null
    private var result: MeasurementAccumulation? = null
    private var occupancyState: OccupancyState? = null
    var isActive: Boolean = false
        private set

    fun begin(initialOccupancyState: OccupancyState? = null) {
        if (isActive) return
        accumulator = TripMeasurementAccumulator(fareRule)
        anchor = null
        result = null
        occupancyState = initialOccupancyState
        isActive = true
    }

    fun add(current: DrivingSample): MeasurementAccumulation? {
        if (!current.isValid) {
            anchor = null
            return null
        }
        if (occupancyState == null) occupancyState = current.occupancyState
        if (current.occupancyState != occupancyState) {
            occupancyState = current.occupancyState
            anchor = current
            return null
        }
        val previous = anchor
        anchor = current
        if (previous == null) return null
        val next = accumulator?.add(previous, current, distanceOnly = occupancyState == OccupancyState.EMPTY) ?: return null
        result = next
        return next
    }

    fun end(): MeasurementAccumulation? {
        if (!isActive) return null
        val completed = result
        accumulator = null
        anchor = null
        result = null
        occupancyState = null
        isActive = false
        return completed
    }

    fun setOccupancyState(next: OccupancyState) {
        if (occupancyState != next) anchor = null
        occupancyState = next
    }

    fun currentResult(): MeasurementAccumulation? = result
}
