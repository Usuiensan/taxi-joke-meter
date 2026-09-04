package jp.usuiensan.taxijokemeter.domain.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEvaluationTest {
    @Test fun clean100KmIsA() {
        val result = SafetyEvaluator.evaluate(SafetyEvaluationInput(distanceKm = 100.0))
        assertEquals(100.0, result.score!!, 0.001)
        assertEquals(SafetyRank.A, result.rank)
    }

    @Test fun distanceFactorMakesLongerTripSlightlyLighter() {
        val fifty = SafetyEvaluator.evaluate(SafetyEvaluationInput(50.0, weakAccelCount = 2))
        val twoHundred = SafetyEvaluator.evaluate(SafetyEvaluationInput(200.0, weakAccelCount = 2))
        assertTrue(twoHundred.rawPenalty == fifty.rawPenalty)
        assertTrue(twoHundred.finalPenalty < fifty.finalPenalty)
    }

    @Test fun overspeedBandsAndHardOperationsUseSpecifiedPoints() {
        val result = SafetyEvaluator.evaluate(SafetyEvaluationInput(100.0, strongAccelCount = 1, strongBrakeCount = 1, overspeedSecondsByBand = mapOf(25 to 60.0, 30 to 60.0)))
        assertEquals(22.0, result.rawPenalty, 0.001)
        assertEquals(79.1, result.score!!, 0.001)
        assertEquals(SafetyRank.C, result.rank)
    }

    @Test fun belowFiveKmIsNotScored() {
        val result = SafetyEvaluator.evaluate(SafetyEvaluationInput(4.999, strongAccelCount = 10))
        assertFalse(result.isScored)
        assertEquals(null, result.score)
        assertEquals(SafetyRank.INSUFFICIENT_DATA, result.rank)
    }

    @Test fun eventNoiseShorterThanFiveSecondsIsIgnored() {
        assertEquals(0, SafetyEvaluator.countOverspeedEvents(listOf(2.0, 4.9)))
        assertEquals(2, SafetyEvaluator.countOverspeedEvents(listOf(5.0, 8.0)))
    }
}
