package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MeterControllerOperationsTest {
    @Test fun concurrentRuntimeUpdatesPreserveRestAndGpsFields() {
        MeterRuntimeStore.update { MeterRuntimeSnapshot() }
        val barrier = CyclicBarrier(2)
        val firstRestUpdate = AtomicBoolean(true)
        val firstGpsUpdate = AtomicBoolean(true)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val rest = executor.submit {
                MeterRuntimeStore.update { current ->
                    if (firstRestUpdate.getAndSet(false)) barrier.await(5, TimeUnit.SECONDS)
                    current.copy(restIntervalId = "rest-1")
                }
            }
            val gps = executor.submit {
                MeterRuntimeStore.update { current ->
                    if (firstGpsUpdate.getAndSet(false)) barrier.await(5, TimeUnit.SECONDS)
                    current.copy(savedSampleCount = 1)
                }
            }

            rest.get(5, TimeUnit.SECONDS)
            gps.get(5, TimeUnit.SECONDS)

            assertEquals("rest-1", MeterRuntimeStore.snapshot.value.restIntervalId)
            assertEquals(1, MeterRuntimeStore.snapshot.value.savedSampleCount)
        } finally {
            executor.shutdownNow()
            MeterRuntimeStore.update { MeterRuntimeSnapshot() }
        }
    }

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
