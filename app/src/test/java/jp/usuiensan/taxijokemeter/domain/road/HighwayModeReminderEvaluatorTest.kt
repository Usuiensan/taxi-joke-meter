package jp.usuiensan.taxijokemeter.domain.road

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
                    HighwayModeSample(start, 10.0, isValid = false),
                    HighwayModeSample(start.plusSeconds(60), 10.0, isValid = false),
                ),
                null,
                start.plusSeconds(60),
            ),
        )
    }

    @Test fun reminderIsOneTimeWhenLastNotificationExists() {
        assertFalse(evaluator.evaluate(RoadMode.HIGHWAY1, samples(10.0), start, start.plusSeconds(60)))
    }

    private fun samples(speedMps: Double) = listOf(
        HighwayModeSample(start, speedMps),
        HighwayModeSample(start.plusSeconds(60), speedMps),
    )
}
