package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import kotlinx.coroutines.CancellationException

/**
 * 合成順序: オフラインDB（主）→ ORS → Google。
 * - オフラインで結果が完結している（通り名まで取れている）場合は通信しない。
 * - オフライン結果はあるが通り名が無い場合は、ORS に通り名だけを聞いてマージする。
 * - オフラインが null の場合は ORS、それも null なら Google にフォールバックする。
 */
class CompositeReverseGeocoder(
    private val offline: ReverseGeocoder,
    private val ors: ReverseGeocoder,
    private val google: ReverseGeocoder,
) : ReverseGeocoder {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? {
        val offlineResult = offline.reverseGeocode(latitude, longitude)
        if (offlineResult != null) {
            if (offlineResult.roadName != null) return offlineResult
            val orsRoadName = try {
                ors.reverseGeocode(latitude, longitude)?.roadName
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }
            return if (orsRoadName != null) offlineResult.copy(roadName = orsRoadName) else offlineResult
        }
        val orsResult = try {
            ors.reverseGeocode(latitude, longitude)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        return orsResult ?: google.reverseGeocode(latitude, longitude)
    }
}
