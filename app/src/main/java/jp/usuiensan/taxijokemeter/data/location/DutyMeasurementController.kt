package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.content.Intent
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.DutySession
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeStore
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class DutyMeasurementController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startRequested = false
    private var sessionId: String? = null

    @Synchronized
    fun ensureMeasurementRunning(fareRule: FareRule): String? {
        sessionId = resolveMeasurementSessionId(sessionId, MeasurementDebugStore.snapshot.value)
        if (sessionId != null && MeasurementDebugStore.snapshot.value.running) return sessionId
        startRequested = true
        sessionId = sessionId ?: UUID.randomUUID().toString()
        MeasurementDebugStore.update { it.copy(sessionId = sessionId) }
        val meterState = MeterRuntimeStore.snapshot.value.meterState
        val occupancy = when (meterState) {
            MeterState.OCCUPIED -> OccupancyState.OCCUPIED
            MeterState.PICKUP -> OccupancyState.PICKUP
            MeterState.REST -> OccupancyState.REST
            MeterState.DEADHEAD -> OccupancyState.DEADHEAD
            else -> OccupancyState.EMPTY
        }
        start(MeterRuntimeStore.snapshot.value.roadMode, occupancy, fareRule, sessionId!!)
        return sessionId
    }

    fun resumeLocation(fareRule: FareRule) {
        if (!hasLocationPermission(context)) return
        if (MeasurementDebugStore.snapshot.value.running) {
            context.startService(Intent(context, LocationMeasurementService::class.java).setAction(LocationMeasurementService.ACTION_RESTART_LOCATION))
        } else {
            ensureMeasurementRunning(fareRule)
        }
    }

    fun start(roadMode: RoadMode, occupancyState: OccupancyState, fareRule: FareRule, sessionId: String = UUID.randomUUID().toString()): String {
        this.sessionId = sessionId
        MeasurementDebugStore.update { it.copy(sessionId = sessionId) }
        val database = DutyDatabase.build(context)
        scope.launch {
            if (database.dutyDao().findSession(sessionId) == null) DutyRecordRepository(database).save(DutySession(sessionId, Instant.now(), null))
            database.close()
            withContext(Dispatchers.Main) { context.startForegroundService(Intent(context, LocationMeasurementService::class.java).apply {
            action = LocationMeasurementService.ACTION_START
            putExtra(LocationMeasurementService.EXTRA_SESSION_ID, sessionId)
            putExtra(LocationMeasurementService.EXTRA_ROAD_MODE, roadMode.name)
            putExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE, occupancyState.name)
            putFareRuleExtras(fareRule)
            }) }
        }
        return sessionId
    }

    fun stop() {
        startRequested = false
        context.stopService(Intent(context, LocationMeasurementService::class.java))
    }

    fun updateState(roadMode: RoadMode, occupancyState: OccupancyState) {
        if (!MeasurementDebugStore.snapshot.value.running) return
        context.startService(Intent(context, LocationMeasurementService::class.java).apply {
            action = LocationMeasurementService.ACTION_UPDATE_STATE
            putExtra(LocationMeasurementService.EXTRA_ROAD_MODE, roadMode.name)
            putExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE, occupancyState.name)
        })
    }

    fun beginFareMeasurement(fareRule: FareRule) = context.startService(Intent(context, LocationMeasurementService::class.java).apply {
        action = LocationMeasurementService.ACTION_BEGIN_FARE
        putFareRuleExtras(fareRule)
    })

    fun endFareMeasurement() = sendAction(LocationMeasurementService.ACTION_END_FARE)

    private fun sendAction(action: String) {
        context.startService(Intent(context, LocationMeasurementService::class.java).setAction(action))
    }
}

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun resolveMeasurementSessionId(localSessionId: String?, snapshot: MeasurementDebugSnapshot): String? =
    localSessionId ?: snapshot.sessionId

internal fun Intent.putFareRuleExtras(fareRule: FareRule): Intent = apply {
    putExtra(LocationMeasurementService.EXTRA_INITIAL_DISTANCE_METERS, fareRule.initialDistanceMeters)
    putExtra(LocationMeasurementService.EXTRA_INITIAL_FARE_YEN, fareRule.initialFareYen)
    putExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_METERS, fareRule.distanceIncrementMeters)
    putExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_FARE_YEN, fareRule.distanceIncrementFareYen)
    putExtra(LocationMeasurementService.EXTRA_LOW_SPEED_LIMIT_KMH, fareRule.lowSpeedLimitKmh)
    putExtra(LocationMeasurementService.EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS, fareRule.lowSpeedTimeIncrementSeconds)
}
