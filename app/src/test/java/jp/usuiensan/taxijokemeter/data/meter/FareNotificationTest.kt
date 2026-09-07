package jp.usuiensan.taxijokemeter.data.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class FareNotificationTest {
    @Test fun formatsFareAsYenWithGrouping() {
        assertEquals("¥680", fareNotificationText(680))
        assertEquals("¥1,240", fareNotificationText(1_240))
        assertEquals("¥12,340", fareNotificationText(12_340))
    }
}
