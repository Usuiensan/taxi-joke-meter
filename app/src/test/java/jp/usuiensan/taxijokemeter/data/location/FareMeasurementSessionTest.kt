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

        assertEquals(null, session.add(sample(0.0, 0), sample(0.001, 1)))
    }

    @Test fun beginStartsAtZeroAndEndReturnsTripResult() {
        val session = FareMeasurementSession(rule)
        session.begin()

        session.add(sample(0.0, 0), sample(0.001, 1))

        assertEquals(91L, session.end()!!.chargeableDistanceMeters)
        assertEquals(null, session.add(sample(0.0, 2), sample(0.001, 3)))
    }

    private fun sample(longitude: Double, second: Long) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.ofEpochSecond(second), latitude = 35.0,
        longitude = longitude, accuracyMeters = 5.0, speedMps = 10.0, bearingDegrees = null,
        roadMode = RoadMode.GENERAL, occupancyState = OccupancyState.EMPTY,
        measurementQuality = MeasurementQuality.VALID, elapsedRealtimeNanos = second * 1_000_000_000L,
        speedSource = SpeedSource.REPORTED,
    )
}
