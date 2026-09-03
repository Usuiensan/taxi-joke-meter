package jp.usuiensan.taxijokemeter.domain.location

interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult?
}

data class ReverseGeocodeAddress(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val featureName: String?,
    val featureDetail: String?,
)

data class ReverseGeocodeResult(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val landmarkName: String?,
    val landmarkDetail: String?,
)

fun mapReverseGeocode(address: ReverseGeocodeAddress?): ReverseGeocodeResult? {
    if (address == null) return null
    val featureName = address.featureName?.trim()?.takeIf {
        it.isNotEmpty() && it != address.canonicalAddress?.trim() && it != address.reportAddress?.trim()
    }
    return ReverseGeocodeResult(
        canonicalAddress = address.canonicalAddress?.trim()?.takeIf(String::isNotEmpty),
        reportAddress = address.reportAddress?.trim()?.takeIf(String::isNotEmpty),
        landmarkName = featureName,
        landmarkDetail = address.featureDetail?.trim()?.takeIf { featureName != null && it.isNotEmpty() },
    )
}
