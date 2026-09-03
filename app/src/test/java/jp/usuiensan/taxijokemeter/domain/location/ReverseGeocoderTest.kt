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
    fun mapsAddressFieldsAndLeavesFeatureNameUnsetWithoutSafeEvidence() {
        val result = mapReverseGeocode(
            ReverseGeocodeAddress(
                canonicalAddress = "東京都豊島区西池袋1丁目1番1号",
                reportAddress = "豊島区西池袋1丁目1番1号",
                featureName = "池袋駅",
                featureDetail = "西口",
            ),
        )

        assertEquals("東京都豊島区西池袋1丁目1番1号", result?.canonicalAddress)
        assertEquals("豊島区西池袋1丁目1番1号", result?.reportAddress)
        assertNull(result?.landmarkName)
        assertNull(result?.landmarkDetail)
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
}
