package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.content.pm.PackageManager
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterStateMachine
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.Trip
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class MeterController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val measurement = DutyMeasurementController(context)
    private val machine = MeterStateMachine()
    private var sessionId: String? = null
    private var trip: Trip? = null
    private var fareRule = FareRuleStore(context).load()

    fun dispatch(action: MeterAction) {
        if (action == MeterAction.DEPART && context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val before = machine.state
        if (action == MeterAction.PAYMENT && !shouldProcessPayment(before.meterState)) return
        if (action == MeterAction.TOTAL && !shouldFinalizePayment(before.meterState)) return
        if (action == MeterAction.EMPTY && before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT) return
        val after = machine.dispatch(action)
        when (action) {
            MeterAction.DEPART -> {
                fareRule = FareRuleStore(context).load()
                val id = measurement.start(after.roadMode, jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState.EMPTY, fareRule)
                sessionId = id
                MeterRuntimeStore.update { it.copy(dutyLifecycle = after.dutyLifecycle, meterState = after.meterState, dutySessionId = id) }
                event(DutyEventType.DEPARTURE)
            }
            MeterAction.OCCUPIED -> {
                if (before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY || before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.TOTAL) {
                    val now = Instant.now()
                    trip = Trip(UUID.randomUUID().toString(), now, null, null, null, 0, 0, 0, 0, jp.usuiensan.taxijokemeter.domain.model.PaymentMethod.UNKNOWN, null)
                    MeterRuntimeStore.update { it.copy(meterState = after.meterState, tripStartedAt = now, fareYen = fareRule.initialFareYen, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, chargeProgress = null) }
                    measurement.beginFareMeasurement(); event(DutyEventType.PICKUP)
                } else if (before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT) {
                    MeterRuntimeStore.update { it.copy(meterState = after.meterState) }
                    measurement.updateState(before.roadMode, OccupancyState.OCCUPIED)
                }
            }
            MeterAction.PAYMENT -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState) }
                measurement.updateState(before.roadMode, OccupancyState.EMPTY)
            }
            MeterAction.TOTAL -> {
                measurement.endFareMeasurement()
                MeterRuntimeStore.update { it.copy(meterState = after.meterState) }
                event(DutyEventType.DROPOFF)
                finishTrip(TripStatus.COMPLETED, true)
            }
            MeterAction.EMPTY -> {
                if (before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) finishTrip(TripStatus.VOIDED, false)
                if (before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) measurement.endFareMeasurement()
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, fareYen = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.fareYen, chargeableDistanceMeters = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.chargeableDistanceMeters, lowSpeedSeconds = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.lowSpeedSeconds, chargeProgress = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) null else it.chargeProgress, tripStartedAt = null) }
            }
            MeterAction.DEADHEAD -> { MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }; event(DutyEventType.DEADHEAD_START) }
            MeterAction.RELEASE -> { MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }; event(DutyEventType.DEADHEAD_END) }
            MeterAction.BREAK -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_START) }
            MeterAction.RESUME -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_END) }
            MeterAction.ARRIVE -> { measurement.stop(); MeterRuntimeStore.update { it.copy(dutyLifecycle = after.dutyLifecycle, menu = after.menu) }; event(DutyEventType.ARRIVAL); endDuty() }
            MeterAction.MENU, MeterAction.BACK -> MeterRuntimeStore.update { it.copy(menu = after.menu) }
        }
    }

    fun setRoadMode(mode: RoadMode) {
        machine.setRoadMode(mode)
        MeterRuntimeStore.update { it.copy(roadMode = mode) }
        val occupancy = if (machine.state.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) OccupancyState.OCCUPIED else OccupancyState.EMPTY
        measurement.updateState(mode, occupancy)
    }

    private fun finishTrip(status: TripStatus, revenueIncluded: Boolean) {
        val current = trip ?: return
        val value = MeterRuntimeStore.snapshot.value
        trip = current.copy(dropoffAt = Instant.now(), operatingDistanceMeters = value.chargeableDistanceMeters, fareYen = value.fareYen, status = status, lowSpeedSeconds = value.lowSpeedSeconds, revenueIncluded = revenueIncluded)
        val session = sessionId ?: return
        trip?.let { completed -> scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).appendTrip(session, completed) } finally { db.close() } } }
        trip = null
    }

    private fun event(type: DutyEventType) { val session = sessionId ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).appendEvent(session, DutyEvent(UUID.randomUUID().toString(), type, Instant.now(), null, null)) } finally { db.close() } } }

    private fun endDuty() { val session = sessionId ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).markEnded(session, Instant.now()) } finally { db.close() } } }

}

internal fun shouldProcessPayment(state: jp.usuiensan.taxijokemeter.domain.meter.MeterState) =
    state == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED

internal fun shouldFinalizePayment(state: jp.usuiensan.taxijokemeter.domain.meter.MeterState) =
    state == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT
