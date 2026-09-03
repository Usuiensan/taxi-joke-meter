package jp.usuiensan.taxijokemeter.ui.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterPresentationTest {
    @Test
    fun fareAmountFormatsRequiredDisplaySizes() {
        assertEquals(FareAmountParts("12", "円"), formatFareAmount(12))
        assertEquals(FareAmountParts("500", "円"), formatFareAmount(500))
        assertEquals(FareAmountParts("2,700", "円"), formatFareAmount(2_700))
        assertEquals(FareAmountParts("14,850", "円"), formatFareAmount(14_850))
    }

    @Test
    fun emptyShowsOnlyEmptySoftKeysAndNoChargeRail() {
        assertEquals(listOf("累計", "運賃制", "高速"), softKeyLabels(MeterState.EMPTY))
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

    @Test fun stateLabelsMatchMeterStates() {
        assertEquals("空車", stateLabel(MeterState.EMPTY))
        assertEquals("賃走", stateLabel(MeterState.OCCUPIED))
        assertEquals("支払", stateLabel(MeterState.PAYMENT))
        assertEquals("合計", stateLabel(MeterState.TOTAL))
        assertEquals("回送", stateLabel(MeterState.DEADHEAD))
    }

    @Test fun emptyAndDeadheadUseTheirFixedSoftKeySlots() {
        assertEquals(listOf("累計", "運賃制", "高速"), softKeyLabels(MeterState.EMPTY))
        assertEquals(listOf("解除"), softKeyLabels(MeterState.DEADHEAD))
    }

    @Test fun dateTimeIsShownOnlyOnEmptyAndDeadheadSurfaces() {
        assertTrue(showsDateTime(MeterState.EMPTY, true))
        assertTrue(showsDateTime(MeterState.EMPTY, false))
        assertTrue(showsDateTime(MeterState.DEADHEAD, false))
        assertFalse(showsDateTime(MeterState.OCCUPIED, false))
        assertFalse(showsDateTime(MeterState.PAYMENT, false))
        assertFalse(showsDateTime(MeterState.TOTAL, false))
    }

    @Test fun everyMeterStateUsesFiveSoftKeySlots() {
        assertEquals(listOf("累計", "運賃制", null, null, "高速"), softKeySlots(MeterState.EMPTY))
        assertEquals(listOf("早朝", "高割", "障割", "予約", "経由地"), softKeySlots(MeterState.OCCUPIED))
        assertEquals(listOf("明細", "置換", "立替", "チケット", "定額"), softKeySlots(MeterState.PAYMENT))
        assertEquals(listOf("明細", "置換", "立替", "チケット", "定額"), softKeySlots(MeterState.TOTAL))
        assertEquals(listOf(null, null, null, null, "解除"), softKeySlots(MeterState.DEADHEAD))
    }

    @Test fun emptyMenuUsesFixedMenuSoftKeySlots() {
        assertEquals(listOf("回送", "レポート", "再発行", null, "戻る"), softKeySlots(MeterState.EMPTY, true))
    }
}
