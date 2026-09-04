package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import jp.usuiensan.taxijokemeter.BuildConfig

class GooglePlacesFacilitySearcher(context: Context) {
    private val client: PlacesClient? = BuildConfig.GOOGLE_MAPS_API_KEY.takeIf(String::isNotBlank)?.let {
        runCatching {
            if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context, it)
            Places.createClient(context)
        }.onFailure { Log.e(TAG, "Places初期化失敗", it) }.getOrNull()
    }

    fun search(latitude: Double, longitude: Double, accuracyMeters: Double, callback: (String?) -> Unit) {
        val placesClient = client ?: return callback(null)
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.PRIMARY_TYPE, Place.Field.TYPES)
        val request = SearchNearbyRequest.builder(
            CircularBounds.newInstance(LatLng(latitude, longitude), 100.0), fields,
        ).setMaxResultCount(10).setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE).build()
        placesClient.searchNearby(request)
            .addOnSuccessListener { response ->
                val candidates = response.places.mapNotNull { place ->
                    val position = place.location ?: return@mapNotNull null
                    val name = place.displayName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    PlaceCandidate(name, position.latitude, position.longitude, place.primaryType, place.placeTypes ?: emptyList())
                }
                callback(chooseFacilityCandidate(latitude, longitude, accuracyMeters, candidates)?.name)
            }
            .addOnFailureListener { Log.e(TAG, "Places Nearby検索失敗", it); callback(null) }
    }

    private companion object { const val TAG = "GooglePlaces" }
}
