package jp.usuiensan.taxijokemeter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FareAdjustmentTest {
    @Test fun builtInPresetMatchesRequestedTenYenRule() {
        assertEquals(FareRule(1_000, 10, 1_000, 10, 10, 360), FarePresets.TEN_YEN_PER_KM.rule)
    }

    @Test fun appliesExclusiveFloorAdjustmentToMeteredFareOnly() {
        assertEquals(13L, adjustedMeteredFareYen(12, FareAdjustmentType.SURCHARGE, 10))
        assertEquals(10L, adjustedMeteredFareYen(12, FareAdjustmentType.DISCOUNT, 10))
        assertEquals(30L, adjustedMeteredFareYen(30, FareAdjustmentType.SURCHARGE, 0))
        assertEquals(70L, adjustedMeteredFareYen(70, FareAdjustmentType.DISCOUNT, 0))
        assertEquals(130L, adjustedMeteredFareYen(100, FareAdjustmentType.SURCHARGE, 30))
        assertEquals(70L, adjustedMeteredFareYen(100, FareAdjustmentType.DISCOUNT, 30))
    }

    @Test fun chargesAreExcludedFromAdjustment() {
        assertEquals(1_200L, totalFareYen(1_000, FareAdjustmentType.SURCHARGE, 10, 100))
    }
}
