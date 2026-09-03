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
            mapReverseGeocode(
                ReverseGeocodeAddress(
                    canonicalAddress = address.getAddressLine(0),
                    reportAddress = japaneseAddress(address.locality, address.subLocality, address.subThoroughfare, address.getAddressLine(0)),
                    featureName = address.featureName,
                    featureDetail = null,
                    roadName = japaneseRoadName(address.thoroughfare, address.featureName),
                    areaText = japaneseAddress(address.locality, address.subLocality, address.subThoroughfare, address.getAddressLine(0)),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}
