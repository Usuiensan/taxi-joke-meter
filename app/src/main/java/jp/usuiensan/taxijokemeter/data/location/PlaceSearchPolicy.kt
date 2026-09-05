package jp.usuiensan.taxijokemeter.data.location

import kotlin.math.*

internal class StationaryPlacesTracker {
    private var stoppedAtMillis: Long? = null
    private var searchedThisStop = false
    private var anchorLatitude: Double? = null
    private var anchorLongitude: Double? = null
    private var movingSinceMillis: Long? = null
    private var lastSearchLatitude: Double? = null
    private var lastSearchLongitude: Double? = null

    fun reset() {
        stoppedAtMillis = null
        searchedThisStop = false
        anchorLatitude = null
        anchorLongitude = null
        movingSinceMillis = null
        lastSearchLatitude = null
        lastSearchLongitude = null
    }

    fun update(nowMillis: Long, speedMps: Double?, goodGps: Boolean, latitude: Double, longitude: Double): Boolean {
        val speedKmh = speedMps?.times(3.6)
        if (speedKmh != null && speedKmh > 3.0) {
            val movingSince = movingSinceMillis ?: nowMillis.also { movingSinceMillis = it }
            if (nowMillis - movingSince < 3_000L && stoppedAtMillis != null) return false
            stoppedAtMillis = null
            searchedThisStop = false
            anchorLatitude = null
            anchorLongitude = null
            return false
        }
        movingSinceMillis = null
        if (!goodGps || speedKmh == null) return false
        if (stoppedAtMillis == null) {
            stoppedAtMillis = nowMillis
            anchorLatitude = latitude
            anchorLongitude = longitude
        }
        if (anchorLatitude != null && distanceMeters(anchorLatitude!!, anchorLongitude!!, latitude, longitude) > 30.0) {
            stoppedAtMillis = nowMillis
            searchedThisStop = false
            anchorLatitude = latitude
            anchorLongitude = longitude
        }
        if (searchedThisStop || nowMillis - stoppedAtMillis!! < 60_000L) return false
        val movedEnough = lastSearchLatitude == null || distanceMeters(lastSearchLatitude!!, lastSearchLongitude!!, latitude, longitude) >= 300.0
        if (!movedEnough) return false
        searchedThisStop = true
        lastSearchLatitude = latitude
        lastSearchLongitude = longitude
        return true
    }
}

private fun distanceMeters(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
    val dLat = Math.toRadians(latitude2 - latitude1)
    val dLon = Math.toRadians(longitude2 - longitude1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude1)) * cos(Math.toRadians(latitude2)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}
