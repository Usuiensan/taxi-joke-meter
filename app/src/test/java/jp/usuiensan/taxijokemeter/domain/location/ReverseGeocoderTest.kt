package jp.usuiensan.taxijokemeter.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import jp.usuiensan.taxijokemeter.data.meter.launchReverseGeocode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReverseGeocoderTest {
    @Test
    fun mapsJapaneseRoadAddressAndFacility() {
        val result = mapReverseGeocode(
            ReverseGeocodeAddress(
                canonicalAddress = "東京都豊島区西池袋1丁目1番1号",
                reportAddress = "豊島区西池袋1丁目1番1号",
                featureName = "池袋駅",
                featureDetail = "西口",
                roadName = "西池袋通り",
                areaText = "豊島区西池袋1丁目",
            ),
        )

        assertEquals("東京都豊島区西池袋1丁目1番1号", result?.canonicalAddress)
        assertEquals("豊島区西池袋1丁目1番1号", result?.reportAddress)
        assertEquals("池袋駅", result?.landmarkName)
        assertNull(result?.landmarkDetail)
        assertEquals("西池袋通り", result?.roadName)
        assertEquals("豊島区西池袋1丁目", result?.areaText)
        assertEquals("池袋駅", result?.facilityName)
    }

    @Test
    fun japaneseAddressUsesJapaneseComponentsWithoutDuplicationOrEnglishFallback() {
        assertEquals("千代田区千代田1丁目1番", japaneseAddress("千代田区", "千代田1丁目", "1番", "Tokyo Chiyoda"))
        assertEquals("平塚市土屋1038", japaneseAddress("平塚市", "土屋", "1038", null))
        assertEquals("千代田区千代田1丁目1番", japaneseAddress("千代田区", "6", null, "〒100-0001 日本、東京都千代田区千代田1丁目1番"))
        assertNull(japaneseAddress("Chiyoda City", "1-Chome", "", "Tokyo"))
    }

    @Test
    fun englishRoadIsDroppedButJapaneseAddressRemains() {
        val result = mapReverseGeocode(
            ReverseGeocodeAddress("英語住所", "秦野市南矢名1丁目12", "1-Chome", null, roadName = "Street XYZ", areaText = "秦野市南矢名1丁目12"),
        )
        assertNull(result?.roadName)
        assertEquals("秦野市南矢名1丁目12", result?.areaText)
    }

    @Test
    fun featureNameIsUsedAsRoadOnlyWhenItClearlyNamesAJapaneseRoad() {
        assertEquals("国道1号", japaneseRoadName(null, "国道1号"))
        assertEquals("内堀通り", japaneseRoadName("内堀通り", "皇居"))
        assertNull(japaneseRoadName(null, "千代田区"))
        assertNull(japaneseRoadName("正道", null))
        assertEquals("城陽市寺田正道", japaneseAddress("城陽市", "寺田", null, "京都府城陽市寺田正道", "正道"))
    }

    @Test
    fun geocoderAddressLineRemainsCompleteAtEveryAccuracy() {
        val address = japaneseAddress(
            locality = "城陽市",
            subLocality = null,
            subThoroughfare = null,
            addressLine = "日本、〒610-0121 京都府城陽市寺田正道５４−１０",
            thoroughfare = "正道",
        )
        val result = mapReverseGeocode(
            ReverseGeocodeAddress(
                canonicalAddress = address,
                reportAddress = address,
                featureName = "５４−１０",
                featureDetail = null,
                municipality = "城陽市",
                aza = "正道",
            ),
        )!!

        assertEquals("城陽市寺田正道54-10", result.canonicalAddress)
        assertEquals("城陽市寺田正道54-10", reportAddressForAccuracy(result, 30.0))
        assertEquals("城陽市", reportAddressForAccuracy(result, 60.0))
        assertEquals("城陽市", reportAddressForAccuracy(result, 150.0))
        assertNull(japaneseRoadName("正道", null))
    }

    @Test
    fun emulatorChiyodaAddressKeepsTownNameFromAddressLine() {
        val address = japaneseAddress(
            locality = "千代田区",
            subLocality = null,
            subThoroughfare = "１",
            addressLine = "日本、〒100-0001 東京都千代田区千代田１",
            thoroughfare = null,
        )

        assertEquals("千代田区千代田1", address)
    }

    @Test
    fun reverseGeocodeWorkDoesNotBlockTheSerializedOperationQueue() = runBlocking<Unit> {
        val operationDispatcher = newSingleThreadContext("operation")
        val reverseDispatcher = newSingleThreadContext("reverse-geocode")
        val release = CountDownLatch(1)
        try {
            val operationScope = CoroutineScope(operationDispatcher)
            val started = CompletableDeferred<Unit>()
            val reverse = operationScope.launchReverseGeocode(reverseDispatcher) {
                started.complete(Unit)
                release.await(2, TimeUnit.SECONDS)
            }
            started.await()

            val saved = CompletableDeferred<String>()
            operationScope.launch { saved.complete("saved") }
            assertEquals("saved", withTimeout(500) { saved.await() })
            release.countDown()
            reverse.join()
        } finally {
            release.countDown()
            operationDispatcher.close()
            reverseDispatcher.close()
        }
    }

    @Test
    fun geocoderFailureProducesNoAddressUpdate() {
        assertNull(mapReverseGeocode(null))
    }

    @Test
    fun reportAddressBecomesCoarserAsAccuracyWorsens() {
        val result = ReverseGeocodeResult(
            canonicalAddress = "東京都平塚市土屋1038",
            reportAddress = "平塚市土屋1丁目1038",
            landmarkName = "土屋施設",
            landmarkDetail = null,
            roadName = "土屋通り",
            areaText = "平塚市土屋1丁目",
            municipality = "平塚市",
            town = "土屋",
            chome = "1丁目",
            block = "10番",
            houseNumber = "38号",
        )

        assertEquals("平塚市土屋1丁目1038", reportAddressForAccuracy(result, 15.0))
        assertEquals("平塚市土屋1丁目", reportAddressForAccuracy(result, 60.0))
        assertEquals("平塚市", reportAddressForAccuracy(result, 180.0))
        assertEquals("平塚市", reportAddressForAccuracy(result, 500.0))
        assertEquals("東京都平塚市土屋1038", result.canonicalAddress)
    }

    @Test
    fun romanizedLabelIsNotUsedForLiveAddress() {
        val result = ReverseGeocodeResult("正道第4公園, Uji-shi, KY, Japan", "Uji-shi, KY, Japan", "正道第4公園", null, null, "Uji-shi", "Uji-shi")
        assertEquals(null, reportAddressForAccuracy(result, 150.0))
    }

    @Test
    fun addressDisplayRemovesOnlyAnIndependentTrailingFacilityName() {
        assertEquals("千代田区千代田1", addressDisplay("千代田区千代田1 桜田門駅", "桜田門駅"))
        assertEquals("千代田区千代田1", addressDisplay("千代田区千代田1　桜田門駅", "桜田門駅"))
        assertEquals("千代田区千代田1", addressDisplay("千代田区千代田1", "桜田門駅"))
        assertEquals("桜田門駅前", addressDisplay("桜田門駅前", "桜田門駅"))
        assertNull(addressDisplay("桜田門駅", "桜田門駅"))
    }
}
