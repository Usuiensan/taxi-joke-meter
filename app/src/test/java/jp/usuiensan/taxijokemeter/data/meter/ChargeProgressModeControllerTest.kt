package jp.usuiensan.taxijokemeter.data.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeProgressModeControllerTest {
    @Test fun lowSpeedMustRemainFiveSecondsBeforeTimeMode() {
        val c = ChargeProgressModeController(10.0)
        assertEquals(ChargeProgressMode.DISTANCE, c.update(2.0, 0))
        assertEquals(ChargeProgressMode.DISTANCE, c.update(2.0, 4_999))
        assertEquals(ChargeProgressMode.TIME, c.update(2.0, 5_000))
    }

    @Test fun speedMustRemainAboveHysteresisThresholdThreeSecondsBeforeDistanceMode() {
        val c = ChargeProgressModeController(10.0).also { it.reset(ChargeProgressMode.TIME) }
        assertEquals(ChargeProgressMode.TIME, c.update(3.4, 0))
        assertEquals(ChargeProgressMode.TIME, c.update(3.4, 2_999))
        assertEquals(ChargeProgressMode.DISTANCE, c.update(3.4, 3_000))
    }

    @Test fun missingSpeedKeepsLastStableMode() {
        val c = ChargeProgressModeController(10.0).also { it.reset(ChargeProgressMode.TIME) }
        assertEquals(ChargeProgressMode.TIME, c.update(null, 100_000))
    }
}
