package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.content.Intent
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.FareRule

class DutyMeasurementController(private val context: Context) {
    fun start(sessionId: String, roadMode: RoadMode, occupancyState: OccupancyState, fareRule: FareRule) {
        context.startForegroundService(Intent(context, LocationMeasurementService::class.java).apply {
            action = LocationMeasurementService.ACTION_START
            putExtra(LocationMeasurementService.EXTRA_SESSION_ID, sessionId)
            putExtra(LocationMeasurementService.EXTRA_ROAD_MODE, roadMode.name)
            putExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE, occupancyState.name)
            putExtra(LocationMeasurementService.EXTRA_INITIAL_DISTANCE_METERS, fareRule.initialDistanceMeters)
            putExtra(LocationMeasurementService.EXTRA_INITIAL_FARE_YEN, fareRule.initialFareYen)
            putExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_METERS, fareRule.distanceIncrementMeters)
            putExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_FARE_YEN, fareRule.distanceIncrementFareYen)
            putExtra(LocationMeasurementService.EXTRA_LOW_SPEED_LIMIT_KMH, fareRule.lowSpeedLimitKmh)
            putExtra(LocationMeasurementService.EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS, fareRule.lowSpeedTimeIncrementSeconds)
        })
    }

    fun stop() {
        context.stopService(Intent(context, LocationMeasurementService::class.java))
    }

    fun updateState(roadMode: RoadMode, occupancyState: OccupancyState) {
        context.startService(Intent(context, LocationMeasurementService::class.java).apply {
            action = LocationMeasurementService.ACTION_UPDATE_STATE
            putExtra(LocationMeasurementService.EXTRA_ROAD_MODE, roadMode.name)
            putExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE, occupancyState.name)
        })
    }

    fun beginFareMeasurement() = sendAction(LocationMeasurementService.ACTION_BEGIN_FARE)

    fun endFareMeasurement() = sendAction(LocationMeasurementService.ACTION_END_FARE)

    private fun sendAction(action: String) {
        context.startService(Intent(context, LocationMeasurementService::class.java).setAction(action))
    }
}
