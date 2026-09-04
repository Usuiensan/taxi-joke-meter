package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.measurement.FareMeasurementMode
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FareMeasurementSessionTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)

    @Test fun beginningTwiceDoesNotResetAnActiveFareMeasurement() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.add(sample(0.001, 1, occupancyState = OccupancyState.OCCUPIED))
        session.add(sample(0.002, 2, occupancyState = OccupancyState.OCCUPIED))
        val first = session.currentResult()

        session.begin(OccupancyState.OCCUPIED)

        assertEquals(first, session.currentResult())
    }

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

    @Test fun paymentLowSpeedAddsDistanceWithoutLowSpeedTime() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.add(sample(0.001, 1, speedMps = 1.0, occupancyState = OccupancyState.OCCUPIED))
        session.setOccupancyState(OccupancyState.EMPTY)

        session.add(sample(0.002, 2, speedMps = 1.0, occupancyState = OccupancyState.EMPTY))
        val result = session.add(sample(0.003, 3, speedMps = 1.0, occupancyState = OccupancyState.EMPTY))

        assertEquals(91L, result!!.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test fun changingOccupancyCutsTheSegmentAnchor() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.add(sample(0.001, 1, occupancyState = OccupancyState.OCCUPIED))
        session.setOccupancyState(OccupancyState.EMPTY)

        assertEquals(null, session.add(sample(0.002, 2, occupancyState = OccupancyState.EMPTY)))
    }

    @Test fun resumingOccupancyCutsThePaymentAnchor() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.setOccupancyState(OccupancyState.EMPTY)
        session.add(sample(0.001, 1, occupancyState = OccupancyState.EMPTY))
        session.setOccupancyState(OccupancyState.OCCUPIED)

        assertEquals(null, session.add(sample(0.002, 2, occupancyState = OccupancyState.OCCUPIED)))
    }

    @Test fun invalidSampleDoesNotBecomeFareAnchor() {
        val session = FareMeasurementSession(rule)
        session.begin()

        session.add(sample(0.001, 1, MeasurementQuality.STALE))
        assertEquals(null, session.add(sample(0.002, 2)))

        assertEquals(null, session.end())
    }

    @Test fun shortInvalidGapCanRecoverDistanceWithoutLowSpeedTime() {
        MeasurementQuality.entries.filter { it != MeasurementQuality.VALID }.forEach { quality ->
            val session = FareMeasurementSession(rule)
            session.begin()
            session.add(sample(0.001, 1))
            session.add(sample(0.0015, 2, quality))
            session.add(sample(0.002, 3))
            session.add(sample(0.003, 4))

            assertEquals(182L, session.end()!!.chargeableDistanceMeters)
        }
    }

    @Test fun poorAccuracyDoesNotAdvanceLowSpeedTime() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.add(sample(0.001, 1, occupancyState = OccupancyState.OCCUPIED))

        val result = session.add(sample(0.002, 6, MeasurementQuality.POOR_ACCURACY, speedMps = null, occupancyState = OccupancyState.OCCUPIED))

        assertEquals(null, result)
    }

    @Test fun returningToValidAfterPoorAccuracyDoesNotBridgeDistance() {
        val session = FareMeasurementSession(rule)
        session.begin(OccupancyState.OCCUPIED)
        session.add(sample(0.001, 1, occupancyState = OccupancyState.OCCUPIED))
        session.add(sample(0.002, 6, MeasurementQuality.POOR_ACCURACY, speedMps = null, occupancyState = OccupancyState.OCCUPIED))

        val result = session.add(sample(0.003, 7, occupancyState = OccupancyState.OCCUPIED))

        assertEquals(182L, result!!.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    private fun sample(
        longitude: Double,
        second: Long,
        quality: MeasurementQuality = MeasurementQuality.VALID,
        speedMps: Double? = 10.0,
        occupancyState: OccupancyState = OccupancyState.EMPTY,
    ) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.ofEpochSecond(second), latitude = 35.0,
        longitude = longitude, accuracyMeters = 5.0, speedMps = speedMps, bearingDegrees = null,
        roadMode = RoadMode.GENERAL, occupancyState = occupancyState,
        measurementQuality = quality, elapsedRealtimeNanos = second * 1_000_000_000L,
        speedSource = SpeedSource.REPORTED,
    )
}
