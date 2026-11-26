package jp.usuiensan.taxijokemeter.data.location

import android.util.Log
import jp.usuiensan.taxijokemeter.BuildConfig
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenRouteService (Pelias) のリバースジオコーディングを使う逆ジオコーダ。
 * API キー（Gradle プロパティ orsApiKey → BuildConfig.ORS_API_KEY）が空なら常に null を返す。
 */
class OrsReverseGeocoder(
    private val apiKey: String = BuildConfig.ORS_API_KEY,
) : ReverseGeocoder {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                Log.w(TAG, "ORS無効: API key未設定")
                return@withContext null
            }
            val connection = (URL("$ENDPOINT?point.lat=$latitude&point.lon=$longitude&boundary.country=JP&size=1")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", apiKey)
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    when (status) {
                        401, 403 -> Log.e(TAG, "ORS authentication/restriction error HTTP $status")
                        429 -> Log.e(TAG, "ORS quota error HTTP $status")
                        else -> Log.e(TAG, "ORS HTTP error $status")
                    }
                    return@withContext null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val result = parseOrsReverseGeocode(body)
                if (result == null) Log.i(TAG, "ORS zero results")
                result
            } catch (exception: java.net.SocketTimeoutException) {
                Log.e(TAG, "ORS timeout", exception)
                null
            } catch (exception: org.json.JSONException) {
                Log.e(TAG, "ORS parse error", exception)
                null
            } catch (exception: Exception) {
                Log.e(TAG, "ORS request error", exception)
                null
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TAG = "OrsReverseGeocoder"
        const val ENDPOINT = "https://api.openrouteservice.org/geocode/reverse"
    }
}

internal fun parseOrsReverseGeocode(body: String): ReverseGeocodeResult? {
    val features = JSONObject(body).optJSONArray("features") ?: return null
    val properties = features.optJSONObject(0)?.optJSONObject("properties") ?: return null
    fun prop(name: String): String? = properties.optString(name).takeIf(String::isNotBlank)

    val locality = prop("locality")
    val localadmin = prop("localadmin")
    val municipality = listOfNotNull(
        locality,
        localadmin?.takeIf { locality == null || !locality.contains(it) },
    ).joinToString("").takeIf(String::isNotEmpty)
    val town = prop("neighbourhood")
    val street = prop("street")
    val houseNumber = prop("housenumber")
    val name = prop("name")
    val layer = prop("layer")
    val roadName = (street ?: name?.takeIf { layer == "street" })
        ?.takeIf(::isJapaneseOrsText)
    val areaText = listOfNotNull(municipality, town).joinToString("").takeIf(String::isNotEmpty)
    val reportAddress = listOfNotNull(municipality, town, houseNumber)
        .joinToString("").takeIf(String::isNotEmpty)
    val region = prop("region")
    val canonicalAddress = listOfNotNull(region, reportAddress)
        .joinToString("").takeIf(String::isNotEmpty)
    return ReverseGeocodeResult(
        canonicalAddress = canonicalAddress,
        reportAddress = reportAddress,
        landmarkName = null,
        landmarkDetail = null,
        roadName = roadName,
        areaText = areaText,
        facilityName = null,
        municipality = municipality,
        town = town,
        chome = null,
        houseNumber = houseNumber,
        formattedAddress = prop("label"),
    )
}

private fun isJapaneseOrsText(value: String): Boolean =
    value.any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' }
