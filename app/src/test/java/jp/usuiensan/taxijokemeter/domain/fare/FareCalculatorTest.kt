package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test

class FareCalculatorTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)

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
