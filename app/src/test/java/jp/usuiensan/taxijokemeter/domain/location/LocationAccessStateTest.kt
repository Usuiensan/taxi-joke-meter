package jp.usuiensan.taxijokemeter.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationAccessStateTest {
    @Test fun noPermissionBeforeFirstRequestIsPermissionNone() {
        assertEquals(LocationAccessState.LOCATION_PERMISSION_NONE, locationAccessState(false, false, true, false, false))
    }

    @Test fun deniedWithoutRetryIsSettingsRequired() {
        assertEquals(LocationAccessState.PERMISSION_SETTINGS_REQUIRED, locationAccessState(false, false, true, true, false))
    }

    @Test fun coarsePermissionRemainsUsable() {
        assertEquals(LocationAccessState.LOCATION_PERMISSION_COARSE, locationAccessState(false, true, true, true, false))
    }

    @Test fun enabledPermissionWithLocationOffNeedsLocationSettings() {
        assertEquals(LocationAccessState.LOCATION_SERVICE_OFF, locationAccessState(true, false, false, true, false))
    }
}
