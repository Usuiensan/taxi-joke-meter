package jp.usuiensan.taxijokemeter.domain.location

enum class LocationAccessState {
    LOCATION_PERMISSION_NONE,
    LOCATION_PERMISSION_COARSE,
    LOCATION_PERMISSION_FINE,
    LOCATION_SERVICE_OFF,
    LOCATION_READY,
    PERMISSION_SETTINGS_REQUIRED,
}

fun locationAccessState(
    fineGranted: Boolean,
    coarseGranted: Boolean,
    locationServiceEnabled: Boolean,
    requestedOnce: Boolean,
    requestCanBeRetried: Boolean,
): LocationAccessState = when {
    !fineGranted && !coarseGranted && requestedOnce && !requestCanBeRetried -> LocationAccessState.PERMISSION_SETTINGS_REQUIRED
    !fineGranted && !coarseGranted -> LocationAccessState.LOCATION_PERMISSION_NONE
    !locationServiceEnabled -> LocationAccessState.LOCATION_SERVICE_OFF
    fineGranted -> LocationAccessState.LOCATION_PERMISSION_FINE
    else -> LocationAccessState.LOCATION_PERMISSION_COARSE
}
