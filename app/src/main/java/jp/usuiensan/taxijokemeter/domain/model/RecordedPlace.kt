package jp.usuiensan.taxijokemeter.domain.model

import java.time.Instant

enum class LocationPrecision { UNKNOWN, POINT, BLOCK, DISTRICT, TOWN }

enum class LocationSource { GPS, NETWORK, MANUAL, REVERSE_GEOCODER, UNKNOWN }

data class RecordedPlace(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: Instant,
    val addressText: String?,
    val locationPrecision: LocationPrecision,
    val source: LocationSource,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(accuracyMeters == null || accuracyMeters >= 0.0)
    }
}
