package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.content.pm.PackageManager
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettingsStore
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.meter.MeterStateMachine
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.LocationPrecision
import jp.usuiensan.taxijokemeter.domain.model.LocationSource
import jp.usuiensan.taxijokemeter.domain.model.RecordedPlace
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange
import jp.usuiensan.taxijokemeter.domain.model.Trip
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.road.RoadModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class MeterController(private val context: Context) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
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
        if (machine.state.meterState == MeterState.REST && MeterRuntimeStore.snapshot.value.meterState == MeterState.EMPTY && MeterRuntimeStore.snapshot.value.restIntervalId == null) machine.dispatch(MeterAction.REST_RELEASE)
        val before = machine.state
        if (action == MeterAction.PAYMENT && !shouldProcessPayment(before.meterState)) return
        if (action == MeterAction.TOTAL && !shouldFinalizePayment(before.meterState)) return
        if (action == MeterAction.EMPTY && before.meterState == MeterState.PAYMENT) return
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
            MeterAction.OCCUPIED -> if (startsNewTrip(before.meterState)) startTrip(before.meterState, after)
            else if (before.meterState == MeterState.PAYMENT) {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                measurement.updateState(after.roadMode, OccupancyState.OCCUPIED)
            }
            MeterAction.PAYMENT -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                activeTripId()?.let { tripId -> scope.launch {
                    val db = DutyDatabase.build(context)
                    try { DutyRecordRepository(db).upsertDropoff(tripId, latestValidPlace(DrivingSampleRepository(db))) } finally { db.close() }
                } }
            }
            MeterAction.TOTAL -> {
                measurement.endFareMeasurement()
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                event(DutyEventType.DROPOFF)
                finishTrip(TripStatus.COMPLETED, true)
            }
            MeterAction.EMPTY -> {
                if (before.meterState == MeterState.OCCUPIED) {
                    finishTrip(TripStatus.VOIDED, false)
                    measurement.endFareMeasurement()
                }
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                MeterRuntimeStore.update { state -> state.copy(meterState = after.meterState, menu = after.menu, fareYen = if (after.meterState == MeterState.EMPTY) 0 else state.fareYen, actualDistanceMeters = if (after.meterState == MeterState.EMPTY) 0 else state.actualDistanceMeters, chargeableDistanceMeters = if (after.meterState == MeterState.EMPTY) 0 else state.chargeableDistanceMeters, lowSpeedSeconds = if (after.meterState == MeterState.EMPTY) 0 else state.lowSpeedSeconds, chargeProgress = if (after.meterState == MeterState.EMPTY) null else state.chargeProgress, tripStartedAt = null, currentTripId = if (after.meterState == MeterState.EMPTY) null else state.currentTripId, currentTripNumber = if (after.meterState == MeterState.EMPTY) null else state.currentTripNumber) }
            }
            MeterAction.PICKUP -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
            }
            MeterAction.REST -> startRest(after)
            MeterAction.REST_RELEASE -> finishRest("MANUAL", after)
            MeterAction.DEADHEAD -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                event(DutyEventType.DEADHEAD_START)
            }
            MeterAction.RELEASE -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                event(DutyEventType.DEADHEAD_END)
            }
            MeterAction.BREAK -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_START) }
            MeterAction.RESUME -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_END) }
            MeterAction.ARRIVE -> { measurement.stop(); MeterRuntimeStore.update { it.copy(dutyLifecycle = after.dutyLifecycle, menu = after.menu) }; event(DutyEventType.ARRIVAL); endDuty() }
            MeterAction.MENU, MeterAction.BACK -> MeterRuntimeStore.update { it.copy(menu = after.menu) }
        }
    }

    fun setRoadMode(mode: RoadMode) {
        val transition = roadModeController.select(machine.state.roadMode, mode)
        if (transition.safetyRoadModeChange == null) return
        machine.setRoadMode(transition.nextMode)
        MeterRuntimeStore.update { it.copy(roadMode = transition.nextMode) }
        measurement.updateState(transition.nextMode, measurementOccupancyState(machine.state.meterState))
        event(DutyEventType.SAFETY_ROAD_MODE_CHANGE, transition.safetyRoadModeChange)
    }

    fun toggleReservation() {
        if (machine.state.meterState in setOf(MeterState.EMPTY, MeterState.PICKUP)) MeterRuntimeStore.update { it.copy(pendingReservation = !it.pendingReservation) }
    }

    fun changePassengerCount(passengerCount: Int) {
        val count = clampPassengerCount(passengerCount)
        val tripId = activeTripId() ?: return
        MeterRuntimeStore.update { it.copy(passengerCount = count) }
        scope.launch {
            val db = DutyDatabase.build(context)
            try { DutyRecordRepository(db).updatePassengerCount(tripId, count) } finally { db.close() }
        }
    }

    fun addWaypoint() {
        if (machine.state.meterState != MeterState.OCCUPIED) return
        val tripId = activeTripId() ?: return
        scope.launch {
            val db = DutyDatabase.build(context)
            try {
                val place = latestValidPlace(DrivingSampleRepository(db)) ?: return@launch
                DutyRecordRepository(db).addWaypoint(tripId, place)
                MeterRuntimeStore.update { it.copy(waypointCount = it.waypointCount + 1) }
            } finally { db.close() }
        }
    }

    fun togglePickupFee() {
        if (machine.state.meterState !in setOf(MeterState.OCCUPIED, MeterState.PAYMENT)) return
        val amount = TripChargeSettingsStore(context).load().pickupFeeYen
        val tripId = activeTripId() ?: return
        if (amount <= 0) return
        MeterRuntimeStore.update { it.copy(pickupFeeApplied = true) }
        scope.launch {
            val db = DutyDatabase.build(context)
            try { DutyRecordRepository(db).addChargeIfAbsent(tripId, "PICKUP_FEE", amount, Instant.now()) } finally { db.close() }
        }
    }

    private fun startTrip(before: MeterState, after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        fareRule = FareRuleStore(context).load()
        val now = Instant.now()
        val settings = TripChargeSettingsStore(context).load()
        val pendingReservation = MeterRuntimeStore.snapshot.value.pendingReservation
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, tripStartedAt = now, fareYen = fareRule.initialFareYen, actualDistanceMeters = 0, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, currentTripId = null, currentTripNumber = null, chargeProgress = ChargeProgressMapper.initial(fareRule, it.latestSpeedMps), passengerCount = 1, waypointCount = 0, pickupFeeApplied = false, restIntervalId = if (before == MeterState.REST) it.restIntervalId else null, restDistanceMeters = if (before == MeterState.REST) it.restDistanceMeters else 0) }
        val session = currentSessionId() ?: return
        scope.launch {
            val db = DutyDatabase.build(context)
            try {
                val records = DutyRecordRepository(db)
                if (before == MeterState.REST) MeterRuntimeStore.snapshot.value.restIntervalId?.let { records.finishRestInterval(it, now, "OCCUPIED_STARTED") }
                val started = records.startTrip(session, now, fareRule)
                trip = started
                records.savePickupPlace(started.id, latestValidPlace(DrivingSampleRepository(db)))
                val charges = tripStartChargeTypes(before, pendingReservation, settings.pickupFeeYen, settings.reservationFeeYen)
                charges.forEach { type -> records.addChargeIfAbsent(started.id, type, if (type == "PICKUP_FEE") settings.pickupFeeYen else settings.reservationFeeYen, now) }
                records.appendEvent(session, DutyEvent(UUID.randomUUID().toString(), DutyEventType.PICKUP, now, null, null))
                MeterRuntimeStore.update { it.copy(currentTripId = started.id, currentTripNumber = started.tripNumber, pendingReservation = if (pendingReservation) false else it.pendingReservation, pickupFeeApplied = "PICKUP_FEE" in charges, restIntervalId = null, restDistanceMeters = 0) }
                measurement.updateState(machine.state.roadMode, OccupancyState.OCCUPIED)
                measurement.beginFareMeasurement(fareRule)
            } finally { db.close() }
        }
    }

    private fun startRest(after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, restDistanceMeters = 0, restIntervalId = null) }
        measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
        val session = currentSessionId() ?: return
        scope.launch {
            val db = DutyDatabase.build(context)
            try {
                val id = DutyRecordRepository(db).startRestInterval(session, Instant.now(), latestValidPlace(DrivingSampleRepository(db)))
                MeterRuntimeStore.update { if (it.restIntervalId == null) it.copy(restIntervalId = id) else it }
            } finally { db.close() }
        }
    }

    private fun finishRest(reason: String, after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
        measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
        scope.launch {
            val db = DutyDatabase.build(context)
            try { MeterRuntimeStore.snapshot.value.restIntervalId?.let { DutyRecordRepository(db).finishRestInterval(it, Instant.now(), reason) } } finally { db.close() }
            MeterRuntimeStore.update { it.copy(restIntervalId = null, restDistanceMeters = 0) }
        }
    }

    private fun activeTripId(): String? = if (machine.state.meterState in setOf(MeterState.OCCUPIED, MeterState.PAYMENT)) trip?.id ?: MeterRuntimeStore.snapshot.value.currentTripId else null
    private fun currentSessionId(): String? = sessionId ?: MeterRuntimeStore.snapshot.value.dutySessionId
    private suspend fun latestValidPlace(samples: DrivingSampleRepository): RecordedPlace? {
        val session = currentSessionId() ?: return null
        return samples.latestValidByDutySessionId(session)?.toRecordedPlace()
    }

    private fun finishTrip(status: TripStatus, revenueIncluded: Boolean) {
        val current = trip ?: return
        val value = MeterRuntimeStore.snapshot.value
        val completed = current.copy(dropoffAt = Instant.now(), operatingDistanceMeters = value.chargeableDistanceMeters, fareYen = value.fareYen, status = status, lowSpeedSeconds = value.lowSpeedSeconds, revenueIncluded = revenueIncluded, actualDistanceMeters = value.actualDistanceMeters, chargeableDistanceMeters = value.chargeableDistanceMeters, meteredFareYen = value.fareYen, totalFareYen = value.fareYen)
        scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).finishTrip(completed, status, revenueIncluded) } finally { db.close() } }
        trip = null
    }

    private fun event(type: DutyEventType, modeChange: SafetyRoadModeChange? = null) { val session = currentSessionId() ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).appendEvent(session, DutyEvent(UUID.randomUUID().toString(), type, Instant.now(), null, null, modeChange)) } finally { db.close() } } }
    private fun endDuty() { val session = currentSessionId() ?: return; scope.launch { val db = DutyDatabase.build(context); try { DutyRecordRepository(db).markEnded(session, Instant.now()) } finally { db.close() } } }
}

