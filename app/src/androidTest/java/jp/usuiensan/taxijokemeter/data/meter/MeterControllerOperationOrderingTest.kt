package jp.usuiensan.taxijokemeter.data.meter

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettings
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettingsStore
import jp.usuiensan.taxijokemeter.data.location.LocationMeasurementService
import jp.usuiensan.taxijokemeter.data.location.MeasurementCommandDebug
import jp.usuiensan.taxijokemeter.data.location.MeasurementDebugSnapshot
import jp.usuiensan.taxijokemeter.data.location.MeasurementDebugStore
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.DutySession
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class MeterControllerOperationOrderingTest {
    private lateinit var baseContext: Context
    private lateinit var context: IsolatedControllerContext
    private lateinit var database: DutyDatabase
    private lateinit var sessionId: String

    @Before fun setUp() = runBlocking {
        baseContext = ApplicationProvider.getApplicationContext()
        context = IsolatedControllerContext(baseContext, "controller-${UUID.randomUUID()}.db")
        database = DutyDatabase.build(context)
        sessionId = "session-${UUID.randomUUID()}"
        DutyRecordRepository(database).save(DutySession(sessionId, Instant.EPOCH, null))
        DrivingSampleRepository(database).save(sample(sessionId))
        TripChargeSettingsStore(context).save(TripChargeSettings(pickupFeeYen = 500, reservationFeeYen = 0))
        MeterRuntimeStore.update { MeterRuntimeSnapshot(dutySessionId = sessionId) }
    }

    @After fun tearDown() {
        if (context.delegateServiceStarts) baseContext.stopService(Intent(baseContext, LocationMeasurementService::class.java))
        MeasurementDebugStore.update(MeasurementDebugSnapshot())
        database.close()
        baseContext.deleteDatabase(context.databaseName)
    }

    @Test fun repeatedRestCreatesExactlyOneInterval() = runBlocking {
        val controller = MeterController(context, context.databaseName)

        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.REST)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.REST)

        waitUntil { restCount() == 1 }
        assertEquals(1, restCount())
    }

    @Test fun autoCompletedRestIsEmptyForRoadModeAndReservationEligibility() = runBlocking {
        val controller = MeterController(context, context.databaseName)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.REST)
        waitUntil { MeterRuntimeStore.snapshot.value.restIntervalId != null }
        MeterRuntimeStore.update {
            it.copy(meterState = MeterState.EMPTY, menu = MeterMenu.EMPTY_MENU, restIntervalId = null)
        }
        MeasurementDebugStore.update(MeasurementDebugSnapshot(running = true, sessionId = sessionId))
        context.startedServices.clear()

        controller.setRoadMode(RoadMode.HIGHWAY2)
        controller.toggleReservation()

        waitUntil { context.startedServices.any { it.action == LocationMeasurementService.ACTION_UPDATE_STATE } }
        val stateUpdate = context.startedServices.last { it.action == LocationMeasurementService.ACTION_UPDATE_STATE }
        assertEquals(OccupancyState.EMPTY.name, stateUpdate.getStringExtra(LocationMeasurementService.EXTRA_OCCUPANCY_STATE))
        assertTrue(MeterRuntimeStore.snapshot.value.pendingReservation)
        waitUntil { DutyRecordRepository(database).findById(sessionId)!!.events.any { it.type.name == "SAFETY_ROAD_MODE_CHANGE" } }
    }

    @Test fun immediateTripOperationsPersistOneCompletedTripThenOneNewActiveTrip() = runBlocking {
        val controller = MeterController(context, context.databaseName)

        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.OCCUPIED)
        controller.changePassengerCount(12)
        controller.addWaypoint()
        controller.togglePickupFee()
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.PAYMENT)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.OCCUPIED)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.PAYMENT)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.TOTAL)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.OCCUPIED)

        waitUntil { database.dutyDao().findTrips(sessionId).size == 2 }
        val trips = database.dutyDao().findTrips(sessionId)
        val completed = trips.single { it.status == TripStatus.COMPLETED.name }
        val active = trips.single { it.status == TripStatus.ACTIVE.name }

        assertEquals(9, completed.passengerCount)
        assertEquals(1, database.dutyDao().findTripCharges(completed.id).count { it.type == "PICKUP_FEE" })
        assertEquals(1, database.dutyDao().findTripPlaces(completed.id).count { it.type == "WAYPOINT" })
        assertEquals(1, database.dutyDao().findTripPlaces(completed.id).count { it.type == "DROPOFF" })
        assertEquals(2, active.tripNumber)
    }

    @Test fun immediateEmptyFinalizesTheNewTripAsVoided() = runBlocking {
        val controller = MeterController(context, context.databaseName)

        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.OCCUPIED)
        controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.EMPTY)

        waitUntil { database.dutyDao().findTrips(sessionId).singleOrNull()?.status == TripStatus.VOIDED.name }
        assertEquals(TripStatus.VOIDED.name, database.dutyDao().findTrips(sessionId).single().status)
    }

    @Test fun immediateOccupiedPaymentTotalIsProcessedByMeasurementServiceInOrder() = runBlocking {
        context.delegateServiceStarts = true
        MeasurementDebugStore.update(MeasurementDebugSnapshot(running = true, sessionId = sessionId))
        val controller = MeterController(context, context.databaseName)
        val writableDatabase = database.openHelper.writableDatabase

        writableDatabase.beginTransaction()
        try {
            controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.OCCUPIED)
            controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.PAYMENT)
            controller.dispatch(jp.usuiensan.taxijokemeter.domain.meter.MeterAction.TOTAL)
        } finally {
            writableDatabase.endTransaction()
        }

        waitUntil { MeasurementDebugStore.snapshot.value.processedCommands.size == 5 }
        assertEquals(
            listOf(
                MeasurementCommandDebug(LocationMeasurementService.ACTION_UPDATE_STATE, OccupancyState.OCCUPIED, false),
                MeasurementCommandDebug(LocationMeasurementService.ACTION_BEGIN_FARE, OccupancyState.OCCUPIED, true),
                MeasurementCommandDebug(LocationMeasurementService.ACTION_UPDATE_STATE, OccupancyState.EMPTY, true),
                MeasurementCommandDebug(LocationMeasurementService.ACTION_END_FARE, OccupancyState.EMPTY, false),
                MeasurementCommandDebug(LocationMeasurementService.ACTION_UPDATE_STATE, OccupancyState.EMPTY, false),
            ),
            MeasurementDebugStore.snapshot.value.processedCommands,
        )
    }

    @Test fun sameRoadModeDoesNotRecordAnEvent() = runBlocking {
        val controller = MeterController(context, context.databaseName)

        controller.setRoadMode(RoadMode.GENERAL)
        Thread.sleep(100)

        assertTrue(DutyRecordRepository(database).findById(sessionId)!!.events.none { it.type.name == "SAFETY_ROAD_MODE_CHANGE" })
    }

    private fun restCount() = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM rest_intervals WHERE sessionId = ?", arrayOf(sessionId),
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private suspend fun waitUntil(condition: suspend () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("timed out", condition())
    }

    private fun sample(dutySessionId: String) = DrivingSample(
        dutySessionId = dutySessionId, timestamp = Instant.EPOCH, latitude = 35.0, longitude = 139.0,
        accuracyMeters = 5.0, speedMps = 0.0, bearingDegrees = null, roadMode = RoadMode.GENERAL,
        occupancyState = OccupancyState.EMPTY, measurementQuality = MeasurementQuality.VALID,
    )
}

private class IsolatedControllerContext(base: Context, val databaseName: String) : ContextWrapper(base) {
    var delegateServiceStarts = false
    val startedServices = CopyOnWriteArrayList<Intent>()

    override fun getApplicationContext(): Context = this
    override fun getDatabasePath(name: String) = baseContext.getDatabasePath(databaseName)
    override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences("$databaseName-$name", mode)
    override fun startService(service: Intent): ComponentName? {
        startedServices += service
        return if (delegateServiceStarts) baseContext.startService(service)
        else ComponentName(baseContext.packageName, service.component?.className ?: "test")
    }
    override fun startForegroundService(service: Intent): ComponentName = requireNotNull(startService(service))
}
