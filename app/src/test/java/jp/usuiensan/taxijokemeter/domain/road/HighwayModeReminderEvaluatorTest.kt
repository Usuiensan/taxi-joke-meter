package jp.usuiensan.taxijokemeter.domain.road

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighwayModeReminderEvaluatorTest {
    private val evaluator = HighwayModeReminderEvaluator()
    private val start = Instant.parse("2026-09-03T00:00:00Z")

    @Test fun generalModeNeverReminds() {
        assertFalse(evaluator.evaluate(RoadMode.GENERAL, samples(70.0), null, start.plusSeconds(60)))
    }

    @Test fun sustainedLowSpeedInHighwayModeReminds() {
        assertTrue(evaluator.evaluate(RoadMode.HIGHWAY1, samples(10.0), null, start.plusSeconds(60)))
    }

    @Test fun invalidSamplesDoNotTriggerReminder() {
        assertFalse(
            evaluator.evaluate(
                RoadMode.HIGHWAY1,
                listOf(
                    sample(0, 10.0, MeasurementQuality.UNAVAILABLE),
                    sample(60, 10.0, MeasurementQuality.UNAVAILABLE),
                ),
                null,
                start.plusSeconds(60),
            ),
        )
    }

    @Test fun invalidSampleDoesNotBridgeLowSpeedIntervals() {
        assertFalse(
            evaluator.evaluate(
                RoadMode.HIGHWAY1,
                listOf(
                    sample(0, 10.0),
                    sample(30, 10.0, MeasurementQuality.UNAVAILABLE),
                    sample(120, 10.0),
                ),
                null,
                start.plusSeconds(120),
            ),
        )
    }

    @Test fun customWindowAndAverageSpeedThresholdAreAppliedAtBoundary() {
        val configured = HighwayModeReminderEvaluator(
            reminderWindowSeconds = 120,
            averageSpeedThresholdKmh = 20.0,
        )
        assertFalse(
            configured.evaluate(
                RoadMode.HIGHWAY1,
                listOf(sample(0, 5.0), sample(119, 5.0)),
                null,
                start.plusSeconds(119),
            ),
        )
        assertTrue(
            configured.evaluate(
                RoadMode.HIGHWAY1,
                listOf(sample(0, 5.0), sample(120, 5.0)),
                null,
                start.plusSeconds(120),
            ),
        )
    }

    @Test fun reminderIsOneTimeWhenLastNotificationExists() {
        assertFalse(evaluator.evaluate(RoadMode.HIGHWAY1, samples(10.0), start, start.plusSeconds(60)))
    }

    @Test fun windowExcludesSamplesBeforeReminderWindow() {
        assertFalse(evaluator.evaluate(RoadMode.HIGHWAY1, samples(10.0), null, start.plusSeconds(120)))
    }

    @Test fun countsValidIntervalThatCrossesWindowStart() {
        val configured = HighwayModeReminderEvaluator(reminderWindowSeconds = 60)

        assertTrue(
            configured.evaluate(
                RoadMode.HIGHWAY1,
                listOf(sample(0, 10.0), sample(120, 10.0)),
                null,
                start.plusSeconds(120),
            ),
        )
    }

    @Test fun staleLastSampleIsNotExtrapolatedToNow() {
        assertFalse(evaluator.evaluate(RoadMode.HIGHWAY1, listOf(sample(0, 10.0)), null, start.plusSeconds(60)))
    }

    private fun samples(speedMps: Double) = listOf(
        sample(0, speedMps),
        sample(60, speedMps),
    )

    private fun sample(
        second: Long,
        speedMps: Double,
        quality: MeasurementQuality = MeasurementQuality.VALID,
    ) = DrivingSample(
        dutySessionId = "duty-1",
        timestamp = start.plusSeconds(second),
        latitude = 35.0,
        longitude = 135.0,
        accuracyMeters = 5.0,
        speedMps = speedMps,
        bearingDegrees = null,
        roadMode = RoadMode.HIGHWAY1,
        occupancyState = jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState.OCCUPIED,
        measurementQuality = quality,
    )
}
