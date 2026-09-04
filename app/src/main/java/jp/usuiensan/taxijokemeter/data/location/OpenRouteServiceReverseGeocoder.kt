package jp.usuiensan.taxijokemeter.data.location

import android.util.Log
import jp.usuiensan.taxijokemeter.BuildConfig
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.location.japaneseAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** ORS/Pelias is the single production reverse-geocoding source. */
class OpenRouteServiceReverseGeocoder(
    private val apiKey: String = BuildConfig.ORS_API_KEY,
    private val endpoint: String = "https://api.heigit.org/pelias/v1/reverse",
) : ReverseGeocoder {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val url = URL("$endpoint?point.lat=$latitude&point.lon=$longitude&size=1&api_key=${URLEncoder.encode(apiKey, "UTF-8")}")
        try {
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (responseCode !in 200..299) return@withContext null
                inputStream.bufferedReader().use { parse(it.readText()) }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w(TAG, "ORS reverse geocode failed", e)
            null
        }
    }

    internal fun parse(body: String): ReverseGeocodeResult? {
        val feature = JSONObject(body).optJSONArray("features")?.optJSONObject(0) ?: return null
        val p = feature.optJSONObject("properties") ?: return null
        val label = p.optString("label").takeIf(String::isNotBlank)
        val layer = p.optString("layer").takeIf(String::isNotBlank)
        val name = p.optString("name").takeIf(String::isNotBlank)
        val street = p.optString("street").takeIf(String::isNotBlank)
        val road = when (layer) {
            "street" -> street ?: name
            "address", "venue" -> street
            else -> null
        }
        val house = p.optString("housenumber").takeIf(String::isNotBlank)
        val postal = p.optString("postalcode").takeIf(String::isNotBlank)
        val municipality = p.optString("municipality").takeIf(String::isNotBlank)
        val locality = p.optString("locality").takeIf(String::isNotBlank)
        val district = p.optString("district").takeIf(String::isNotBlank)
        val facility = name?.takeIf { layer == "venue" && it != road }
        val address = japaneseAddress(municipality, listOfNotNull(locality, district).distinct().joinToString(""), house, null)
        return ReverseGeocodeResult(
            canonicalAddress = label ?: address,
            reportAddress = address,
            landmarkName = facility,
            landmarkDetail = null,
            roadName = road,
            areaText = address,
            facilityName = facility,
            municipality = municipality,
            town = locality,
            houseNumber = house,
            formattedAddress = label,
        )
    }

    private companion object { const val TAG = "OpenRouteService" }
}
