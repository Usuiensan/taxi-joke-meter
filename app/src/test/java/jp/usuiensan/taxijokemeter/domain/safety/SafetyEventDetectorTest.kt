package jp.usuiensan.taxijokemeter.domain.safety

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEventDetectorTest {
    @Test fun hardAccelerationStartsOnceAndCompletesAfterSettling() {
        val detector = SafetyEventDetector()
        assertTrue(detector.consume(sample(0, 0.0)).isEmpty())
        val started = detector.consume(sample(3, 8.0)).filterIsInstance<SafetyEventSignal.Started>()
        assertEquals(1, started.size)
        assertEquals(8.0 / 3.0, started.single().accelerationMps2!!, 0.001)
        assertTrue(detector.consume(sample(4, 8.0)).isEmpty())
        assertTrue(detector.consume(sample(6, 8.0)).filterIsInstance<SafetyEventSignal.Completed>().isEmpty())
        assertEquals(1, detector.consume(sample(9, 8.0)).filterIsInstance<SafetyEventSignal.Completed>().size)
    }

    @Test fun repeatedHardDecelerationSamplesProduceOneEvent() {
        val detector = SafetyEventDetector()
        detector.consume(sample(0, 20.0))
        val started = detector.consume(sample(3, 10.0)).filterIsInstance<SafetyEventSignal.Started>()
        assertEquals(1, started.size)
        assertEquals(20.0, started.single().previousSpeedMps!!, 0.001)
        assertEquals(10.0, started.single().currentSpeedMps!!, 0.001)
        assertEquals(-3.0, started.single().threshold!!, 0.001)
        assertTrue(!started.single().detectionId.isNullOrEmpty())
        assertTrue(detector.consume(sample(4, 9.0)).none { it is SafetyEventSignal.Started })
        assertTrue(detector.consume(sample(5, 8.0)).none { it is SafetyEventSignal.Started })
    }

    @Test fun overspeedNeedsFiveSecondsAndCompletesAfterTwoSecondsBelowRelease() {
        val detector = SafetyEventDetector()
        (0L..4L).forEach { second -> assertTrue(detector.consume(sample(second, 66.0 / 3.6)).isEmpty()) }
        val started = detector.consume(sample(5, 66.0 / 3.6)).filterIsInstance<SafetyEventSignal.Started>()
        assertEquals(1, started.size)
        assertEquals(66, started.single().speedKmh)
        assertTrue(detector.consume(sample(6, 61.0 / 3.6)).isEmpty())
        assertEquals(1, detector.consume(sample(8, 61.0 / 3.6)).filterIsInstance<SafetyEventSignal.Completed>().size)
    }

    @Test fun invalidGapDoesNotConnectAcceleration() {
        val detector = SafetyEventDetector()
        detector.consume(sample(0, 40.0 / 3.6))
        detector.consume(sample(6, 0.0, quality = MeasurementQuality.UNAVAILABLE, speedSource = SpeedSource.UNAVAILABLE))
        assertTrue(detector.consume(sample(7, 0.0)).isEmpty())
    }

    @Test fun roadModeChangeUsesNewLimitWithoutStartingAnotherEvent() {
        val detector = SafetyEventDetector()
        (0L..5L).forEach { second -> detector.consume(sample(second, 70.0 / 3.6)) }
        val signals = detector.consume(sample(6, 70.0 / 3.6, roadMode = RoadMode.HIGHWAY2))
        assertTrue(signals.none { it is SafetyEventSignal.Started })
    }

    @Test fun stableImuAccelerationProducesImuSourcedEventWithoutGpsSpeedDifference() {
        val detector = SafetyEventDetector()
        detector.consume(sample(0, 0.0))
        assertEquals(1, detector.consume(sample(1, 0.0), imuAccelerationMps2 = 3.0, imuStable = true).filterIsInstance<SafetyEventSignal.Started>().size)
        detector.consume(sample(2, 0.0), imuAccelerationMps2 = 0.0, imuStable = true)
        detector.consume(sample(3, 0.0), imuAccelerationMps2 = 0.0, imuStable = true)
        val completed = detector.consume(sample(4, 0.0), imuAccelerationMps2 = 0.0, imuStable = true).filterIsInstance<SafetyEventSignal.Completed>()
        assertEquals(SafetyEventSource.IMU, completed.single().event.source)
    }

    private fun sample(
        second: Long,
        speedMps: Double,
        quality: MeasurementQuality = MeasurementQuality.VALID,
        speedSource: SpeedSource = SpeedSource.REPORTED,
        roadMode: RoadMode = RoadMode.GENERAL,
    ) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.EPOCH.plusSeconds(second), latitude = 35.0, longitude = 139.0,
        accuracyMeters = 5.0, speedMps = speedMps, bearingDegrees = null, roadMode = roadMode,
        occupancyState = OccupancyState.OCCUPIED, measurementQuality = quality,
        elapsedRealtimeNanos = second * 1_000_000_000L, speedSource = speedSource,
    )
}
