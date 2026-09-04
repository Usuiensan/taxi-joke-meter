package jp.usuiensan.taxijokemeter.domain.model

import java.math.BigInteger

enum class FareAdjustmentType { NONE, SURCHARGE, DISCOUNT }

data class FareAdjustmentRates(val surchargePercent: Int = 0, val discountPercent: Int = 0) {
    init { require(surchargePercent in 0..999 && discountPercent in 0..999) }
}

data class FareAdjustmentSelection(val type: FareAdjustmentType = FareAdjustmentType.NONE, val percent: Int = 0)

fun toggleFareAdjustment(current: FareAdjustmentSelection, requested: FareAdjustmentType, rates: FareAdjustmentRates): FareAdjustmentSelection =
    if (current.type == requested) FareAdjustmentSelection() else FareAdjustmentSelection(requested, if (requested == FareAdjustmentType.SURCHARGE) rates.surchargePercent else rates.discountPercent)

fun adjustedMeteredFareYen(meteredFareYen: Long, type: FareAdjustmentType, percent: Int): Long {
    require(meteredFareYen >= 0)
    require(percent in 0..999)
    val multiplier = when (type) {
        FareAdjustmentType.NONE -> 100
        FareAdjustmentType.SURCHARGE -> 100 + percent
        FareAdjustmentType.DISCOUNT -> (100 - percent).coerceAtLeast(0)
    }
    val value = BigInteger.valueOf(meteredFareYen).multiply(BigInteger.valueOf(multiplier.toLong()))
        .divide(BigInteger.valueOf(100))
    return value.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
}

fun totalFareYen(meteredFareYen: Long, adjustmentType: FareAdjustmentType, adjustmentPercent: Int, fareChargesYen: Long): Long =
    adjustedMeteredFareYen(meteredFareYen, adjustmentType, adjustmentPercent) + fareChargesYen
