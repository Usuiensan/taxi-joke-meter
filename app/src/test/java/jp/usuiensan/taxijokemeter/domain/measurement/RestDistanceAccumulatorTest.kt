package jp.usuiensan.taxijokemeter.domain.measurement

import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RestDistanceAccumulatorTest {
    @Test fun validRestSegmentAddsItsDistance() {
        val previous = sample(35.0, 139.0, MeasurementQuality.VALID)
        val current = sample(35.01, 139.0, MeasurementQuality.VALID)

        assertTrue(RestDistanceAccumulator.add(previous, current) >= 1_000)
    }

    @Test fun invalidRestSegmentAddsNothing() {
        assertEquals(0, RestDistanceAccumulator.add(sample(35.0, 139.0, MeasurementQuality.VALID), sample(35.01, 139.0, MeasurementQuality.STALE)))
    }

    private fun sample(latitude: Double, longitude: Double, quality: MeasurementQuality) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.EPOCH, latitude = latitude, longitude = longitude,
        accuracyMeters = 5.0, speedMps = 0.0, bearingDegrees = null, roadMode = RoadMode.GENERAL,
        occupancyState = OccupancyState.REST, measurementQuality = quality,
    )
}
