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
        assertEquals(111L, result.actualDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun accumulatesLowSpeedTimeWithoutAddingItsDistance() {
        val accumulator = TripMeasurementAccumulator(rule)

        val result = accumulator.add(sample(0, 0.0, 0.0, speedMps = 1.0), sample(60, 0.0, 0.001, speedMps = 1.0))

        assertEquals(0L, result.chargeableDistanceMeters)
        assertEquals(0L, result.actualDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun treatsExactlyTenKmhAsLowSpeed() {
        val result = TripMeasurementAccumulator(rule).add(
            sample(0, speedMps = 10.0 / 3.6),
            sample(1, longitude = 135.0001, speedMps = 10.0 / 3.6),
        )

        assertEquals(1L, result.lowSpeedSeconds)
        assertEquals(0L, result.chargeableDistanceMeters)
    }

    @Test
    fun classifiesNinePointNineTenAndTenPointOneKmhAtTheBoundary() {
        mapOf(9.9 to FareMeasurementMode.TIME, 10.0 to FareMeasurementMode.TIME, 10.1 to FareMeasurementMode.DISTANCE).forEach { (kmh, expectedMode) ->
            val result = TripMeasurementAccumulator(rule).add(
                sample(0, speedMps = kmh / 3.6),
                sample(1, longitude = 135.00001, speedMps = kmh / 3.6),
            )

            assertEquals(expectedMode, result.mode)
            assertEquals(if (expectedMode == FareMeasurementMode.TIME) 1L else 0L, result.lowSpeedSeconds)
        }
    }

    @Test
    fun lowSpeedDistanceIsNotChargedButTimeResumesAfterCrossingTheBoundary() {
        val accumulator = TripMeasurementAccumulator(rule)
        val low = sample(0, speedMps = 9.9 / 3.6)
        val fast = sample(1, longitude = 135.00001, speedMps = 10.1 / 3.6)
        val lowAgain = sample(2, longitude = 135.00002, speedMps = 9.9 / 3.6)

        accumulator.add(low, fast)
        val result = accumulator.add(fast, lowAgain)

        assertEquals(1L, result.lowSpeedSeconds)
        assertEquals(1L, result.chargeableDistanceMeters)
        assertEquals(2L, result.actualDistanceMeters)
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
    fun validSamplesAfterShortGapRecoverDistanceWithoutInventingLowSpeedTime() {
        val result = TripMeasurementAccumulator(rule).add(
            sample(0, longitude = 135.0, speedMps = 12.0),
            sample(5, longitude = 135.0005, speedMps = 12.0),
        )

        assertEquals(46L, result.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun validSamplesAfterLongGapDoNotRecoverDistance() {
        val result = TripMeasurementAccumulator(rule).add(
            sample(0, longitude = 135.0, speedMps = 12.0),
            sample(30, longitude = 135.0005, speedMps = 12.0),
        )

        assertEquals(0L, result.chargeableDistanceMeters)
        assertEquals(0L, result.actualDistanceMeters)
    }

    @Test
    fun preservesSubSecondLowSpeedIntervalsUntilFinalConversion() {
        val accumulator = TripMeasurementAccumulator(rule)

        var result = MeasurementAccumulation(0, 0)
        repeat(4) { index ->
            result = accumulator.add(sampleAtNanos(index * 500_000_000L, speedMps = 1.0), sampleAtNanos((index + 1) * 500_000_000L, speedMps = 1.0))
        }

        assertEquals(2L, result.lowSpeedSeconds)
    }

    @Test
    fun prefersMonotonicTimeWhenWallClockMovesBackwards() {
        val accumulator = TripMeasurementAccumulator(rule)

        val result = accumulator.add(
            sample(100, longitude = 135.0, speedMps = 1.0, elapsedRealtimeNanos = 1_000_000_000L),
            sample(90, longitude = 135.001, speedMps = 1.0, elapsedRealtimeNanos = 2_000_000_000L),
        )

        assertEquals(1L, result.lowSpeedSeconds)
    }

    @Test
    fun reportedZeroSpeedIsStoppedButMissingSpeedIsNotStopped() {
        val stopped = TripMeasurementAccumulator(rule).add(
            sample(0, speedMps = 0.0, elapsedRealtimeNanos = 0L),
            sample(1, longitude = 135.001, speedMps = 0.0, elapsedRealtimeNanos = 1_000_000_000L),
        )
        val unknown = TripMeasurementAccumulator(rule).add(
            sample(0, speedMps = null, elapsedRealtimeNanos = 0L),
            sample(1, longitude = 135.001, speedMps = null, elapsedRealtimeNanos = 1_000_000_000L),
        )

        assertEquals(1L, stopped.lowSpeedSeconds)
        assertEquals(0L, unknown.lowSpeedSeconds)
        assertEquals(91L, unknown.chargeableDistanceMeters)
    }

    @Test
    fun derivesAverageSpeedForMissingReportedSpeed() {
        val result = TripMeasurementAccumulator(rule).add(
            sample(0, longitude = 135.0, speedMps = null, elapsedRealtimeNanos = 0L),
            sample(1, longitude = 135.0001, speedMps = null, elapsedRealtimeNanos = 1_000_000_000L),
        )

        assertEquals(9L, result.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun rejectsAOnePointGpsJumpWhenReportedSpeedCannotExplainIt() {
        val accumulator = TripMeasurementAccumulator(rule)
        val first = sample(0, latitude = 35.0, longitude = 135.0, speedMps = 20.0 / 3.6)
        val second = sample(1, latitude = 35.0, longitude = 135.00005, speedMps = 20.0 / 3.6)
        val outlier = sample(2, latitude = 35.02, longitude = 135.02, speedMps = 20.0 / 3.6)
        val last = sample(3, latitude = 35.0, longitude = 135.0001, speedMps = 20.0 / 3.6)

        accumulator.add(first, second)
        accumulator.add(second, outlier)
        val result = accumulator.add(outlier, last)

        assertEquals(9L, result.actualDistanceMeters)
    }

    @Test
    fun excludesMissingSpeedWhenAverageCannotBeDerived() {
        val result = TripMeasurementAccumulator(rule).add(
            sample(0, longitude = 135.0, speedMps = null),
            sample(1, longitude = 135.0001, speedMps = null),
        )

        assertEquals(0L, result.chargeableDistanceMeters)
        assertEquals(0L, result.lowSpeedSeconds)
    }

    @Test
    fun onlyValidQualityIsChargeable() {
        assertEquals(setOf(MeasurementQuality.VALID, MeasurementQuality.POOR_ACCURACY, MeasurementQuality.STALE, MeasurementQuality.UNAVAILABLE), MeasurementQuality.entries.toSet())
    }

    @Test
    fun poorAccuracyWithUnknownSpeedIsNotFareOrTimeFallback() {
        val sample = sample(0, speedMps = null, quality = MeasurementQuality.POOR_ACCURACY)

        assertEquals(true, sample.isFareContinuable)
        assertEquals(true, sample.isTimeFallback)
    }

    @Test
    fun exposesTwoSafetyLimitsIncludingLegacyHighway() {
        assertEquals(60, RoadMode.GENERAL.safetyLimitKmh)
        assertEquals(90, RoadMode.HIGHWAY1.safetyLimitKmh)
        assertEquals(90, RoadMode.HIGHWAY2.safetyLimitKmh)
    }

    private fun sample(
        second: Long,
        latitude: Double = 35.0,
        longitude: Double = 135.0,
        speedMps: Double? = 10.0,
        quality: MeasurementQuality = MeasurementQuality.VALID,
        elapsedRealtimeNanos: Long? = null,
    ) = DrivingSample(
            dutySessionId = "duty-1",
            timestamp = Instant.ofEpochSecond(second),
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            latitude = latitude,
        longitude = longitude,
            accuracyMeters = 5.0,
            speedMps = speedMps,
            speedSource = if (speedMps == null) SpeedSource.UNAVAILABLE else SpeedSource.REPORTED,
        bearingDegrees = null,
        roadMode = RoadMode.GENERAL,
        occupancyState = OccupancyState.OCCUPIED,
        measurementQuality = quality,
    )

    private fun sampleAtNanos(nanos: Long, speedMps: Double = 10.0) = sample(0, speedMps = speedMps).copy(timestamp = Instant.EPOCH.plusNanos(nanos), elapsedRealtimeNanos = nanos)
}