private fun DrivingSample.toRecordedPlace() = RecordedPlace(latitude, longitude, accuracyMeters, timestamp, null, LocationPrecision.POINT, LocationSource.GPS)

internal fun startsNewTrip(state: MeterState) = state in setOf(MeterState.EMPTY, MeterState.PICKUP, MeterState.REST, MeterState.TOTAL)
internal fun clampPassengerCount(value: Int) = value.coerceIn(1, 9)
internal fun measurementOccupancyState(state: MeterState) = when (state) {
    MeterState.OCCUPIED -> OccupancyState.OCCUPIED
    MeterState.PICKUP -> OccupancyState.PICKUP
    MeterState.REST -> OccupancyState.REST
    MeterState.DEADHEAD -> OccupancyState.DEADHEAD
    else -> OccupancyState.EMPTY
}
internal fun tripStartChargeTypes(state: MeterState, pendingReservation: Boolean, pickupFeeYen: Long, reservationFeeYen: Long) = buildSet {
    if (state == MeterState.PICKUP && pickupFeeYen > 0) add("PICKUP_FEE")
    if (pendingReservation && reservationFeeYen > 0) add("RESERVATION")
}

internal fun meterAudioCueForTransition(before: MeterState, after: MeterState) = when {
    before != MeterState.OCCUPIED && after == MeterState.OCCUPIED -> MeterAudioCue.OCCUPIED
    before != MeterState.PAYMENT && after == MeterState.PAYMENT -> MeterAudioCue.PAYMENT
    else -> null
}

internal fun shouldProcessPayment(state: MeterState) = state == MeterState.OCCUPIED
internal fun shouldFinalizePayment(state: MeterState) = state == MeterState.PAYMENT
