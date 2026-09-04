package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test

class FareCalculatorTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)
    private val explicitRule = FareRule(1_000, 10, 1_000, 10, 10, 360)

    @Test fun distanceBoundariesUseInitialDistanceThenCeilingIncrements() {
        mapOf(999L to 10L, 1_000L to 10L, 1_001L to 20L, 1_999L to 20L, 2_000L to 20L, 2_001L to 30L).forEach { (distance, expected) ->
            assertEquals(expected, FareCalculator.calculate(explicitRule, distance, 0))
        }
    }

    @Test fun lowSpeedTimeBoundariesShareTheInitialFareDistance() {
        mapOf(359L to 10L, 360L to 10L, 361L to 20L, 719L to 20L, 720L to 20L, 721L to 30L).forEach { (seconds, expected) ->
            assertEquals(expected, FareCalculator.calculate(explicitRule, 0, seconds))
        }
    }

    @Test fun breakdownExposesTheCurrentTimeDistanceMeaning() {
        val breakdown = FareCalculator.breakdown(explicitRule, chargeableDistanceMeters = 19_315, lowSpeedSeconds = 697)

        assertEquals(19_315L, breakdown.chargeableDistanceMeters)
        assertEquals(697L, breakdown.lowSpeedSeconds)
        assertEquals(1_936.111, breakdown.timeEquivalentDistanceMeters, 0.001)
        assertEquals(21_251.111, breakdown.effectiveDistanceMeters, 0.001)
        assertEquals(21L, breakdown.subsequentChargeCount)
        assertEquals(220L, breakdown.meteredFareYen)
    }

    @Test fun aboveLowSpeedLimitUsesDistanceOnly() = assertEquals(
        600,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 1_001, lowSpeedSeconds = 0),
    )

    @Test fun fullyStoppedLowSpeedSectionUsesTimeOnly() = assertEquals(
        500,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 0, lowSpeedSeconds = 75),
    )

    @Test fun lowSpeedTravelDoesNotDoubleCountItsActualDistance() = assertEquals(
        500,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 500, lowSpeedSeconds = 75),
    )

    @Test fun mixedSectionsAccumulateDistanceAndLowSpeedTime() = assertEquals(
        700,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 1_100, lowSpeedSeconds = 75),
    )

    @Test fun lowSpeedTimeAddsToChargeableDistanceWithoutLowSpeedActualDistance() = assertEquals(
        600,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 900, lowSpeedSeconds = 75),
    )

    @Test fun partialDistanceAndLowSpeedTimeDoNotShareActualLowSpeedDistance() = assertEquals(
        700,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 1_150, lowSpeedSeconds = 19),
    )

    @Test fun publishedSeventyFiveSecondBoundaryAddsOnce() = assertEquals(
        500,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 0, lowSpeedSeconds = 75),
    )

    @Test fun publishedOneHundredFiftySecondBoundaryAddsTwice() = assertEquals(
        500,
        FareCalculator.calculate(rule, chargeableDistanceMeters = 0, lowSpeedSeconds = 150),
    )
}
