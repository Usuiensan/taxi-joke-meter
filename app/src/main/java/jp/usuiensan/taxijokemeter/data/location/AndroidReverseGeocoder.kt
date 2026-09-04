package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.location.Geocoder
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeAddress
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.location.mapReverseGeocode
import jp.usuiensan.taxijokemeter.domain.location.japaneseAddress
import jp.usuiensan.taxijokemeter.domain.location.japaneseRoadName
import kotlinx.coroutines.CancellationException
import java.util.Locale

class AndroidReverseGeocoder(context: Context) : ReverseGeocoder {
    private val geocoder = Geocoder(context, Locale.JAPAN)

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? {
        return try {
            if (!Geocoder.isPresent()) return null
            val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() ?: return null
            val subLocality = address.subLocality
            val chomeMatch = Regex("(.+?)([0-9０-９]+丁目)$").matchEntire(subLocality.orEmpty())
            val roadName = japaneseRoadName(address.thoroughfare, address.featureName)
            val reportAddress = japaneseAddress(address.locality, address.subLocality, address.subThoroughfare, address.getAddressLine(0), address.thoroughfare.takeIf { roadName == null })
            mapReverseGeocode(
                ReverseGeocodeAddress(
                    canonicalAddress = address.getAddressLine(0),
                    reportAddress = reportAddress,
                    featureName = address.featureName,
                    featureDetail = null,
                    roadName = roadName,
                    areaText = reportAddress,
                    municipality = address.locality,
                    town = chomeMatch?.groupValues?.get(1) ?: subLocality,
                    chome = chomeMatch?.groupValues?.get(2),
                    aza = address.thoroughfare.takeIf { roadName == null },
                    block = address.premises,
                    houseNumber = address.subThoroughfare,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}
