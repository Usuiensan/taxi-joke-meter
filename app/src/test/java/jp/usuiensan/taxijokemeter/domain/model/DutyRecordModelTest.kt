package jp.usuiensan.taxijokemeter.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DutyRecordModelTest {
    private val now = Instant.parse("2026-09-03T00:00:00Z")

    @Test fun supportsAllDutyEventTypes() {
        assertEquals(9, DutyEventType.entries.size)
    }

    @Test fun mapsFareAndSafetyModesIndependently() {
        assertEquals(FareRoadMode.GENERAL, RoadMode.GENERAL.fare)
        assertEquals(SafetyRoadMode.GENERAL_60, RoadMode.GENERAL.safety)
        assertEquals(FareRoadMode.HIGHWAY, RoadMode.HIGHWAY1.fare)
        assertEquals(SafetyRoadMode.HIGHWAY_90, RoadMode.HIGHWAY1.safety)
        assertEquals(FareRoadMode.HIGHWAY, RoadMode.HIGHWAY2.fare)
        assertEquals(SafetyRoadMode.HIGHWAY_90, RoadMode.HIGHWAY2.safety)
    }

    @Test fun keepsTripFieldsAndUnresolvedPlace() {
        val place = RecordedPlace(35.7295, 139.7168, 12.0, now, null, LocationPrecision.UNKNOWN, LocationSource.GPS)
        val trip = Trip("trip-1", now, now.plusSeconds(600), place, null, 2, 1_250, 1_500, 200, PaymentMethod.CASH, "備考")

        assertEquals(2, trip.passengerCount)
        assertEquals(1_250, trip.operatingDistanceMeters)
        assertEquals(null, trip.dropoffPlace)
        assertEquals(LocationPrecision.UNKNOWN, place.locationPrecision)
    }

    @Test fun groupsTripsAndEventsInDutySession() {
        val session = DutySession(
            id = "duty-1",
            startedAt = now,
            endedAt = null,
            trips = listOf(),
            events = listOf(DutyEvent("event-1", DutyEventType.DEPARTURE, now, null, null)),
        )

        assertEquals(DutyEventType.DEPARTURE, session.events.single().type)
    }
}
