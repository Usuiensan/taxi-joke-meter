package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.data.location.LocationHealth
import jp.usuiensan.taxijokemeter.domain.meter.DutyActivity
import jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeterRuntimeSnapshot(
    val dutyLifecycle: DutyLifecycle = DutyLifecycle.BEFORE_DEPARTURE,
    val meterState: MeterState = MeterState.EMPTY,
    val dutyActivity: DutyActivity = DutyActivity.WORKING,
    val roadMode: RoadMode = RoadMode.GENERAL,
    val fareYen: Long = 0,
    val chargeableDistanceMeters: Long = 0,
    val lowSpeedSeconds: Long = 0,
    val gpsHealth: LocationHealth = LocationHealth.UNAVAILABLE,
    val latestSpeedMps: Double? = null,
    val latestAccuracyMeters: Double? = null,
    val savedSampleCount: Int = 0,
    val menu: MeterMenu = MeterMenu.NONE,
    val dutySessionId: String? = null,
    val tripStartedAt: java.time.Instant? = null,
    val chargeProgress: ChargeProgressUiState? = null,
)

object MeterRuntimeStore {
    private val _snapshot = MutableStateFlow(MeterRuntimeSnapshot())
    val snapshot: StateFlow<MeterRuntimeSnapshot> = _snapshot.asStateFlow()
    fun update(transform: (MeterRuntimeSnapshot) -> MeterRuntimeSnapshot) { _snapshot.value = transform(_snapshot.value) }
    fun resetMeter() { update { it.copy(fareYen = 0, chargeableDistanceMeters = 0, lowSpeedSeconds = 0, chargeProgress = null) } }
}
