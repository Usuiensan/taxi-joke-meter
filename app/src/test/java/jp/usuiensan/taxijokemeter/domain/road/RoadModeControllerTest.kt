package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadModeControllerTest {
    private val controller = RoadModeController()

    @Test fun shortPressCyclesHighwayModes() {
        assertEquals(RoadMode.HIGHWAY1, controller.transition(RoadMode.GENERAL, RoadModeAction.SHORT_PRESS).nextMode)
        assertEquals(RoadMode.HIGHWAY2, controller.transition(RoadMode.HIGHWAY1, RoadModeAction.SHORT_PRESS).nextMode)
        assertEquals(RoadMode.HIGHWAY1, controller.transition(RoadMode.HIGHWAY2, RoadModeAction.SHORT_PRESS).nextMode)
    }

    @Test fun longPressReturnsHighwayModesToGeneral() {
        assertEquals(RoadMode.GENERAL, controller.transition(RoadMode.HIGHWAY1, RoadModeAction.LONG_PRESS).nextMode)
        assertEquals(RoadMode.GENERAL, controller.transition(RoadMode.HIGHWAY2, RoadModeAction.LONG_PRESS).nextMode)
    }

    @Test fun generalLongPressIsNoOpWithoutSafetyChange() {
        assertEquals(
            RoadModeTransition(RoadMode.GENERAL, null),
            controller.transition(RoadMode.GENERAL, RoadModeAction.LONG_PRESS),
        )
    }

    @Test fun modeChangeCarriesSafetyRoadModeChange() {
        assertEquals(
            SafetyRoadModeChange(RoadMode.GENERAL, RoadMode.HIGHWAY1),
            controller.transition(RoadMode.GENERAL, RoadModeAction.SHORT_PRESS).safetyRoadModeChange,
        )
    }
}
