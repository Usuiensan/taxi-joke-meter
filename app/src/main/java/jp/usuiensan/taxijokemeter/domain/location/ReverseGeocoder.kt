package jp.usuiensan.taxijokemeter.domain.location

interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult?
}

data class ReverseGeocodeAddress(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val featureName: String?,
    val featureDetail: String?,
    val roadName: String? = null,
    val areaText: String? = null,
)

data class ReverseGeocodeResult(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val landmarkName: String?,
    val landmarkDetail: String?,
    val roadName: String? = null,
    val areaText: String? = null,
    val facilityName: String? = null,
)

fun mapReverseGeocode(address: ReverseGeocodeAddress?): ReverseGeocodeResult? {
    if (address == null) return null
    return ReverseGeocodeResult(
        canonicalAddress = address.canonicalAddress?.trim()?.takeIf(String::isNotEmpty),
        reportAddress = address.reportAddress?.trim()?.takeIf(String::isNotEmpty),
        landmarkName = facilityName(address),
        landmarkDetail = null,
        roadName = address.roadName?.trim()?.takeIf(::isJapaneseDisplayText),
        areaText = address.areaText?.trim()?.takeIf(String::isNotEmpty),
        facilityName = facilityName(address),
    )
}

fun japaneseAddress(locality: String?, subLocality: String?, subThoroughfare: String?, addressLine: String?): String? {
    val parts = listOf(locality, subLocality, subThoroughfare)
        .mapNotNull { it?.trim()?.takeIf(::isJapaneseAddressPart) }
        .distinct()
    return (if (parts.isNotEmpty()) parts.joinToString("") else addressLine?.trim()?.takeIf(::isJapaneseDisplayText))
        ?.takeIf(String::isNotEmpty)
}

fun japaneseRoadName(thoroughfare: String?, featureName: String?): String? =
    listOf(thoroughfare, featureName).firstOrNull { value ->
        value?.trim()?.let { isJapaneseDisplayText(it) && (it == thoroughfare || it.contains("道") || it.contains("国道") || it.contains("県道") || it.contains("府道")) } == true
    }?.trim()

private fun isJapaneseDisplayText(value: String): Boolean = value.any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' }

private fun isJapaneseAddressPart(value: String): Boolean = isJapaneseDisplayText(value) || value.matches(Regex("[0-9０-９-]+"))

private fun looksLikeAddress(value: String): Boolean = value.matches(Regex(".*(丁目|番地?|号|^[0-9０-９-]+$).*"))

private fun facilityName(address: ReverseGeocodeAddress): String? = address.featureName?.trim()?.takeIf {
    isJapaneseDisplayText(it) && it != address.roadName && !looksLikeAddress(it)
}
