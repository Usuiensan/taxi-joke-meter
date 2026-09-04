package jp.usuiensan.taxijokemeter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FareAdjustmentTest {
    @Test fun builtInPresetMatchesRequestedTenYenRule() {
        assertEquals(FareRule(1_000, 10, 200, 2, 10, 75), FarePresets.TEN_YEN_PER_KM.rule)
    }

    @Test fun builtInPresetsContainAllTenPriceMasterValues() {
        assertEquals(
            listOf(
                FarePreset("10_yen_per_km", "10円/km", FareRule(1_000, 10, 200, 2, 10, 75), 200, 200, 20, 10),
                FarePreset("taxi_urban", "タクシー都市部", FareRule(1_000, 500, 232, 100, 10, 85), 500, 500, 20, 10),
                FarePreset("taxi_suburban", "タクシー郊外部", FareRule(1_000, 500, 211, 100, 10, 80), 500, 500, 20, 10),
                FarePreset("taxi_rural", "タクシー地方部", FareRule(900, 470, 271, 100, 10, 100), 300, 500, 20, 10),
            ),
            FarePresets.BUILT_IN,
        )
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
