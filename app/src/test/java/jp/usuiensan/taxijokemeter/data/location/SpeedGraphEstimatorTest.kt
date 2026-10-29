package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedGraphEstimatorTest {
    @Test fun smoothsGpsNoiseAndQuantizesWithHysteresis() {
        val estimator = SpeedGraphEstimator()
        val values = listOf(49.7, 50.2, 49.6, 50.5, 50.1, 49.8, 50.4).mapIndexed { index, kmh ->
            estimator.update(index * 500_000_000L, kmh / 3.6, null, false).graphSpeedMps!! * 3.6
        }
        assertEquals(listOf(50.0, 50.0, 50.0, 50.0, 50.0, 50.0, 50.0), values)
    }

    @Test fun integratesStableImuDuringShortGpsOutageAndStopsAfterSixtySeconds() {
        val estimator = SpeedGraphEstimator()
        estimator.update(0, 72.0 / 3.6, null, false)
        estimator.update(2_000_000_000L, null, -0.5, true)
        val afterTenSeconds = estimator.update(12_000_000_000L, null, -0.5, true)
        assertEquals(54.0 / 3.6, afterTenSeconds.speedMps!!, 0.05)
        assertEquals(71.0, afterTenSeconds.graphSpeedMps!! * 3.6, 1.0)
        assertNull(estimator.update(63_000_000_000L, null, 0.0, true).speedMps)
    }

    @Test fun recoversToGpsSpeedWithinAboutTwoSeconds() {
        val estimator = SpeedGraphEstimator()
        estimator.update(0, 72.0 / 3.6, null, false)
        estimator.update(2_000_000_000L, null, -0.5, true)
        val duringOutage = estimator.update(12_000_000_000L, null, -0.5, true)
        val firstGps = estimator.update(12_500_000_000L, 50.0 / 3.6, null, true)
        val recovered = estimator.update(14_500_000_000L, 50.0 / 3.6, null, true)

        assertEquals(54.0 / 3.6, duringOutage.speedMps!!, 0.05)
        assertEquals(50.0 / 3.6, recovered.speedMps!!, 0.2)
        assert(firstGps.speedMps!! > recovered.speedMps!!)
    }

    @Test fun unstableImuMakesOutageSpeedUnavailable() {
        val estimator = SpeedGraphEstimator()
        estimator.update(0, 72.0 / 3.6, null, false)
        val unavailable = estimator.update(2_000_000_000L, null, -0.5, false)

        assertNull(unavailable.speedMps)
        assertNull(unavailable.graphSpeedMps)
    }
}
