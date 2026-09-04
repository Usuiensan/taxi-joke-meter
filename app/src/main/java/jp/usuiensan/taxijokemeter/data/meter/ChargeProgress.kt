package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.model.FareRule

enum class ChargeProgressMode { DISTANCE, TIME }

data class ChargeProgressUiState(
    val mode: ChargeProgressMode,
    val fraction: Float,
)

object ChargeProgressMapper {
    fun initial(rule: FareRule, latestSpeedMps: Double?): ChargeProgressUiState = ChargeProgressUiState(
        mode = if (latestSpeedMps != null && latestSpeedMps * 3.6 <= rule.lowSpeedLimitKmh) ChargeProgressMode.TIME else ChargeProgressMode.DISTANCE,
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

/** Keeps the visual state stable while the fare accumulator continues using its own exact samples. */
class ChargeProgressModeController(
    private val lowSpeedKmh: Double,
    private val timeToEnterMillis: Long = 5_000L,
    private val timeToExitMillis: Long = 3_000L,
    private val exitSpeedKmh: Double = lowSpeedKmh + 2.0,
) {
    private var mode = ChargeProgressMode.DISTANCE
    private var candidateSince: Long? = null

    fun reset(initial: ChargeProgressMode = ChargeProgressMode.DISTANCE) { mode = initial; candidateSince = null }

    fun update(speedMps: Double?, nowMillis: Long): ChargeProgressMode {
        val speed = speedMps?.takeIf(Double::isFinite)?.times(3.6) ?: return mode
        val target = when {
            mode == ChargeProgressMode.DISTANCE && speed <= lowSpeedKmh -> ChargeProgressMode.TIME
            mode == ChargeProgressMode.TIME && speed >= exitSpeedKmh -> ChargeProgressMode.DISTANCE
            else -> mode
        }
        if (target == mode) { candidateSince = null; return mode }
        val since = candidateSince ?: nowMillis.also { candidateSince = it }
        val duration = if (target == ChargeProgressMode.TIME) timeToEnterMillis else timeToExitMillis
        if (nowMillis - since >= duration) { mode = target; candidateSince = null }
        return mode
    }
}
