package jp.usuiensan.taxijokemeter.domain.location

interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult?
}

const val PRECISE_MAX_METERS = 30.0
const val BLOCK_MAX_METERS = 100.0
const val TOWN_MAX_METERS = 300.0

data class ReverseGeocodeAddress(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val featureName: String?,
    val featureDetail: String?,
    val roadName: String? = null,
    val areaText: String? = null,
    val municipality: String? = null,
    val town: String? = null,
    val chome: String? = null,
    val aza: String? = null,
    val block: String? = null,
    val houseNumber: String? = null,
)

data class ReverseGeocodeResult(
    val canonicalAddress: String?,
    val reportAddress: String?,
    val landmarkName: String?,
    val landmarkDetail: String?,
    val roadName: String? = null,
    val areaText: String? = null,
    val facilityName: String? = null,
    val municipality: String? = null,
    val town: String? = null,
    val chome: String? = null,
    val aza: String? = null,
    val block: String? = null,
    val houseNumber: String? = null,
    val formattedAddress: String? = null,
)

fun reportAddressForAccuracy(result: ReverseGeocodeResult, accuracyMeters: Double): String? {
    val municipality = result.municipality?.trim()?.takeIf(String::isNotEmpty)
    val town = result.town?.trim()?.takeIf(String::isNotEmpty)
    val chome = result.chome?.trim()?.takeIf(String::isNotEmpty)
    val aza = result.aza?.trim()?.takeIf(String::isNotEmpty)
    val precise = result.reportAddress?.trim()?.takeIf(String::isNotEmpty)
        ?: listOf(municipality, town, chome, aza, result.block, result.houseNumber)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString("").takeIf(String::isNotEmpty)
    if (!accuracyMeters.isFinite() || accuracyMeters <= PRECISE_MAX_METERS) return precise
    val area = when {
        accuracyMeters <= BLOCK_MAX_METERS -> listOf(municipality, town, chome, aza)
        accuracyMeters <= TOWN_MAX_METERS -> listOf(municipality, town, aza)
        else -> listOf(municipality, town, aza)
    }.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString("")
    return area.takeIf(String::isNotEmpty) ?: result.areaText ?: precise
}

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
        municipality = address.municipality,
        town = address.town,
        chome = address.chome,
        aza = address.aza,
        block = address.block,
        houseNumber = address.houseNumber,
        formattedAddress = address.canonicalAddress,
    )
}

fun japaneseAddress(locality: String?, subLocality: String?, subThoroughfare: String?, addressLine: String?): String? {
    val parts = listOf(locality, subLocality, subThoroughfare)
        .mapNotNull { it?.trim()?.takeIf(::isJapaneseAddressPart) }
        .distinct()
    val fields = parts.joinToString("")
    val line = addressLine?.let(::cleanJapaneseAddressLine)
    return (line?.takeIf { looksLikeAddress(it) && it.length >= fields.length } ?: fields.takeIf { it.isNotEmpty() } ?: line)
        ?.takeIf(String::isNotEmpty)
}

fun japaneseRoadName(thoroughfare: String?, featureName: String?): String? =
    listOf(thoroughfare, featureName).firstOrNull { value ->
        value?.trim()?.let { isJapaneseDisplayText(it) && (it == thoroughfare || it.contains("道") || it.contains("国道") || it.contains("県道") || it.contains("府道")) } == true
    }?.trim()

private fun isJapaneseDisplayText(value: String): Boolean = value.any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' }

private fun isJapaneseAddressPart(value: String): Boolean = isJapaneseDisplayText(value) || value.matches(Regex("[0-9０-９-]+"))

private fun looksLikeAddress(value: String): Boolean = value.matches(Regex(".*(丁目|番地?|号|^[0-9０-９-]+$).*"))

private fun cleanJapaneseAddressLine(value: String): String? {
    if (!isJapaneseDisplayText(value)) return null
    return value.trim()
        .replace(Regex("〒?\\d{3}-?\\d{4}\\s*"), "")
        .replace(Regex("^(日本[、,\\s]*)"), "")
        .replace(Regex("^(北海道|東京都|京都府|大阪府|.{2,3}県)"), "")
        .replace(Regex("[、,]"), "")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun facilityName(address: ReverseGeocodeAddress): String? = address.featureName?.trim()?.takeIf {
    isJapaneseDisplayText(it) && it != address.roadName && !looksLikeAddress(it)
}
