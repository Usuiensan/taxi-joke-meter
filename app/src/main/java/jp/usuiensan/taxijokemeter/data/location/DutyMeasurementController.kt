package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.content.Intent
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode

class DutyMeasurementController(private val context: Context) {
    fun start(sessionId: String, roadMode: RoadMode, occupancyState: OccupancyState) {
        context.startForegroundService(Intent(context, LocationMeasurementService::class.java).apply {
            action = LocationMeasurementService.ACTION_START
            putExtra(LocationMeasurementService.EXTRA_SESSION_ID, sessionId)
            putExtra(LocationMeasurementService.EXTRA_ROAD_MODE, roadMode.name)
            putExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE, occupancyState.name)
        })
    }

    fun stop() {
        context.stopService(Intent(context, LocationMeasurementService::class.java))
    }
}
