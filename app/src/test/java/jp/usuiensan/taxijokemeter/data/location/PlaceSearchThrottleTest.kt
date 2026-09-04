package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchThrottleTest {
    @Test fun firstGoodFixStartsStationaryTimerButDoesNotSearchImmediately() {
        val tracker = StationaryPlacesTracker()
        assertFalse(tracker.update(0L, 0.0, true, 35.0, 135.0))
        assertFalse(tracker.update(59_999L, 0.0, true, 35.0, 135.0))
        assertTrue(tracker.update(60_000L, 0.0, true, 35.0, 135.0))
    }

    @Test fun thirtySecondStopDoesNotSearch() {
        val tracker = StationaryPlacesTracker()
        tracker.update(0L, 0.0, true, 35.0, 135.0)
        assertFalse(tracker.update(30_000L, 0.0, true, 35.0, 135.0))
    }

    @Test fun sameStopIsSearchedOnlyOnceAndSmallSpeedJitterDoesNotReset() {
        val tracker = StationaryPlacesTracker()
        tracker.update(0L, 0.0, true, 35.0, 135.0)
        assertTrue(tracker.update(60_000L, 0.0, true, 35.0, 135.0))
        assertFalse(tracker.update(61_000L, 1.5 / 3.6, true, 35.0, 135.0))
        assertFalse(tracker.update(120_000L, 0.0, true, 35.0, 135.0))
    }

    @Test fun movementResetsStopAndRequiresNewSixtySecondStop() {
        val tracker = StationaryPlacesTracker()
        tracker.update(0L, 0.0, true, 35.0, 135.0)
        assertTrue(tracker.update(60_000L, 0.0, true, 35.0, 135.0))
        assertFalse(tracker.update(61_000L, 3.0 / 3.6, true, 35.0, 135.0))
        assertFalse(tracker.update(120_000L, 0.0, true, 35.003, 135.0))
        assertTrue(tracker.update(180_000L, 0.0, true, 35.003, 135.0))
    }
}
