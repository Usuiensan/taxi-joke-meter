package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.content.pm.PackageManager
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMapper
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterStateMachine
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.Trip
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.road.RoadModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class MeterController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val measurement = DutyMeasurementController(context)
    private val audioCuePlayer = AudioCuePlayer({ cue -> MeterAudioResources.resourceId(context, cue) }, MediaPlayerAudioCueSink(context))
    private val machine = MeterStateMachine()
    private val roadModeController = RoadModeController()
    private var sessionId: String? = null
    private var trip: Trip? = null
    private var fareRule = FareRuleStore(context).load()

    fun ensureMeasurementRunning() {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fareRule = FareRuleStore(context).load()
        sessionId = measurement.ensureMeasurementRunning(fareRule)
        MeterRuntimeStore.update { it.copy(dutySessionId = sessionId) }
    }

    fun dispatch(action: MeterAction) {
        if (action == MeterAction.DEPART && context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val before = machine.state
        if (action == MeterAction.PAYMENT && !shouldProcessPayment(before.meterState)) return
        if (action == MeterAction.TOTAL && !shouldFinalizePayment(before.meterState)) return
        if (action == MeterAction.EMPTY && before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT) return
        val after = machine.dispatch(action)
        meterAudioCueForTransition(before.meterState, after.meterState)?.let(audioCuePlayer::enqueue)
        when (action) {
            MeterAction.DEPART -> {
                fareRule = FareRuleStore(context).load()
                val id = measurement.ensureMeasurementRunning(fareRule) ?: return
                sessionId = id
                MeterRuntimeStore.update { it.copy(dutyLifecycle = after.dutyLifecycle, meterState = after.meterState, menu = after.menu, dutySessionId = id) }
                event(DutyEventType.DEPARTURE)
            }
            MeterAction.OCCUPIED -> {
                if (before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY || before.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.TOTAL) {
                    fareRule = FareRuleStore(context).load()
                    val now = Instant.now()
                    MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, tripStartedAt = now, fareYen = fareRule.initialFareYen, actualDistanceMeters = 0, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, currentTripId = null, currentTripNumber = null, chargeProgress = ChargeProgressMapper.initial(fareRule, it.latestSpeedMps)) }
                    sessionId?.let { session -> scope.launch {
                        val db = DutyDatabase.build(context)
                        try {
                            val started = DutyRecordRepository(db).startTrip(session, now, fareRule)
                            trip = started
                            MeterRuntimeStore.update { it.copy(currentTripId = started.id, currentTripNumber = started.tripNumber) }
                            measurement.beginFareMeasurement(fareRule)
                            event(DutyEventType.PICKUP)
                        } finally { db.close() }
                    } }
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
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, fareYen = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.fareYen, actualDistanceMeters = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.actualDistanceMeters, chargeableDistanceMeters = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.chargeableDistanceMeters, lowSpeedSeconds = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) 0 else it.lowSpeedSeconds, chargeProgress = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) null else it.chargeProgress, tripStartedAt = null, currentTripId = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) null else it.currentTripId, currentTripNumber = if (after.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY) null else it.currentTripNumber) }
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
        val transition = roadModeController.select(machine.state.roadMode, mode)
        machine.setRoadMode(transition.nextMode)
        MeterRuntimeStore.update { it.copy(roadMode = transition.nextMode) }
        val occupancy = if (machine.state.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) OccupancyState.OCCUPIED else OccupancyState.EMPTY
        measurement.updateState(mode, occupancy)
    }

    private fun finishTrip(status: TripStatus, revenueIncluded: Boolean) {
        val current = trip ?: return
        val value = MeterRuntimeStore.snapshot.value
        trip = current.copy(dropoffAt = Instant.now(), operatingDistanceMeters = value.chargeableDistanceMeters, fareYen = value.fareYen, status = status, lowSpeedSeconds = value.lowSpeedSeconds, revenueIncluded = revenueIncluded, actualDistanceMeters = value.actualDistanceMeters, chargeableDistanceMeters = value.chargeableDistanceMeters, meteredFareYen = value.fareYen, totalFareYen = value.fareYen)
        val session = sessionId ?: return
        trip?.let { completed -> scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).finishTrip(completed, status, revenueIncluded) } finally { db.close() } } }
        trip = null
    }

    private fun event(type: DutyEventType) { val session = sessionId ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).appendEvent(session, DutyEvent(UUID.randomUUID().toString(), type, Instant.now(), null, null)) } finally { db.close() } } }

    private fun endDuty() { val session = sessionId ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).markEnded(session, Instant.now()) } finally { db.close() } } }

}

internal fun meterAudioCueForTransition(before: jp.usuiensan.taxijokemeter.domain.meter.MeterState, after: jp.usuiensan.taxijokemeter.domain.meter.MeterState) = when {
    before != jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED && after == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED -> MeterAudioCue.OCCUPIED
    before != jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT && after == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT -> MeterAudioCue.PAYMENT
    else -> null
}

internal fun shouldProcessPayment(state: jp.usuiensan.taxijokemeter.domain.meter.MeterState) =
    state == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED

internal fun shouldFinalizePayment(state: jp.usuiensan.taxijokemeter.domain.meter.MeterState) =
    state == jp.usuiensan.taxijokemeter.domain.meter.MeterState.PAYMENT
