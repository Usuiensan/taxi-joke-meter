package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterControllerPaymentTest {
    @Test
    fun emptyPaymentDoesNotRunFareEndOrDropoff() {
        assertFalse(shouldProcessPayment(MeterState.EMPTY))
    }

    @Test
    fun occupiedPaymentRunsFareEndAndDropoff() {
        assertTrue(shouldProcessPayment(MeterState.OCCUPIED))
    }
}
