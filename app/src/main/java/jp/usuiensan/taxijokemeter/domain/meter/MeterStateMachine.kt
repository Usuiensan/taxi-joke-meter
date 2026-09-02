package jp.usuiensan.taxijokemeter.domain.meter

import jp.usuiensan.taxijokemeter.domain.model.RoadMode

enum class DutyLifecycle { BEFORE_DEPARTURE, ACTIVE, ENDED }
enum class MeterState { EMPTY, OCCUPIED, PAYMENT, DEADHEAD }
enum class DutyActivity { WORKING, BREAK }
enum class MeterAction { DEPART, EMPTY, OCCUPIED, PAYMENT, DEADHEAD, BREAK, RESUME, ARRIVE }
enum class MeterMenu { NONE, EMPTY_MENU }

data class MeterRuntimeState(
    val dutyLifecycle: DutyLifecycle = DutyLifecycle.BEFORE_DEPARTURE,
    val meterState: MeterState = MeterState.EMPTY,
    val dutyActivity: DutyActivity = DutyActivity.WORKING,
    val roadMode: RoadMode = RoadMode.GENERAL,
    val menu: MeterMenu = MeterMenu.NONE,
)

class MeterStateMachine(initial: MeterRuntimeState = MeterRuntimeState()) {
    var state: MeterRuntimeState = initial
        private set

    fun dispatch(action: MeterAction): MeterRuntimeState {
        state = when (action) {
            MeterAction.DEPART -> state.copy(dutyLifecycle = DutyLifecycle.ACTIVE, meterState = MeterState.EMPTY, menu = MeterMenu.NONE)
            MeterAction.EMPTY -> when (state.meterState) {
                MeterState.EMPTY -> state.copy(menu = MeterMenu.EMPTY_MENU)
                MeterState.OCCUPIED, MeterState.PAYMENT -> state.copy(meterState = MeterState.EMPTY, menu = MeterMenu.NONE)
                MeterState.DEADHEAD -> state.copy(meterState = MeterState.EMPTY, menu = MeterMenu.NONE)
            }
            MeterAction.OCCUPIED -> state.copy(meterState = MeterState.OCCUPIED, menu = MeterMenu.NONE)
            MeterAction.PAYMENT -> state.copy(meterState = MeterState.PAYMENT, menu = MeterMenu.NONE)
            MeterAction.DEADHEAD -> state.copy(meterState = MeterState.DEADHEAD, menu = MeterMenu.NONE)
            MeterAction.BREAK -> state.copy(dutyActivity = DutyActivity.BREAK, menu = MeterMenu.NONE)
            MeterAction.RESUME -> state.copy(dutyActivity = DutyActivity.WORKING, menu = MeterMenu.NONE)
            MeterAction.ARRIVE -> state.copy(dutyLifecycle = DutyLifecycle.ENDED, menu = MeterMenu.NONE)
        }
        return state
    }

    fun setRoadMode(roadMode: RoadMode): MeterRuntimeState {
        state = state.copy(roadMode = roadMode)
        return state
    }
}
