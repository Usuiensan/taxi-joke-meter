package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FareCalculator {
    fun calculate(rule: FareRule, chargeableDistanceMeters: Long, lowSpeedSeconds: Long): Long {
        require(chargeableDistanceMeters >= 0 && lowSpeedSeconds >= 0)
        val distanceIncrements = if (chargeableDistanceMeters <= rule.initialDistanceMeters) 0 else {
            (chargeableDistanceMeters - rule.initialDistanceMeters + rule.distanceIncrementMeters - 1) /
                rule.distanceIncrementMeters
        }
        val timeIncrements = (lowSpeedSeconds + rule.lowSpeedTimeIncrementSeconds - 1) /
            rule.lowSpeedTimeIncrementSeconds
        val increments = maxOf(distanceIncrements, timeIncrements)
        return rule.initialFareYen + increments * rule.distanceIncrementFareYen
    }
}
