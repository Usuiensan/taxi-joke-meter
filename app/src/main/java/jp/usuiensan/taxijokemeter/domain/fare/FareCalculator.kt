package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FareCalculator {
    fun calculate(rule: FareRule, distanceMeters: Long, lowSpeedSeconds: Long): Long {
        require(distanceMeters >= 0 && lowSpeedSeconds >= 0)
        val lowSpeedDistance = rule.lowSpeedLimitKmh.toLong() * lowSpeedSeconds * 5 / 18
        val billableDistance = distanceMeters + lowSpeedDistance
        if (billableDistance <= rule.initialDistanceMeters) return rule.initialFareYen
        val increments = (billableDistance - rule.initialDistanceMeters + rule.distanceIncrementMeters - 1) / rule.distanceIncrementMeters
        return rule.initialFareYen + increments * rule.distanceIncrementFareYen
    }
}
