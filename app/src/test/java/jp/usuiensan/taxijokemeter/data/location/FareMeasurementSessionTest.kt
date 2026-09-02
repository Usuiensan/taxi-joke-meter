package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FareMeasurementSessionTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)

    @Test fun inactiveSessionDoesNotAccumulate() {
        val session = FareMeasurementSession(rule)

        assertEquals(null, session.add(sample(0.001, 1)))
    }

    @Test fun firstSampleAfterBeginIsAnchorAndOnlyFollowingSegmentAccumulates() {
        val session = FareMeasurementSession(rule)
        session.begin()

        session.add(sample(0.001, 1))
        session.add(sample(0.002, 2))

        assertEquals(91L, session.end()!!.chargeableDistanceMeters)
        assertEquals(null, session.add(sample(0.001, 3)))
    }

    @Test fun beginResetsAnchorFromPreviousMeasurement() {
        val session = FareMeasurementSession(rule)
        session.begin()
        session.add(sample(0.001, 1))
        session.end()

        session.begin()
        session.add(sample(0.003, 3))

        assertEquals(null, session.end())
    }

    @Test fun invalidSampleDoesNotBecomeFareAnchor() {
        val session = FareMeasurementSession(rule)
        session.begin()

        session.add(sample(0.001, 1, MeasurementQuality.STALE))
        assertEquals(null, session.add(sample(0.002, 2)))

        assertEquals(null, session.end())
    }

    @Test fun everyNonValidQualityDisconnectsThePreviousAnchor() {
        MeasurementQuality.entries.filter { it != MeasurementQuality.VALID }.forEach { quality ->
            val session = FareMeasurementSession(rule)
            session.begin()
            session.add(sample(0.001, 1))
            session.add(sample(0.0015, 2, quality))
            session.add(sample(0.002, 3))
            session.add(sample(0.003, 4))

            assertEquals(91L, session.end()!!.chargeableDistanceMeters)
        }
    }

    private fun sample(longitude: Double, second: Long, quality: MeasurementQuality = MeasurementQuality.VALID) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.ofEpochSecond(second), latitude = 35.0,
        longitude = longitude, accuracyMeters = 5.0, speedMps = 10.0, bearingDegrees = null,
        roadMode = RoadMode.GENERAL, occupancyState = OccupancyState.EMPTY,
        measurementQuality = quality, elapsedRealtimeNanos = second * 1_000_000_000L,
        speedSource = SpeedSource.REPORTED,
    )
}
