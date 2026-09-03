package jp.usuiensan.taxijokemeter.ui.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class MeterPresentationTest {
    @Test
    fun fareAmountSeparatesLargeNumberAndSmallYenUnit() {
        assertEquals(FareAmountParts("2,700", "円"), formatFareAmount(2_700))
        assertEquals(FareAmountParts("114", "円"), formatFareAmount(114))
    }
}
