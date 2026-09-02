package jp.usuiensan.taxijokemeter.domain.measurement

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TripMeasurementAccumulatorTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)

    @Test
    fun accumulatesDistanceForFastSamples() {
        val accumulator = TripMeasurementAccumulator(rule)

        val result = accumulator.add(sample(0, 0.0, 0.0, speedMps = 10.0), sample(10, 0.0, 0.001, speedMps = 10.0))

        assertEquals(111L, result.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun accumulatesLowSpeedTimeWithoutAddingItsDistance() {
        val accumulator = TripMeasurementAccumulator(rule)

        val result = accumulator.add(sample(0, 0.0, 0.0, speedMps = 1.0), sample(60, 0.0, 0.001, speedMps = 1.0))

        assertEquals(0L, result.chargeableDistanceMeters)
        assertEquals(60L, result.lowSpeedSeconds)
    }

    @Test
    fun excludesIntervalsWithInvalidSamples() {
        val accumulator = TripMeasurementAccumulator(rule)

        accumulator.add(sample(0, 0.0, 0.0), sample(10, 0.0, 0.001, quality = MeasurementQuality.UNAVAILABLE))
        val result = accumulator.add(sample(10, 0.0, 0.001, quality = MeasurementQuality.UNAVAILABLE), sample(20, 0.0, 0.002))

        assertEquals(0L, result.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun exposesSafetyLimitsForEachRoadMode() {
        assertEquals(60, RoadMode.GENERAL.safetyLimitKmh)
        assertEquals(80, RoadMode.HIGHWAY1.safetyLimitKmh)
        assertEquals(100, RoadMode.HIGHWAY2.safetyLimitKmh)
    }

    private fun sample(
        second: Long,
        latitude: Double = 35.0,
        longitude: Double = 135.0,
        speedMps: Double = 10.0,
        quality: MeasurementQuality = MeasurementQuality.VALID,
    ) = DrivingSample(
        timestamp = Instant.ofEpochSecond(second),
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5.0,
        speedMps = speedMps,
        bearingDegrees = null,
        roadMode = RoadMode.GENERAL,
        occupancyState = OccupancyState.OCCUPIED,
        measurementQuality = quality,
    )
}
