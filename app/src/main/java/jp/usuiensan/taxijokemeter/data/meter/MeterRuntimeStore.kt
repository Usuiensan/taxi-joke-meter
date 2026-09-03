package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.data.location.LocationHealth
import jp.usuiensan.taxijokemeter.domain.meter.DutyActivity
import jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterRuntimeState
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class MeterRuntimeSnapshot(
    val dutyLifecycle: DutyLifecycle = DutyLifecycle.BEFORE_DEPARTURE,
    val meterState: MeterState = MeterState.EMPTY,
    val dutyActivity: DutyActivity = DutyActivity.WORKING,
    val roadMode: RoadMode = RoadMode.GENERAL,
    val fareYen: Long = 0,
    val chargeableDistanceMeters: Long = 0,
    val actualDistanceMeters: Long = 0,
    val lowSpeedSeconds: Long = 0,
    val gpsHealth: LocationHealth = LocationHealth.UNAVAILABLE,
    val latestSpeedMps: Double? = null,
    val latestAccuracyMeters: Double? = null,
    val savedSampleCount: Int = 0,
    val menu: MeterMenu = MeterMenu.EMPTY_MENU,
    val dutySessionId: String? = null,
    val tripStartedAt: java.time.Instant? = null,
    val currentTripId: String? = null,
    val currentTripNumber: Int? = null,
    val chargeProgress: ChargeProgressUiState? = null,
    val pendingReservation: Boolean = false,
    val passengerCount: Int = 1,
    val waypointCount: Int = 0,
    val pickupFeeApplied: Boolean = false,
    val restIntervalId: String? = null,
    val restDistanceMeters: Long = 0,
    val safetyWarning: SafetyWarning? = null,
)

internal fun MeterRuntimeSnapshot.toMeterRuntimeState() = MeterRuntimeState(dutyLifecycle, meterState, dutyActivity, roadMode, menu)

data class SafetyWarning(
    val id: String,
    val meterState: MeterState,
    val shownAtElapsedRealtimeMillis: Long,
    val expiresAtElapsedRealtimeMillis: Long,
    val text: String,
) {
    fun isVisibleAt(nowElapsedRealtimeMillis: Long) = nowElapsedRealtimeMillis < expiresAtElapsedRealtimeMillis
}

fun safetyWarningText(type: SafetyEventType, accelerationMps2: Double?, speedKmh: Int?): String = when (type) {
    SafetyEventType.HARD_ACCELERATION -> "⚠ 急加速 (%.2fG)".format(kotlin.math.abs(accelerationMps2 ?: 0.0) / 9.80665)
    SafetyEventType.HARD_DECELERATION -> "⚠ 急減速 (%.2fG)".format(kotlin.math.abs(accelerationMps2 ?: 0.0) / 9.80665)
    SafetyEventType.OVERSPEED -> "⚠ 速度超過 (現在 ${speedKmh ?: 0} km/h)"
}

object MeterRuntimeStore {
    private val _snapshot = MutableStateFlow(MeterRuntimeSnapshot())
    val snapshot: StateFlow<MeterRuntimeSnapshot> = _snapshot.asStateFlow()
    private val warningScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun update(transform: (MeterRuntimeSnapshot) -> MeterRuntimeSnapshot) { _snapshot.update(transform) }
    fun resetMeter() { update { it.copy(fareYen = 0, actualDistanceMeters = 0, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, chargeProgress = null, pendingReservation = false, passengerCount = 1, waypointCount = 0, pickupFeeApplied = false, restIntervalId = null, restDistanceMeters = 0) } }

    fun showSafetyWarning(text: String, nowElapsedRealtimeMillis: Long) {
        val warning = SafetyWarning(UUID.randomUUID().toString(), snapshot.value.meterState, nowElapsedRealtimeMillis, nowElapsedRealtimeMillis + 3_000L, text)
        update { it.copy(safetyWarning = warning) }
        warningScope.launch {
            delay(3_000L)
            update { if (it.safetyWarning?.id == warning.id) it.copy(safetyWarning = null) else it }
        }
    }
}
