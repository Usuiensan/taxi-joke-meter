package jp.usuiensan.taxijokemeter.data.location

import kotlin.math.*

internal fun shouldRefreshPlaces(lastAtMillis: Long?, lastLatitude: Double?, lastLongitude: Double?, nowMillis: Long, latitude: Double, longitude: Double): Boolean {
    if (lastAtMillis == null || lastLatitude == null || lastLongitude == null) return true
    if (nowMillis - lastAtMillis < 30_000L) return false
    val dLat = Math.toRadians(latitude - lastLatitude)
    val dLon = Math.toRadians(longitude - lastLongitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lastLatitude)) * cos(Math.toRadians(latitude)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a)) >= 75.0
}
