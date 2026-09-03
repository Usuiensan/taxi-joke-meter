package jp.usuiensan.taxijokemeter.data.duty

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.DutySession
import jp.usuiensan.taxijokemeter.domain.model.LocationPrecision
import jp.usuiensan.taxijokemeter.domain.model.LocationSource
import jp.usuiensan.taxijokemeter.domain.model.PaymentMethod
import jp.usuiensan.taxijokemeter.domain.model.RecordedPlace
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange
import jp.usuiensan.taxijokemeter.domain.model.Trip
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class DutyRecordRepositoryTest {
    private lateinit var database: DutyDatabase
    private lateinit var repository: DutyRecordRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DutyDatabase::class.java).build()
        repository = DutyRecordRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun v9SchemaContainsReportFoundationTablesAndTripColumns() {
        val db = database.openHelper.readableDatabase
        assertEquals(9, db.version)
        assertTrue(db.query("PRAGMA table_info(trips)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
                .containsAll(listOf("serviceDate", "tripNumber", "actualDistanceMeters", "chargeableDistanceMeters", "meteredFareYen", "totalFareYen", "fareInitialDistanceMeters", "fareInitialFareYen", "fareDistanceIncrementMeters", "fareDistanceIncrementFareYen", "fareLowSpeedLimitKmh", "fareLowSpeedTimeIncrementSeconds"))
        })
        assertTrue(tableExists(db, "trip_places"))
        assertTrue(tableExists(db, "trip_charges"))
        assertTrue(tableExists(db, "rest_intervals"))
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String) =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    @Test fun savesAndReloadsSessionTripsEventsAndUnresolvedPlace() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        val place = RecordedPlace(35.7295, 139.7168, 12.0, now, null, LocationPrecision.UNKNOWN, LocationSource.GPS)
        val session = DutySession(
            "duty-1", now, now.plusSeconds(3_600),
            trips = listOf(
                Trip("trip-1", now.plusSeconds(60), now.plusSeconds(300), place, null, 2, 1_250, 1_500, 200, PaymentMethod.CASH, "備考"),
                Trip("trip-2", now.plusSeconds(600), now.plusSeconds(900), null, place, 1, 2_000, 2_200, 0, PaymentMethod.CARD, null),
            ),
            events = DutyEventType.entries.mapIndexed { index, type ->
                DutyEvent("event-$index", type, now.plusSeconds(index.toLong()), if (index == 0) place else null, null)
            },
        )

        repository.save(session)

        assertEquals(session, repository.findById("duty-1"))
    }

    @Test fun appendsModeChangeEventWithNullPlace() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        repository.save(DutySession("duty-2", now, null))
        val event = DutyEvent("event-mode", DutyEventType.SAFETY_ROAD_MODE_CHANGE, now, null, null, SafetyRoadModeChange(RoadMode.GENERAL, RoadMode.HIGHWAY1))

        repository.appendEvent("duty-2", event)

        assertEquals(event, repository.findById("duty-2")!!.events.single())
    }

    @Test fun startTripNumbersByTokyoServiceDateAndStoresFareSnapshot() = runBlocking {
        val rule = FareRule(1_000, 500, 200, 100, 10, 75)
        repository.save(DutySession("duty-1", Instant.EPOCH, null))
        val first = repository.startTrip("duty-1", Instant.parse("2026-09-03T14:50:00Z"), rule)
        val second = repository.startTrip("duty-1", Instant.parse("2026-09-03T15:25:00Z"), rule)

        assertEquals("2026-09-03", first.serviceDate)
        assertEquals(1, first.tripNumber)
        assertEquals("2026-09-04", second.serviceDate)
        assertEquals(1, second.tripNumber)
        assertEquals(TripStatus.ACTIVE, repository.findTrip(first.id)!!.status)
        assertEquals(rule.initialDistanceMeters, first.fareInitialDistanceMeters)
    }

    @Test fun completedAndVoidedTripsRemainPersistedAndConsumeNumbers() = runBlocking {
        val rule = FareRule(1_000, 500, 200, 100, 10, 75)
        repository.save(DutySession("duty-1", Instant.EPOCH, null))
        val completed = repository.startTrip("duty-1", Instant.parse("2026-09-03T01:00:00Z"), rule)
        repository.finishTrip(completed.copy(dropoffAt = completed.pickupAt.plusSeconds(60), actualDistanceMeters = 50, chargeableDistanceMeters = 0, lowSpeedSeconds = 60, meteredFareYen = 500, totalFareYen = 500), TripStatus.COMPLETED, true)
        val voided = repository.startTrip("duty-1", Instant.parse("2026-09-03T02:00:00Z"), rule)
        repository.finishTrip(voided.copy(dropoffAt = voided.pickupAt.plusSeconds(30)), TripStatus.VOIDED, false)
        val next = repository.startTrip("duty-1", Instant.parse("2026-09-03T03:00:00Z"), rule)

        assertEquals(TripStatus.COMPLETED, repository.findTrip(completed.id)!!.status)
        assertEquals(50L, repository.findTrip(completed.id)!!.actualDistanceMeters)
        assertEquals(TripStatus.VOIDED, repository.findTrip(voided.id)!!.status)
        assertEquals(2, voided.tripNumber)
        assertEquals(3, next.tripNumber)
    }

    @Test fun pickupFeeIsAddedOnlyOnceForTheSameTripAndType() = runBlocking {
        val trip = createTrip()

        repository.addChargeIfAbsent(trip.id, "PICKUP_FEE", 500, trip.pickupAt, "迎車")
        repository.addChargeIfAbsent(trip.id, "PICKUP_FEE", 500, trip.pickupAt, "迎車")

        assertEquals(1, database.dutyDao().findTripCharges(trip.id).size)
    }

    @Test fun zeroAmountChargeIsNotInserted() = runBlocking {
        val trip = createTrip()

        repository.addChargeIfAbsent(trip.id, "PICKUP_FEE", 0, trip.pickupAt, "迎車")

        assertTrue(database.dutyDao().findTripCharges(trip.id).isEmpty())
    }

    @Test fun waypointsUseOneBasedSequences() = runBlocking {
        val trip = createTrip()
        val place = place(trip.pickupAt)

        repository.addWaypoint(trip.id, place)
        repository.addWaypoint(trip.id, place.copy(latitude = 35.73))
        repository.addWaypoint(trip.id, place.copy(latitude = 35.74))

        assertEquals(listOf(1, 2, 3), database.dutyDao().findTripPlaces(trip.id).map { it.sequence })
    }

    @Test fun dropoffSequenceZeroIsUpdatedInsteadOfDuplicated() = runBlocking {
        val trip = createTrip()

        repository.upsertDropoff(trip.id, place(trip.pickupAt))
        repository.upsertDropoff(trip.id, place(trip.pickupAt.plusSeconds(60)).copy(latitude = 35.74))

        val dropoffs = database.dutyDao().findTripPlaces(trip.id).filter { it.type == "DROPOFF" }
        assertEquals(1, dropoffs.size)
        assertEquals(35.74, dropoffs.single().latitude, 0.0)
    }

    @Test fun passengerCountAndRestFinishFieldsAreUpdated() = runBlocking {
        val trip = createTrip()
        repository.updatePassengerCount(trip.id, 4)
        val restId = repository.startRestInterval("duty-1", Instant.parse("2026-09-03T01:00:00Z"), place(Instant.EPOCH))
        val endedAt = Instant.parse("2026-09-03T02:00:00Z")

        repository.updateRestDistance(restId, 1_250)
        repository.finishRestInterval(restId, endedAt, "AUTO_DISTANCE_1KM")

        assertEquals(4, repository.findTrip(trip.id)!!.passengerCount)
        val rest = database.dutyDao().findRestInterval(restId)
        assertNotNull(rest)
        assertEquals(1_250L, rest!!.distanceMeters)
        assertEquals(endedAt, rest.endedAt)
        assertEquals("AUTO_DISTANCE_1KM", rest.endReason)
    }

    @Test fun placeOperationsWithoutCoordinatesSucceedWithoutSavingPlaces() = runBlocking {
        val trip = createTrip()

        repository.savePickupPlace(trip.id, null)
        repository.addWaypoint(trip.id, null)
        repository.upsertDropoff(trip.id, null)

        assertTrue(database.dutyDao().findTripPlaces(trip.id).isEmpty())
    }

    private suspend fun createTrip(): Trip {
        repository.save(DutySession("duty-1", Instant.EPOCH, null))
        return repository.startTrip("duty-1", Instant.parse("2026-09-03T00:00:00Z"), FareRule(1_000, 500, 200, 100, 10, 75))
    }

    private fun place(recordedAt: Instant) = RecordedPlace(35.7295, 139.7168, 12.0, recordedAt, null, LocationPrecision.UNKNOWN, LocationSource.GPS)
}
