package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FareRuleStoreTest {
    @Test
    fun savedRuleIsLoadedAfterStoreIsRecreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fare_rule_test"
        context.deleteSharedPreferences(name)
        val rule = FareRule(1_200, 700, 250, 120, 12, 90)

        FareRuleStore(context, name).save(rule)

        assertEquals(rule, FareRuleStore(context, name).load())
        context.deleteSharedPreferences(name)
    }
}
