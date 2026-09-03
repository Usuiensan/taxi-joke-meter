package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeProgressMapperTest {
    private val rule = FareRule(1_000, 500, 200, 100, 10, 60)

    @Test
    fun initialDistanceUsesTheInitialFareWindow() {
        assertEquals(0.5f, ChargeProgressMapper.fraction(rule, 500, 0), 0.0001f)
    }

    @Test
    fun exactFareBoundaryStartsTheNextWindow() {
        assertEquals(0f, ChargeProgressMapper.fraction(rule, 1_200, 0), 0.0001f)
    }

    @Test
    fun subsequentWindowUsesThePositionAfterTheLastFareIncrease() {
        assertEquals(0.5f, ChargeProgressMapper.fraction(rule, 1_300, 0), 0.0001f)
    }
}
