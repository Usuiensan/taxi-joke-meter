package jp.usuiensan.taxijokemeter.domain.model

enum class FareRoadMode(val isHighway: Boolean) { GENERAL(false), HIGHWAY(true) }

enum class SafetyRoadMode(val limitKmh: Int) {
    GENERAL_60(60),
    HIGHWAY_90(90),
}

enum class RoadMode(val fare: FareRoadMode, val safety: SafetyRoadMode) {
    GENERAL(FareRoadMode.GENERAL, SafetyRoadMode.GENERAL_60),
    // Legacy database value. New samples/UI use HIGHWAY2 for the single highway mode.
    HIGHWAY1(FareRoadMode.HIGHWAY, SafetyRoadMode.HIGHWAY_90),
    HIGHWAY2(FareRoadMode.HIGHWAY, SafetyRoadMode.HIGHWAY_90),
}

val selectableRoadModes = listOf(RoadMode.GENERAL, RoadMode.HIGHWAY2)

fun RoadMode.isHighwayForReport() = this == RoadMode.HIGHWAY1 || this == RoadMode.HIGHWAY2

data class SafetyRoadModeChange(
    val from: RoadMode,
    val to: RoadMode,
)
