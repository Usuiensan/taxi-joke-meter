package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange

enum class RoadModeAction { SHORT_PRESS, LONG_PRESS }

data class RoadModeTransition(
    val nextMode: RoadMode,
    val safetyRoadModeChange: SafetyRoadModeChange?,
)

class RoadModeController {
    fun select(current: RoadMode, target: RoadMode): RoadModeTransition {
        if (target == RoadMode.HIGHWAY1) return RoadModeTransition(current, null)
        return RoadModeTransition(target, if (current == target) null else SafetyRoadModeChange(current, target))
    }

    fun transition(current: RoadMode, action: RoadModeAction): RoadModeTransition {
        val next = when (action) {
            RoadModeAction.LONG_PRESS -> RoadMode.GENERAL
            RoadModeAction.SHORT_PRESS -> when (current) {
                RoadMode.GENERAL -> RoadMode.HIGHWAY2
                RoadMode.HIGHWAY1 -> RoadMode.HIGHWAY2
                RoadMode.HIGHWAY2 -> RoadMode.GENERAL
            }
        }
        return RoadModeTransition(next, if (next == current) null else SafetyRoadModeChange(current, next))
    }
}
