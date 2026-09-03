package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterControllerOperationsTest {
    @Test fun startsOneTripOnlyFromTripStartStates() {
        assertTrue(startsNewTrip(MeterState.EMPTY))
        assertTrue(startsNewTrip(MeterState.PICKUP))
        assertTrue(startsNewTrip(MeterState.REST))
        assertTrue(startsNewTrip(MeterState.TOTAL))
        assertFalse(startsNewTrip(MeterState.PAYMENT))
    }

    @Test fun tripStartChargesUseOnlyPositiveConfiguredFees() {
        assertEquals(setOf("PICKUP_FEE", "RESERVATION"), tripStartChargeTypes(MeterState.PICKUP, true, 400, 500))
        assertEquals(emptySet<String>(), tripStartChargeTypes(MeterState.EMPTY, true, 0, 0))
    }

    @Test fun passengerCountIsClampedToOneThroughNine() {
        assertEquals(1, clampPassengerCount(0))
        assertEquals(9, clampPassengerCount(12))
    }

    @Test fun measurementStateRetainsRestAndDeadhead() {
        assertEquals(OccupancyState.REST, measurementOccupancyState(MeterState.REST))
        assertEquals(OccupancyState.DEADHEAD, measurementOccupancyState(MeterState.DEADHEAD))
        assertEquals(OccupancyState.OCCUPIED, measurementOccupancyState(MeterState.OCCUPIED))
    }
}
