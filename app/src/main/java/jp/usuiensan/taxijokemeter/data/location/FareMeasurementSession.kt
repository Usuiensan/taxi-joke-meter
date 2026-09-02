package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementAccumulation
import jp.usuiensan.taxijokemeter.domain.measurement.TripMeasurementAccumulator
import jp.usuiensan.taxijokemeter.domain.model.FareRule

class FareMeasurementSession(fareRule: FareRule) {
    private val fareRule = fareRule
    private var accumulator: TripMeasurementAccumulator? = null
    private var anchor: DrivingSample? = null
    private var result: MeasurementAccumulation? = null

    fun begin() {
        accumulator = TripMeasurementAccumulator(fareRule)
        anchor = null
        result = null
    }

    fun add(current: DrivingSample): MeasurementAccumulation? {
        if (!current.isValid) {
            anchor = null
            return null
        }
        val previous = anchor
        anchor = current
        if (previous == null) return null
        val next = accumulator?.add(previous, current) ?: return null
        result = next
        return next
    }

    fun end(): MeasurementAccumulation? {
        val completed = result
        accumulator = null
        anchor = null
        result = null
        return completed
    }
}
