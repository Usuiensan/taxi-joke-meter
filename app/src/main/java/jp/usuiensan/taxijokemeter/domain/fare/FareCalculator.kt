package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FareCalculator {
    fun calculate(rule: FareRule, distanceMeters: Long, lowSpeedSeconds: Long): Long {
        require(distanceMeters >= 0 && lowSpeedSeconds >= 0)
        val combinedScaled =
            distanceMeters * rule.lowSpeedTimeIncrementSeconds +
                lowSpeedSeconds * rule.distanceIncrementMeters
        val initialDistanceScaled = rule.initialDistanceMeters * rule.lowSpeedTimeIncrementSeconds
        val incrementDistanceScaled = rule.distanceIncrementMeters * rule.lowSpeedTimeIncrementSeconds
        val increments = if (combinedScaled <= initialDistanceScaled) 0 else {
            (combinedScaled - initialDistanceScaled + incrementDistanceScaled - 1) / incrementDistanceScaled
        }
        return rule.initialFareYen + increments * rule.distanceIncrementFareYen
    }
}
