package jp.usuiensan.taxijokemeter.ui.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState
import jp.usuiensan.taxijokemeter.domain.meter.MeterStateMachine
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import jp.usuiensan.taxijokemeter.data.meter.SafetyWarning
import jp.usuiensan.taxijokemeter.data.meter.safetyWarningText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MeterPresentationTest {
    @Test
    fun fareAmountFormatsRequiredDisplaySizes() {
        assertEquals(FareAmountParts("12", "円"), formatFareAmount(12))
        assertEquals(FareAmountParts("500", "円"), formatFareAmount(500))
        assertEquals(FareAmountParts("2,700", "円"), formatFareAmount(2_700))
        assertEquals(FareAmountParts("14,850", "円"), formatFareAmount(14_850))
    }

    @Test
    fun emptyShowsTwoDirectMenuRowsAndNoChargeRail() {
        assertEquals(
            listOf(
                listOf("休憩", "迎車", "予約", "回送", null),
                listOf("レポート", "再発行", "運賃制", "デバッグ", null),
            ),
            emptyMenuRows(),
        )
        assertFalse(showsChargeProgressRail(MeterState.EMPTY))
    }

    @Test
    fun occupiedShowsPrimaryOperationSoftKeysAndChargeRail() {
        assertEquals(listOf("人員", "経由地", "迎車", "割増", "割引"), softKeyLabels(MeterState.OCCUPIED))
        assertTrue(showsChargeProgressRail(MeterState.OCCUPIED))
    }

    @Test
    fun paymentShowsPaymentSoftKeys() {
        assertEquals(emptyList<String>(), softKeyLabels(MeterState.PAYMENT))
    }

    @Test fun totalShowsPaymentSoftKeysAndSummaryTitle() {
        assertEquals(emptyList<String>(), softKeyLabels(MeterState.TOTAL))
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
        assertEquals("休憩", stateLabel(MeterState.REST))
        assertEquals("迎車", stateLabel(MeterState.PICKUP))
    }

    @Test fun emptyButtonKeepsEmptyAndLeavesOtherTransitionsUnchanged() {
        assertEquals(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.EMPTY, emptyButtonAction(MeterState.EMPTY))
        assertEquals(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.EMPTY, emptyButtonAction(MeterState.TOTAL))
    }

    @Test fun pickupEmptyKeyReturnsToEmptyMenu() {
        val machine = MeterStateMachine(MeterRuntimeState(meterState = MeterState.PICKUP, menu = MeterMenu.NONE))

        assertEquals(MeterAction.EMPTY, emptyButtonAction(MeterState.PICKUP))
        machine.dispatch(emptyButtonAction(MeterState.PICKUP))

        assertEquals(MeterState.EMPTY, machine.state.meterState)
        assertEquals(MeterMenu.EMPTY_MENU, machine.state.menu)
    }

    @Test fun restAndDeadheadUseTheirReleaseSoftKeySlots() {
        assertEquals(listOf(null, null, null, null, "解除"), softKeySlots(MeterState.REST))
        assertEquals(listOf(null, null, null, null, "解除"), softKeySlots(MeterState.DEADHEAD))
        assertEquals(MeterAction.REST_RELEASE, softKeyAction(MeterState.REST, "解除"))
        assertEquals(MeterAction.RELEASE, softKeyAction(MeterState.DEADHEAD, "解除"))
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
        assertEquals(listOf("休憩", "迎車", "予約", "回送", null, "レポート", "再発行", "運賃制", "デバッグ", null), softKeySlots(MeterState.EMPTY))
        assertEquals(listOf("人員", "経由地", "迎車", "割増", "割引"), softKeySlots(MeterState.OCCUPIED))
        assertEquals(listOf(null, null, null, null, null), softKeySlots(MeterState.PAYMENT))
        assertEquals(listOf(null, null, null, null, null), softKeySlots(MeterState.TOTAL))
        assertEquals(listOf(null, null, null, null, "解除"), softKeySlots(MeterState.DEADHEAD))
    }

    @Test fun emptyMenuUsesFixedMenuSoftKeySlots() {
        assertEquals(listOf("休憩", "迎車", "予約", "回送", null, "レポート", "再発行", "運賃制", "デバッグ", null), softKeySlots(MeterState.EMPTY, true))
    }

    @Test fun emptyPickupSoftKeyDispatchesPickupAction() {
        assertEquals(MeterAction.PICKUP, emptySoftKeyAction("迎車"))
        val machine = MeterStateMachine(MeterRuntimeState(meterState = MeterState.EMPTY))
        assertEquals(MeterState.PICKUP, machine.dispatch(emptySoftKeyAction("迎車")!!).meterState)
    }

    @Test fun restDurationUsesHoursAndMinutes() {
        val start = Instant.parse("2026-09-04T00:00:00Z")
        assertEquals("0時間00分", formatRestDuration(start, start))
        assertEquals("1時間05分", formatRestDuration(start, start.plusSeconds(65 * 60L)))
    }

    @Test fun reservationButtonReflectsPendingState() {
        assertEquals("予約", reservationLabel(false))
        assertEquals("予約 ✓", reservationLabel(true))
    }

    @Test fun rightKeysUseStateSpecificEnablementAndKeepRoadModesUsable() {
        assertEquals(listOf(false, true, false, false, true, true, true), rightKeyEnabled(MeterState.EMPTY))
        assertEquals(listOf(true, false, true, false, true, true, true), rightKeyEnabled(MeterState.OCCUPIED))
        assertEquals(listOf(false, true, false, true, true, true, true), rightKeyEnabled(MeterState.PAYMENT))
        assertEquals(listOf(true, true, false, false, true, true, true), rightKeyEnabled(MeterState.PICKUP))
        assertEquals(listOf(false, true, false, false, true, true, true), rightKeyEnabled(MeterState.REST))
        assertEquals(listOf(true, true, false, false, true, true, true), rightKeyEnabled(MeterState.TOTAL))
        assertEquals(listOf(false, false, false, false, true, true, true), rightKeyEnabled(MeterState.DEADHEAD))
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
        assertTrue(showsSafetyWarning(MeterState.PAYMENT))
        assertTrue(showsSafetyWarning(MeterState.TOTAL))
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
