package jp.usuiensan.taxijokemeter.ui.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import jp.usuiensan.taxijokemeter.data.meter.SafetyWarning
import jp.usuiensan.taxijokemeter.data.meter.safetyWarningText
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
        assertEquals(listOf("回送", "レポート", "再発行", "デバッグ", "戻る"), softKeySlots(MeterState.EMPTY, true))
    }

    @Test fun roadModeButtonsMapToExistingRoadModes() {
        assertEquals(listOf(RoadMode.GENERAL, RoadMode.HIGHWAY1, RoadMode.HIGHWAY2), roadModeButtonModes())
        assertEquals("一般", roadModeLabel(RoadMode.GENERAL))
        assertEquals("専用道", roadModeLabel(RoadMode.HIGHWAY1))
        assertEquals("高速", roadModeLabel(RoadMode.HIGHWAY2))
    }

    @Test fun safetyWarningTextUsesStartedValuesAndExpiresAfterThreeSeconds() {
        assertEquals("⚠ 急加速 (0.32G)", safetyWarningText(SafetyEventType.HARD_ACCELERATION, 3.138128, null))
        assertEquals("⚠ 急減速 (0.41G)", safetyWarningText(SafetyEventType.HARD_DECELERATION, -4.021, null))
        assertEquals("⚠ 速度超過 (現在 68 km/h)", safetyWarningText(SafetyEventType.OVERSPEED, null, 68))
        val warning = SafetyWarning("test", MeterState.OCCUPIED, 1_000L, 4_000L, "text")
        assertTrue(warning.isVisibleAt(3_999L))
        assertFalse(warning.isVisibleAt(4_000L))
        assertFalse(showsSafetyWarning(MeterState.PAYMENT))
        assertFalse(showsSafetyWarning(MeterState.TOTAL))
    }

    @Test fun chargeRailUsesOneKnobCenterForProgressAndIcon() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val geometry = chargeRailGeometry(12f, 400f, fraction)
            assertEquals(12f + 400f * fraction, geometry.knobCenterX, 0f)
            assertEquals(geometry.knobCenterX, geometry.coloredProgressEndX, 0f)
            assertEquals(geometry.knobCenterX, geometry.whiteRemainderStartX, 0f)
        }
        assertEquals(12f, chargeRailGeometry(12f, 400f, -1f).knobCenterX, 0f)
        assertEquals(412f, chargeRailGeometry(12f, 400f, 2f).knobCenterX, 0f)
    }

    @Test fun fareDisplaySizingKeepsCurrencySmallerAndAdaptsToDigits() {
        val shortOccupied = fareDisplaySizing(MeterState.OCCUPIED, 280f, "20")
        val longOccupied = fareDisplaySizing(MeterState.OCCUPIED, 280f, "12,340")
        val payment = fareDisplaySizing(MeterState.PAYMENT, 220f, "2,700")
        assertTrue(shortOccupied.numberSp in 120f..150f)
        assertTrue(longOccupied.numberSp < shortOccupied.numberSp)
        assertEquals(shortOccupied.numberSp * 0.4f, shortOccupied.unitSp, 0.01f)
        assertTrue(payment.numberSp >= 96f)
        assertTrue(payment.numberSp < shortOccupied.numberSp)
    }
}
