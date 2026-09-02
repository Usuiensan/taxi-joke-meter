package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange

enum class RoadModeAction { SHORT_PRESS, LONG_PRESS }

data class RoadModeTransition(
    val nextMode: RoadMode,
    val safetyRoadModeChange: SafetyRoadModeChange?,
)

class RoadModeController {
    fun transition(current: RoadMode, action: RoadModeAction): RoadModeTransition {
        val next = when (action) {
            RoadModeAction.LONG_PRESS -> RoadMode.GENERAL
            RoadModeAction.SHORT_PRESS -> when (current) {
                RoadMode.GENERAL -> RoadMode.HIGHWAY1
                RoadMode.HIGHWAY1 -> RoadMode.HIGHWAY2
                RoadMode.HIGHWAY2 -> RoadMode.HIGHWAY1
            }
        }
        return RoadModeTransition(next, if (next == current) null else SafetyRoadModeChange(current, next))
    }
}
