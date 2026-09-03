package jp.usuiensan.taxijokemeter.data.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState

data class MeasurementCommandDebug(
    val action: String,
    val occupancyState: OccupancyState,
    val fareActive: Boolean,
)

data class MeasurementDebugSnapshot(
    val running: Boolean = false,
    val sessionId: String? = null,
    val health: LocationHealth = LocationHealth.UNAVAILABLE,
    val latestAccuracyMeters: Double? = null,
    val latestSpeedMps: Double? = null,
    val savedSampleCount: Int = 0,
    val roadMode: String = "GENERAL",
    val processedCommands: List<MeasurementCommandDebug> = emptyList(),
)

object MeasurementDebugStore {
    private val _snapshot = MutableStateFlow(MeasurementDebugSnapshot())
    val snapshot: StateFlow<MeasurementDebugSnapshot> = _snapshot.asStateFlow()

    fun update(snapshot: MeasurementDebugSnapshot) { _snapshot.value = snapshot }
    fun update(transform: (MeasurementDebugSnapshot) -> MeasurementDebugSnapshot) { _snapshot.value = transform(_snapshot.value) }
}
