package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FareCalculator {
    fun calculate(rule: FareRule, distanceMeters: Long, lowSpeedSeconds: Long): Long {
        require(distanceMeters >= 0 && lowSpeedSeconds >= 0)
        val distanceIncrements = if (distanceMeters <= rule.initialDistanceMeters) 0 else {
            (distanceMeters - rule.initialDistanceMeters + rule.distanceIncrementMeters - 1) / rule.distanceIncrementMeters
        }
        val timeIncrements = if (lowSpeedSeconds == 0L) 0 else {
            (lowSpeedSeconds + rule.lowSpeedTimeIncrementSeconds - 1) / rule.lowSpeedTimeIncrementSeconds
        }
        val increments = maxOf(distanceIncrements, timeIncrements)
        return rule.initialFareYen + increments * rule.distanceIncrementFareYen
    }
}
