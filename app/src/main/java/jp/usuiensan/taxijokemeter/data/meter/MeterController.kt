package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.content.pm.PackageManager
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.duty.TripPlaceEntity
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettingsStore
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.data.location.hasLocationPermission
import jp.usuiensan.taxijokemeter.data.location.AndroidReverseGeocoder
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.location.reportAddressForAccuracy
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
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
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentSelection
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentRates
import jp.usuiensan.taxijokemeter.domain.model.toggleFareAdjustment
import jp.usuiensan.taxijokemeter.domain.model.totalFareYen
import jp.usuiensan.taxijokemeter.domain.road.RoadModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

class MeterController internal constructor(
    private val context: Context,
    private val databaseName: String = "duty.db",
    reverseGeocoder: ReverseGeocoder? = null,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val reverseGeocodeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val measurement = DutyMeasurementController(context)
    private val reverseGeocoder = reverseGeocoder ?: AndroidReverseGeocoder(context)
    private val audioCuePlayer = AudioCuePlayer({ cue -> MeterAudioResources.resourceId(context, cue) }, MediaPlayerAudioCueSink(context), safetyPlaybackGuard = ProcessSafetyPlaybackGuard)
    private val roadModeController = RoadModeController()
    private var sessionId: String? = null
    private var trip: Trip? = null
    private var fareRule = FareRuleStore(context).load()

    fun ensureMeasurementRunning() {
        if (!hasLocationPermission(context)) return
        fareRule = FareRuleStore(context).load()
        sessionId = measurement.ensureMeasurementRunning(fareRule)
        MeterRuntimeStore.update { it.copy(dutySessionId = sessionId, pendingReservation = reservationPreferences().getBoolean(KEY_PENDING_RESERVATION, it.pendingReservation)) }
        sessionId?.let { currentSession -> enqueue {
            val db = database()
            try {
                DutyRecordRepository(db).findActiveRestInterval(currentSession)?.let { rest ->
                    MeterRuntimeStore.update { it.copy(meterState = MeterState.REST, menu = MeterMenu.NONE, restIntervalId = rest.id, restStartedAt = rest.startedAt) }
                }
            } finally { db.close() }
        } }
    }

    fun dispatch(action: MeterAction) {
        if (action == MeterAction.DEPART && !hasLocationPermission(context)) return
        val machine = MeterStateMachine(MeterRuntimeStore.snapshot.value.toMeterRuntimeState())
        val before = machine.state
        if (action == MeterAction.PAYMENT && !shouldProcessPayment(before.meterState)) return
        if (action == MeterAction.TOTAL && !shouldFinalizePayment(before.meterState)) return
        if (action == MeterAction.EMPTY && before.meterState == MeterState.PAYMENT) return
        val after = machine.dispatch(action)
        meterAudioCueForTransition(before.meterState, after.meterState)?.let { audioCuePlayer.enqueue(it, "MeterController.stateTransition") }
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
                enqueue { measurement.updateState(after.roadMode, OccupancyState.OCCUPIED) }
            }
            MeterAction.PAYMENT -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                enqueue {
                    measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                    val tripId = activeTripId() ?: return@enqueue
                    val db = database()
                    try {
                        DutyRecordRepository(db).upsertDropoff(tripId, latestValidPlace(DrivingSampleRepository(db)))
                        db.dutyDao().findTripPlace(tripId, "DROPOFF", 0)?.let(::enqueueReverseGeocode)
                    } finally { db.close() }
                }
            }
            MeterAction.TOTAL -> {
                val value = MeterRuntimeStore.snapshot.value
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                event(DutyEventType.DROPOFF)
                enqueue {
                    measurement.endFareMeasurement()
                    measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                    finishTrip(TripStatus.COMPLETED, true, value)
                }
            }
            MeterAction.EMPTY -> {
                if (before.meterState == MeterState.OCCUPIED) {
                    val value = MeterRuntimeStore.snapshot.value
                    enqueue {
                        measurement.endFareMeasurement()
                        measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
                        finishTrip(TripStatus.VOIDED, false, value)
                    }
                } else enqueue { measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState)) }
                MeterRuntimeStore.update { state -> state.copy(meterState = after.meterState, menu = after.menu, fareYen = if (after.meterState == MeterState.EMPTY) 0 else state.fareYen, actualDistanceMeters = if (after.meterState == MeterState.EMPTY) 0 else state.actualDistanceMeters, chargeableDistanceMeters = if (after.meterState == MeterState.EMPTY) 0 else state.chargeableDistanceMeters, lowSpeedSeconds = if (after.meterState == MeterState.EMPTY) 0 else state.lowSpeedSeconds, chargeProgress = if (after.meterState == MeterState.EMPTY) null else state.chargeProgress, tripStartedAt = null, currentTripId = if (after.meterState == MeterState.EMPTY) null else state.currentTripId, currentTripNumber = if (after.meterState == MeterState.EMPTY) null else state.currentTripNumber) }
            }
            MeterAction.PICKUP -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                enqueue { measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState)) }
            }
            MeterAction.REST -> if (before.meterState == MeterState.EMPTY && after.meterState == MeterState.REST && MeterRuntimeStore.snapshot.value.restIntervalId == null) startRest(after)
            MeterAction.REST_RELEASE -> finishRest("MANUAL", after)
            MeterAction.DEADHEAD -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                enqueue { measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState)) }
                event(DutyEventType.DEADHEAD_START)
            }
            MeterAction.RELEASE -> {
                MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
                enqueue { measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState)) }
                event(DutyEventType.DEADHEAD_END)
            }
            MeterAction.BREAK -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_START) }
            MeterAction.RESUME -> { MeterRuntimeStore.update { it.copy(dutyActivity = after.dutyActivity, menu = after.menu) }; event(DutyEventType.BREAK_END) }
            MeterAction.ARRIVE -> { enqueue { measurement.stop() }; MeterRuntimeStore.update { it.copy(dutyLifecycle = after.dutyLifecycle, menu = after.menu) }; event(DutyEventType.ARRIVAL); endDuty() }
            MeterAction.MENU, MeterAction.BACK -> MeterRuntimeStore.update { it.copy(menu = after.menu) }
        }
    }

    fun setRoadMode(mode: RoadMode) {
        val machine = MeterStateMachine(MeterRuntimeStore.snapshot.value.toMeterRuntimeState())
        val transition = roadModeController.select(machine.state.roadMode, mode)
        if (transition.safetyRoadModeChange == null) return
        val after = machine.setRoadMode(transition.nextMode)
        MeterRuntimeStore.update { it.copy(roadMode = transition.nextMode) }
        enqueue { measurement.updateState(transition.nextMode, measurementOccupancyState(after.meterState)) }
        event(DutyEventType.SAFETY_ROAD_MODE_CHANGE, transition.safetyRoadModeChange)
    }

    fun toggleReservation() {
        if (MeterRuntimeStore.snapshot.value.meterState in setOf(MeterState.EMPTY, MeterState.PICKUP)) {
            MeterRuntimeStore.update { state ->
                val pending = !state.pendingReservation
                reservationPreferences().edit().putBoolean(KEY_PENDING_RESERVATION, pending).apply()
                state.copy(pendingReservation = pending)
            }
        }
    }

    fun changePassengerCount(passengerCount: Int) {
        if (MeterRuntimeStore.snapshot.value.meterState !in setOf(MeterState.OCCUPIED, MeterState.PAYMENT)) return
        val count = clampPassengerCount(passengerCount)
        MeterRuntimeStore.update { it.copy(passengerCount = count) }
        enqueue {
            val tripId = activeTripId() ?: return@enqueue
            val db = database()
            try { DutyRecordRepository(db).updatePassengerCount(tripId, count) } finally { db.close() }
        }
    }

    fun addWaypoint() {
        if (MeterRuntimeStore.snapshot.value.meterState != MeterState.OCCUPIED) return
        enqueue {
            val tripId = activeTripId() ?: return@enqueue
            val db = database()
            try {
                val place = latestValidPlace(DrivingSampleRepository(db)) ?: return@enqueue
                DutyRecordRepository(db).addWaypoint(tripId, place)
                db.dutyDao().maxWaypointSequence(tripId)?.let { sequence ->
                    db.dutyDao().findTripPlace(tripId, "WAYPOINT", sequence)?.let(::enqueueReverseGeocode)
                }
                MeterRuntimeStore.update { it.copy(waypointCount = it.waypointCount + 1) }
            } finally { db.close() }
        }
    }

    fun togglePickupFee() {
        if (MeterRuntimeStore.snapshot.value.meterState !in setOf(MeterState.OCCUPIED, MeterState.PAYMENT)) return
        if (MeterRuntimeStore.snapshot.value.pickupFeeApplied) return
        val amount = TripChargeSettingsStore(context).load().pickupFeeYen
        if (amount <= 0) return
        MeterRuntimeStore.update { it.copy(pickupFeeApplied = true, fareChargesYen = it.fareChargesYen + amount, fareYen = totalFareYen(it.meteredFareYen, it.fareAdjustmentType, it.fareAdjustmentPercent, it.fareChargesYen + amount)) }
        enqueue {
            val tripId = activeTripId() ?: return@enqueue
            val db = database()
            try { DutyRecordRepository(db).addChargeIfAbsent(tripId, "PICKUP_FEE", amount, Instant.now()) } finally { db.close() }
        }
    }

    fun toggleFareAdjustment(type: FareAdjustmentType) {
        if (MeterRuntimeStore.snapshot.value.meterState != MeterState.OCCUPIED) return
        val settings = TripChargeSettingsStore(context).load()
        val current = MeterRuntimeStore.snapshot.value
        val selection = toggleFareAdjustment(
            FareAdjustmentSelection(current.fareAdjustmentType, current.fareAdjustmentPercent),
            type,
            FareAdjustmentRates(settings.surchargePercent, settings.discountPercent),
        )
        MeterRuntimeStore.update { it.copy(fareAdjustmentType = selection.type, fareAdjustmentPercent = selection.percent, fareYen = totalFareYen(it.meteredFareYen, selection.type, selection.percent, it.fareChargesYen)) }
        enqueue {
            val tripId = activeTripId() ?: return@enqueue
            val db = database()
            try { db.dutyDao().updateTripAdjustment(tripId, selection.type.name, selection.percent) } finally { db.close() }
        }
    }

    fun resumeLocation() {
        if (!hasLocationPermission(context)) return
        fareRule = FareRuleStore(context).load()
        measurement.resumeLocation(fareRule)
    }

    private fun startTrip(before: MeterState, after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        fareRule = FareRuleStore(context).load()
        val now = Instant.now()
        val settings = TripChargeSettingsStore(context).load()
        val pendingReservation = MeterRuntimeStore.snapshot.value.pendingReservation
        val initialCharges = tripStartCharges(before, pendingReservation, settings.pickupFeeYen, settings.reservationFeeYen)
        val initialChargeTotal = initialCharges.values.sum()
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, tripStartedAt = now, fareYen = fareRule.initialFareYen + initialChargeTotal, meteredFareYen = fareRule.initialFareYen, fareChargesYen = initialChargeTotal, actualDistanceMeters = 0, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, currentTripId = null, currentTripNumber = null, chargeProgress = ChargeProgressMapper.initial(fareRule, it.latestSpeedMps), passengerCount = 1, waypointCount = 0, pickupFeeApplied = "PICKUP_FEE" in initialCharges, restIntervalId = if (before == MeterState.REST) it.restIntervalId else null, restStartedAt = null, restDistanceMeters = if (before == MeterState.REST) it.restDistanceMeters else 0) }
        val session = currentSessionId() ?: return
        enqueue {
            val db = database()
            try {
                val records = DutyRecordRepository(db)
                if (before == MeterState.REST) MeterRuntimeStore.snapshot.value.restIntervalId?.let { records.finishRestInterval(it, now, "OCCUPIED_STARTED") }
                val started = records.startTrip(session, now, fareRule)
                trip = started
                records.savePickupPlace(started.id, latestValidPlace(DrivingSampleRepository(db)))
                db.dutyDao().findTripPlace(started.id, "PICKUP", 0)?.let(::enqueueReverseGeocode)
                initialCharges.forEach { (type, amount) -> records.addChargeIfAbsent(started.id, type, amount, now) }
                records.appendEvent(session, DutyEvent(UUID.randomUUID().toString(), DutyEventType.PICKUP, now, null, null))
                MeterRuntimeStore.update { it.copy(currentTripId = started.id, currentTripNumber = started.tripNumber, pendingReservation = if (pendingReservation) false else it.pendingReservation, restIntervalId = null, restStartedAt = null, restDistanceMeters = 0) }
                if (pendingReservation) reservationPreferences().edit().putBoolean(KEY_PENDING_RESERVATION, false).apply()
                measurement.updateState(after.roadMode, OccupancyState.OCCUPIED)
                measurement.beginFareMeasurement(fareRule)
            } finally { db.close() }
        }
    }

    private fun startRest(after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        val startedAt = Instant.now()
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu, restDistanceMeters = 0, restIntervalId = null, restStartedAt = startedAt) }
        val session = currentSessionId() ?: return
        enqueue {
            measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
            val db = database()
            try {
                val id = DutyRecordRepository(db).startRestInterval(session, startedAt, latestValidPlace(DrivingSampleRepository(db)))
                MeterRuntimeStore.update { if (it.restIntervalId == null) it.copy(restIntervalId = id) else it }
            } finally { db.close() }
        }
    }

    private fun finishRest(reason: String, after: jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState) {
        MeterRuntimeStore.update { it.copy(meterState = after.meterState, menu = after.menu) }
        enqueue {
            measurement.updateState(after.roadMode, measurementOccupancyState(after.meterState))
            val db = database()
            try { MeterRuntimeStore.snapshot.value.restIntervalId?.let { DutyRecordRepository(db).finishRestInterval(it, Instant.now(), reason) } } finally { db.close() }
            MeterRuntimeStore.update { it.copy(restIntervalId = null, restStartedAt = null, restDistanceMeters = 0) }
        }
    }

    private fun activeTripId(): String? = trip?.id ?: MeterRuntimeStore.snapshot.value.currentTripId
    private fun currentSessionId(): String? = sessionId ?: MeterRuntimeStore.snapshot.value.dutySessionId
    private suspend fun latestValidPlace(samples: DrivingSampleRepository): RecordedPlace? {
        val session = currentSessionId() ?: return null
        return samples.latestValidByDutySessionId(session)?.toRecordedPlace()
    }

    private suspend fun finishTrip(status: TripStatus, revenueIncluded: Boolean, value: MeterRuntimeSnapshot) {
        val current = trip ?: return
        val completed = current.copy(fareAdjustmentType = value.fareAdjustmentType, fareAdjustmentPercent = value.fareAdjustmentPercent, dropoffAt = Instant.now(), operatingDistanceMeters = value.chargeableDistanceMeters, fareYen = value.meteredFareYen, status = status, lowSpeedSeconds = value.lowSpeedSeconds, revenueIncluded = revenueIncluded, actualDistanceMeters = value.actualDistanceMeters, chargeableDistanceMeters = value.chargeableDistanceMeters, meteredFareYen = value.meteredFareYen, totalFareYen = value.fareYen, additionalChargeYen = value.fareChargesYen)
        val db = database()
        try { DutyRecordRepository(db).finishTrip(completed, status, revenueIncluded) } finally { db.close() }
        trip = null
    }

    private fun enqueue(operation: suspend () -> Unit) { scope.launch { operationMutex.withLock { operation() } } }
    private fun enqueueReverseGeocode(place: TripPlaceEntity) {
        reverseGeocodeScope.launchReverseGeocode(Dispatchers.IO) {
            val result = try {
                reverseGeocoder.reverseGeocode(place.latitude, place.longitude)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            } ?: return@launchReverseGeocode
            val db = database()
            try {
                db.dutyDao().updateTripPlaceAddresses(
                    place.id,
                    place.recordedAt,
                    place.latitude,
                    place.longitude,
                    result.canonicalAddress,
                    reportAddressForAccuracy(result, place.accuracyMeters ?: Double.POSITIVE_INFINITY),
                    result.landmarkName,
                    result.landmarkDetail,
                )
            } finally { db.close() }
        }
    }
    private fun database() = DutyDatabase.build(context, databaseName)
    private fun reservationPreferences() = context.getSharedPreferences("meter_runtime", Context.MODE_PRIVATE)
    private fun event(type: DutyEventType, modeChange: SafetyRoadModeChange? = null) { val session = currentSessionId() ?: return; enqueue { val db = database(); try { DutyRecordRepository(db).appendEvent(session, DutyEvent(UUID.randomUUID().toString(), type, Instant.now(), null, null, modeChange)) } finally { db.close() } } }
    private fun endDuty() { val session = currentSessionId() ?: return; enqueue { val db = database(); try { DutyRecordRepository(db).markEnded(session, Instant.now()) } finally { db.close() } } }

    private companion object {
        const val KEY_PENDING_RESERVATION = "pending_reservation"
    }
}

internal fun CoroutineScope.launchReverseGeocode(dispatcher: CoroutineDispatcher, operation: suspend CoroutineScope.() -> Unit): Job =
    launch(dispatcher, block = operation)

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

internal fun tripStartCharges(state: MeterState, pendingReservation: Boolean, pickupFeeYen: Long, reservationFeeYen: Long) = buildMap {
    if (state == MeterState.PICKUP && pickupFeeYen > 0) put("PICKUP_FEE", pickupFeeYen)
    if (pendingReservation && reservationFeeYen > 0) put("RESERVATION", reservationFeeYen)
}

internal fun meterAudioCueForTransition(before: MeterState, after: MeterState) = when {
    before != MeterState.OCCUPIED && after == MeterState.OCCUPIED -> MeterAudioCue.OCCUPIED
    before != MeterState.PAYMENT && after == MeterState.PAYMENT -> MeterAudioCue.PAYMENT
    else -> null
}

internal fun shouldProcessPayment(state: MeterState) = state == MeterState.OCCUPIED
internal fun shouldFinalizePayment(state: MeterState) = state == MeterState.PAYMENT
