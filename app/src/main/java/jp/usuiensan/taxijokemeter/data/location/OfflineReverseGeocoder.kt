package jp.usuiensan.taxijokemeter.data.location

import android.content.Context
import android.util.Log
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeResult
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.location.mapReverseGeocode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 同梱のオフライン住所DB（address_db.bin）だけを使う逆ジオコーダ。通信は行わない。
 * データ出典: 国土交通省「位置参照情報」、道路は © OpenStreetMap contributors。
 */
class OfflineReverseGeocoder(private val context: Context) : ReverseGeocoder {

    @Volatile
    private var database: AddressDatabase? = null

    @Volatile
    private var loadFailed: Boolean = false

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? =
        withContext(Dispatchers.IO) {
            val db = loadDatabase() ?: return@withContext null
            try {
                val hit = db.lookup(latitude, longitude) ?: return@withContext null
                val roadName = runCatching { db.nearestRoadName(latitude, longitude) }.getOrNull()
                mapReverseGeocode(buildOfflineAddress(hit, roadName))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "オフライン住所引きに失敗", exception)
                null
            }
        }

    private fun loadDatabase(): AddressDatabase? {
        database?.let { return it }
        if (loadFailed) return null
        synchronized(this) {
            database?.let { return it }
            if (loadFailed) return null
            val loaded = try {
                context.assets.open(ASSET_NAME).use { it.readBytes() }
                    .let { AddressDatabase.load(it) }
            } catch (exception: Exception) {
                Log.e(TAG, "オフライン住所DBの読み込みに失敗", exception)
                null
            }
            if (loaded == null) loadFailed = true
            database = loaded
            return loaded
        }
    }

    private companion object {
        const val TAG = "OfflineReverseGeocoder"
        const val ASSET_NAME = "address_db.bin"
    }
}
