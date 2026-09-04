package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule

data class FareCalculationBreakdown(
    val chargeableDistanceMeters: Long,
    val lowSpeedSeconds: Long,
    val timeEquivalentDistanceMeters: Double,
    val effectiveDistanceMeters: Double,
    val initialDistanceMeters: Long,
    val subsequentChargeCount: Long,
    val meteredFareYen: Long,
)

object FareCalculator {
    fun calculate(rule: FareRule, chargeableDistanceMeters: Long, lowSpeedSeconds: Long): Long {
        return breakdown(rule, chargeableDistanceMeters, lowSpeedSeconds).meteredFareYen
    }

    fun breakdown(rule: FareRule, chargeableDistanceMeters: Long, lowSpeedSeconds: Long): FareCalculationBreakdown {
        require(chargeableDistanceMeters >= 0 && lowSpeedSeconds >= 0)
        val combinedScaled = chargeableDistanceMeters * rule.lowSpeedTimeIncrementSeconds +
            lowSpeedSeconds * rule.distanceIncrementMeters
        val initialScaled = rule.initialDistanceMeters * rule.lowSpeedTimeIncrementSeconds
        val incrementScaled = rule.distanceIncrementMeters * rule.lowSpeedTimeIncrementSeconds
        val increments = if (combinedScaled <= initialScaled) 0 else {
            (combinedScaled - initialScaled + incrementScaled - 1) / incrementScaled
        }
        val timeEquivalentDistanceMeters = lowSpeedSeconds.toDouble() * rule.distanceIncrementMeters / rule.lowSpeedTimeIncrementSeconds
        return FareCalculationBreakdown(
            chargeableDistanceMeters = chargeableDistanceMeters,
            lowSpeedSeconds = lowSpeedSeconds,
            timeEquivalentDistanceMeters = timeEquivalentDistanceMeters,
            effectiveDistanceMeters = chargeableDistanceMeters + timeEquivalentDistanceMeters,
            initialDistanceMeters = rule.initialDistanceMeters,
            subsequentChargeCount = increments,
            meteredFareYen = rule.initialFareYen + increments * rule.distanceIncrementFareYen,
        )
    }
}
