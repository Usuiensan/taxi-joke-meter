package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.model.FareRule

enum class ChargeProgressMode { DISTANCE, TIME }

data class ChargeProgressUiState(
    val mode: ChargeProgressMode,
    val fraction: Float,
)

object ChargeProgressMapper {
    fun initial(rule: FareRule, latestSpeedMps: Double?): ChargeProgressUiState = ChargeProgressUiState(
        mode = if (latestSpeedMps != null && latestSpeedMps * 3.6 < rule.lowSpeedLimitKmh) ChargeProgressMode.TIME else ChargeProgressMode.DISTANCE,
        fraction = 0f,
    )

    fun fraction(rule: FareRule, distanceMeters: Long, lowSpeedSeconds: Long): Float {
        val combined = distanceMeters * rule.lowSpeedTimeIncrementSeconds.toDouble() +
            lowSpeedSeconds * rule.distanceIncrementMeters.toDouble()
        val initial = rule.initialDistanceMeters * rule.lowSpeedTimeIncrementSeconds.toDouble()
        val increment = rule.distanceIncrementMeters * rule.lowSpeedTimeIncrementSeconds.toDouble()
        if (combined <= initial) return (combined / initial).toFloat().coerceIn(0f, 1f)
        return (((combined - initial) % increment) / increment).toFloat().coerceIn(0f, 1f)
    }
}
