package jp.usuiensan.taxijokemeter.data.location

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FareRuleIntentTest {
    @Test fun fareRuleIntentContainsAllSixValues() {
        val rule = FareRule(1_234, 777, 345, 88, 10, 42)
        val intent = Intent().putFareRuleExtras(rule)

        assertEquals(1_234L, intent.getLongExtra(LocationMeasurementService.EXTRA_INITIAL_DISTANCE_METERS, -1))
        assertEquals(777L, intent.getLongExtra(LocationMeasurementService.EXTRA_INITIAL_FARE_YEN, -1))
        assertEquals(345L, intent.getLongExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_METERS, -1))
        assertEquals(88L, intent.getLongExtra(LocationMeasurementService.EXTRA_DISTANCE_INCREMENT_FARE_YEN, -1))
        assertEquals(10, intent.getIntExtra(LocationMeasurementService.EXTRA_LOW_SPEED_LIMIT_KMH, -1))
        assertEquals(42, intent.getIntExtra(LocationMeasurementService.EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS, -1))
    }
}
