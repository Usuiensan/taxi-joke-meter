package jp.usuiensan.taxijokemeter.domain.model

enum class FareRoadMode(val isHighway: Boolean) { GENERAL(false), HIGHWAY(true) }

enum class SafetyRoadMode(val limitKmh: Int) {
    GENERAL_60(60),
    URBAN_EXPRESSWAY_80(80),
    MOTORWAY_100(100),
}

enum class RoadMode(val fare: FareRoadMode, val safety: SafetyRoadMode) {
    GENERAL(FareRoadMode.GENERAL, SafetyRoadMode.GENERAL_60),
    HIGHWAY1(FareRoadMode.HIGHWAY, SafetyRoadMode.URBAN_EXPRESSWAY_80),
    HIGHWAY2(FareRoadMode.HIGHWAY, SafetyRoadMode.MOTORWAY_100),
}

data class SafetyRoadModeChange(
    val from: RoadMode,
    val to: RoadMode,
)
