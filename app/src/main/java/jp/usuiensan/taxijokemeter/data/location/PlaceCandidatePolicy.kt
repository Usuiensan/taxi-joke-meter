package jp.usuiensan.taxijokemeter.data.location

import kotlin.math.*

data class PlaceCandidate(val name: String, val latitude: Double, val longitude: Double, val primaryType: String?, val types: List<String>)

internal fun chooseFacilityCandidate(latitude: Double, longitude: Double, accuracyMeters: Double, candidates: List<PlaceCandidate>): PlaceCandidate? {
    if (accuracyMeters > 30.0) return null
    val accepted = candidates.mapNotNull { candidate ->
        if (!candidate.isPoi()) return@mapNotNull null
        val distance = distanceMeters(latitude, longitude, candidate.latitude, candidate.longitude)
        if (distance <= maxOf(30.0, accuracyMeters + 20.0)) candidate to distance else null
    }.sortedBy { it.second }
    val nearest = accepted.firstOrNull() ?: return null
    if (accepted.getOrNull(1)?.second?.minus(nearest.second)?.let { it < 10.0 } == true) return null
    return nearest.first
}

private fun distanceMeters(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
    val radius = 6_371_000.0
    val dLat = Math.toRadians(latitude2 - latitude1)
    val dLon = Math.toRadians(longitude2 - longitude1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude1)) * cos(Math.toRadians(latitude2)) * sin(dLon / 2).pow(2)
    return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun PlaceCandidate.isPoi(): Boolean {
    val blocked = setOf("locality", "administrative_area_level_1", "administrative_area_level_2", "sublocality", "neighborhood", "postal_code")
    return primaryType !in blocked && types.none { it in blocked }
}
