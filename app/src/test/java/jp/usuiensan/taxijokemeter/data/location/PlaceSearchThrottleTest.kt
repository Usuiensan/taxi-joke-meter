package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchThrottleTest {
    @Test fun firstGoodFixIsAllowed() = assertTrue(shouldRefreshPlaces(null, null, null, 0L, 35.0, 135.0))
    @Test fun intervalAndMovementAreRequiredAfterFirstSearch() {
        assertFalse(shouldRefreshPlaces(0L, 35.0, 135.0, 30_000L, 35.0005, 135.0))
        assertTrue(shouldRefreshPlaces(0L, 35.0, 135.0, 30_000L, 35.001, 135.0))
    }
}
