package jp.usuiensan.taxijokemeter.ui.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterPresentationTest {
    @Test
    fun fareAmountSeparatesLargeNumberAndSmallYenUnit() {
        assertEquals(FareAmountParts("2,700", "円"), formatFareAmount(2_700))
        assertEquals(FareAmountParts("114", "円"), formatFareAmount(114))
    }

    @Test
    fun emptyDoesNotShowOccupiedSoftKeysOrChargeRail() {
        assertTrue(softKeyLabels(MeterState.EMPTY).isEmpty())
        assertFalse(showsChargeProgressRail(MeterState.EMPTY))
    }

    @Test
    fun occupiedShowsOccupiedSoftKeysAndChargeRail() {
        assertEquals(listOf("早朝", "高割", "障割", "予約", "経由地"), softKeyLabels(MeterState.OCCUPIED))
        assertTrue(showsChargeProgressRail(MeterState.OCCUPIED))
    }

    @Test
    fun paymentShowsPaymentSoftKeys() {
        assertEquals(listOf("明細", "置換", "立替", "チケット", "定額"), softKeyLabels(MeterState.PAYMENT))
    }

    @Test fun totalShowsPaymentSoftKeysAndSummaryTitle() {
        assertEquals(listOf("明細", "置換", "立替", "チケット", "定額"), softKeyLabels(MeterState.TOTAL))
        assertEquals("支払", paymentSummaryTitle(MeterState.PAYMENT))
        assertEquals("合計", paymentSummaryTitle(MeterState.TOTAL))
        assertEquals(listOf("運賃料金計" to "2,700円", "通行料他" to "0円"), paymentSummaryRows(2_700))
    }
}
