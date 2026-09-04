package jp.usuiensan.taxijokemeter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FareAdjustmentToggleTest {
    private val rates = FareAdjustmentRates(surchargePercent = 30, discountPercent = 20)

    @Test fun surchargeUsesMasterRateAndSecondPressClears() {
        assertEquals(FareAdjustmentSelection(FareAdjustmentType.SURCHARGE, 30), toggleFareAdjustment(FareAdjustmentSelection(), FareAdjustmentType.SURCHARGE, rates))
        assertEquals(FareAdjustmentSelection(), toggleFareAdjustment(FareAdjustmentSelection(FareAdjustmentType.SURCHARGE, 30), FareAdjustmentType.SURCHARGE, rates))
    }

    @Test fun oppositeAdjustmentReplacesCurrentSelection() {
        assertEquals(FareAdjustmentSelection(FareAdjustmentType.DISCOUNT, 20), toggleFareAdjustment(FareAdjustmentSelection(FareAdjustmentType.SURCHARGE, 30), FareAdjustmentType.DISCOUNT, rates))
    }

    @Test fun zeroRateStillProducesAnOnSelection() {
        val zero = FareAdjustmentRates(0, 0)
        assertEquals(FareAdjustmentSelection(FareAdjustmentType.SURCHARGE, 0), toggleFareAdjustment(FareAdjustmentSelection(), FareAdjustmentType.SURCHARGE, zero))
    }
}
