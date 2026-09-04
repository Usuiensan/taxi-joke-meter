package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import jp.usuiensan.taxijokemeter.BuildConfig
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleReverseGeocoder(
    private val context: Context,
    private val apiKey: String = BuildConfig.GOOGLE_MAPS_API_KEY,
) : ReverseGeocoder {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Google Geocoding無効: API key未設定")
            return@withContext null
        }
        val connection = (URL("$ENDPOINT/$latitude,$longitude?languageCode=ja&regionCode=JP").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
            setRequestProperty("X-Android-Package", context.packageName)
            certificateSha1(context)?.let { setRequestProperty("X-Android-Cert", it) }
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                when (status) {
                    401, 403 -> Log.e(TAG, "Google Geocoding authentication/restriction error HTTP $status")
                    429 -> Log.e(TAG, "Google Geocoding quota error HTTP $status")
                    else -> Log.e(TAG, "Google Geocoding HTTP error $status")
                }
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val result = parseGoogleReverseGeocode(body)
            if (result == null) Log.i(TAG, "Google Geocoding zero results")
            result
        } catch (exception: java.net.SocketTimeoutException) {
            Log.e(TAG, "Google Geocoding timeout", exception)
            null
        } catch (exception: org.json.JSONException) {
            Log.e(TAG, "Google Geocoding parse error", exception)
            null
        } catch (exception: Exception) {
            Log.e(TAG, "Google Geocoding request error", exception)
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "GoogleReverseGeocoder"
        const val ENDPOINT = "https://geocode.googleapis.com/v4/geocode/location"
        const val FIELD_MASK = "results.formattedAddress,results.addressComponents,results.types,results.placeId,results.location,results.granularity"
    }
}

internal fun parseGoogleReverseGeocode(body: String): ReverseGeocodeResult? {
    val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
    val components = buildList {
        val array = result.optJSONArray("addressComponents") ?: return@buildList
        for (index in 0 until array.length()) {
            val component = array.optJSONObject(index) ?: continue
            add(GoogleAddressComponent(
                component.optString("longText").takeIf(String::isNotBlank),
                component.optString("shortText").takeIf(String::isNotBlank),
                component.optJSONArray("types")?.let { types -> buildSet { for (i in 0 until types.length()) add(types.optString(i)) } } ?: emptySet(),
                component.optString("languageCode").takeIf(String::isNotBlank),
            ))
        }
    }
    fun find(vararg types: String): String? = components.firstOrNull { it.types.any(types::contains) && it.longText?.isJapanese() == true }?.longText
        ?: components.firstOrNull { it.types.any(types::contains) }?.longText
    val route = find("route")
    val municipality = find("locality") ?: find("administrative_area_level_2") ?: find("sublocality_level_1", "sublocality")
    val town = find("sublocality_level_1", "sublocality", "neighborhood")?.takeUnless { it == municipality }
    val chome = find("sublocality_level_2")?.takeUnless { it == municipality || it == town }
    val houseNumber = find("street_number")
    val japaneseParts = listOf(municipality, town, chome, houseNumber).filter { it?.isJapanese() == true || it?.matches(Regex("[0-9０-９-]+")) == true }
    val reportAddress = japaneseParts.distinct().joinToString("").takeIf(String::isNotBlank)
    return ReverseGeocodeResult(
        canonicalAddress = result.optString("formattedAddress").takeIf(String::isNotBlank),
        reportAddress = reportAddress,
        landmarkName = null,
        landmarkDetail = null,
        roadName = route?.takeIf { it.isNotBlank() },
        areaText = reportAddress,
        facilityName = null,
        municipality = municipality,
        town = town,
        chome = chome,
        houseNumber = houseNumber,
        formattedAddress = result.optString("formattedAddress").takeIf(String::isNotBlank),
    )
}

private data class GoogleAddressComponent(val longText: String?, val shortText: String?, val types: Set<String>, val languageCode: String?)

private fun String.isJapanese(): Boolean = any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' }

private fun certificateSha1(context: Context): String? = runCatching {
    val packageInfo = if (Build.VERSION.SDK_INT >= 28) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    }
    val signature = if (Build.VERSION.SDK_INT >= 28) packageInfo.signingInfo?.apkContentsSigners?.firstOrNull() ?: error("no signing certificate") else {
        @Suppress("DEPRECATION") packageInfo.signatures?.firstOrNull() ?: error("no signing certificate")
    }
    MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString(":") { "%02X".format(it) }
}.getOrNull()
