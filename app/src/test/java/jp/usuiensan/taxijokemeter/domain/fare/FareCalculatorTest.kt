package jp.usuiensan.taxijokemeter.domain.fare

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test

class FareCalculatorTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 75)

    @Test fun withinInitialDistance() = assertEquals(500, FareCalculator.calculate(rule, 999, 0))
    @Test fun firstDistanceIncrementBoundary() = assertEquals(600, FareCalculator.calculate(rule, 1_001, 0))
    @Test fun lowSpeedTimeUsesPublishedIncrementSeconds() = assertEquals(600, FareCalculator.calculate(rule, 1_000, 75))
    @Test fun lowSpeedTimeAndDistanceUseTheLargerIncrementCount() = assertEquals(700, FareCalculator.calculate(rule, 1_000, 150))
}
