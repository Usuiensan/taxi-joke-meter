package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FareCalculator {
    fun calculate(rule: FareRule, chargeableDistanceMeters: Long, lowSpeedSeconds: Long): Long {
        require(chargeableDistanceMeters >= 0 && lowSpeedSeconds >= 0)
        val combinedScaled = chargeableDistanceMeters * rule.lowSpeedTimeIncrementSeconds +
            lowSpeedSeconds * rule.distanceIncrementMeters
        val initialScaled = rule.initialDistanceMeters * rule.lowSpeedTimeIncrementSeconds
        val incrementScaled = rule.distanceIncrementMeters * rule.lowSpeedTimeIncrementSeconds
        val increments = if (combinedScaled <= initialScaled) 0 else {
            (combinedScaled - initialScaled + incrementScaled - 1) / incrementScaled
        }
        return rule.initialFareYen + increments * rule.distanceIncrementFareYen
    }
}
