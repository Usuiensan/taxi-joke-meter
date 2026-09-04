package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationHealthTrackerTest {
    @Test fun freshGoodGpsBlocksAWeakerNetworkFix() {
        assertEquals(true, shouldIgnoreNetworkFix(1_000_000_000L, 8.0, 4_000_000_000L))
        assertEquals(false, shouldIgnoreNetworkFix(1_000_000_000L, 8.0, 6_000_000_000L))
    }

    @Test fun transitionsFromValidToStaleToUnavailableUsingMonotonicTime() {
        val tracker = LocationHealthTracker()
        tracker.recordFix(1_000_000_000L)

        assertEquals(LocationHealth.VALID, tracker.healthAt(5_999_999_999L))
        assertEquals(LocationHealth.STALE, tracker.healthAt(6_000_000_000L))
        assertEquals(LocationHealth.UNAVAILABLE, tracker.healthAt(16_000_000_000L))
    }

    @Test fun newFixRestoresValidHealth() {
        val tracker = LocationHealthTracker()
        tracker.recordFix(0L)

        assertEquals(LocationHealth.STALE, tracker.healthAt(5_000_000_000L))
        tracker.recordFix(5_100_000_000L)

        assertEquals(LocationHealth.VALID, tracker.healthAt(5_100_000_001L))
    }
}
