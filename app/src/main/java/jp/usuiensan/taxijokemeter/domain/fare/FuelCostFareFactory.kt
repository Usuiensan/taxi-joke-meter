package jp.usuiensan.taxijokemeter.domain.fare

import kotlin.math.ceil
import kotlin.math.roundToLong
import jp.usuiensan.taxijokemeter.domain.model.EnergyCostProfile
import jp.usuiensan.taxijokemeter.domain.model.EnergyEfficiency
import jp.usuiensan.taxijokemeter.domain.model.FareRule

object FuelCostFareFactory {
    fun create(profile: EnergyCostProfile, initialDistanceMeters: Long, distanceIncrementMeters: Long, lowSpeedLimitKmh: Int): FareRule {
        val yenPerKm = when (val efficiency = profile.efficiency) {
            is EnergyEfficiency.KmPerLiter -> profile.unitPriceYen / efficiency.value
            is EnergyEfficiency.KwhPer100Km -> profile.unitPriceYen * efficiency.value / 100.0
        }
        val initialFare = (yenPerKm * initialDistanceMeters / 1_000).roundToLong()
        val incrementFare = (yenPerKm * distanceIncrementMeters / 1_000).roundToLong().coerceAtLeast(1)
        val seconds = ceil(distanceIncrementMeters / 1_000.0 / (lowSpeedLimitKmh / 3_600.0) / 5.0).toInt() * 5
        return FareRule(initialDistanceMeters, initialFare, distanceIncrementMeters, incrementFare, lowSpeedLimitKmh, seconds)
    }
}
