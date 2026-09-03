package jp.usuiensan.taxijokemeter.domain.meter

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterStateMachineTest {
    @Test fun paymentCanResumeOrFinalizeButCannotBecomeEmpty() {
        val machine = MeterStateMachine()
        assertEquals(DutyLifecycle.BEFORE_DEPARTURE, machine.state.dutyLifecycle)
        machine.dispatch(MeterAction.DEPART)
        assertEquals(DutyLifecycle.ACTIVE, machine.state.dutyLifecycle)
        machine.dispatch(MeterAction.OCCUPIED)
        assertEquals(MeterState.OCCUPIED, machine.state.meterState)
        machine.dispatch(MeterAction.PAYMENT)
        assertEquals(MeterState.PAYMENT, machine.state.meterState)
        machine.dispatch(MeterAction.EMPTY)
        assertEquals(MeterState.PAYMENT, machine.state.meterState)
        machine.dispatch(MeterAction.OCCUPIED)
        assertEquals(MeterState.OCCUPIED, machine.state.meterState)
        machine.dispatch(MeterAction.PAYMENT)
        machine.dispatch(MeterAction.TOTAL)
        assertEquals(MeterState.TOTAL, machine.state.meterState)
    }

    @Test fun totalCanBecomeEmptyOrStartANewTrip() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE, MeterState.TOTAL))

        machine.dispatch(MeterAction.OCCUPIED)
        assertEquals(MeterState.OCCUPIED, machine.state.meterState)
        machine.dispatch(MeterAction.PAYMENT)
        assertEquals(MeterState.PAYMENT, machine.state.meterState)
        machine.dispatch(MeterAction.TOTAL)
        machine.dispatch(MeterAction.EMPTY)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
    }

    @Test fun occupiedCancellationStillReturnsToEmpty() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE, MeterState.OCCUPIED))

        machine.dispatch(MeterAction.EMPTY)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
    }

    @Test fun emptyPressOpensMenuAndDeadheadRequiresRelease() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE))
        machine.dispatch(MeterAction.MENU)
        assertEquals(MeterMenu.EMPTY_MENU, machine.state.menu)
        machine.dispatch(MeterAction.DEADHEAD)
        assertEquals(MeterState.DEADHEAD, machine.state.meterState)
        machine.dispatch(MeterAction.EMPTY)
        machine.dispatch(MeterAction.OCCUPIED)
        machine.dispatch(MeterAction.PAYMENT)
        machine.dispatch(MeterAction.TOTAL)
        assertEquals(MeterState.DEADHEAD, machine.state.meterState)
        machine.dispatch(MeterAction.RELEASE)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(DutyLifecycle.ACTIVE, machine.state.dutyLifecycle)
    }

    @Test fun paymentFromEmptyKeepsStateUnchanged() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE))

        val before = machine.state
        machine.dispatch(MeterAction.PAYMENT)

        assertEquals(before, machine.state)
    }

    @Test fun menuKeepsEmptyStateAndBackClosesMenu() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE))

        machine.dispatch(MeterAction.MENU)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(MeterMenu.EMPTY_MENU, machine.state.menu)

        machine.dispatch(MeterAction.BACK)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(MeterMenu.NONE, machine.state.menu)
    }

    @Test fun emptyActionFromTotalReturnsToNormalEmptyWithoutOpeningMenu() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE, MeterState.TOTAL))

        machine.dispatch(MeterAction.EMPTY)

        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(MeterMenu.NONE, machine.state.menu)
    }

    @Test fun menuActionOnlyOpensFromEmpty() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE, MeterState.OCCUPIED))

        machine.dispatch(MeterAction.MENU)

        assertEquals(MeterState.OCCUPIED, machine.state.meterState)
        assertEquals(MeterMenu.NONE, machine.state.menu)
    }

    @Test fun activityAndRoadModeAreSeparateAxes() {
        val machine = MeterStateMachine(MeterRuntimeState(DutyLifecycle.ACTIVE))
        machine.dispatch(MeterAction.BREAK)
        machine.setRoadMode(RoadMode.HIGHWAY2)
        assertEquals(DutyActivity.BREAK, machine.state.dutyActivity)
        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(RoadMode.HIGHWAY2, machine.state.roadMode)
        machine.dispatch(MeterAction.RESUME)
        assertEquals(DutyActivity.WORKING, machine.state.dutyActivity)
    }
}
