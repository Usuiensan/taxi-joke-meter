package jp.usuiensan.taxijokemeter.domain.model

data class FareRule(
    val initialDistanceMeters: Long,
    val initialFareYen: Long,
    val distanceIncrementMeters: Long,
    val distanceIncrementFareYen: Long,
    val lowSpeedLimitKmh: Int,
    val lowSpeedTimeIncrementSeconds: Int,
) {
    init {
        require(initialDistanceMeters > 0 && initialFareYen >= 0)
        require(distanceIncrementMeters > 0 && distanceIncrementFareYen >= 0)
        require(lowSpeedLimitKmh > 0 && lowSpeedTimeIncrementSeconds > 0)
    }
}
