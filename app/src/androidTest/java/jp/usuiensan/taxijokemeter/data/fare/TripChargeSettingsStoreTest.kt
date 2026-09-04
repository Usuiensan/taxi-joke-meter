package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.ui.shell.saveFareSettings
import jp.usuiensan.taxijokemeter.ui.shell.saveFarePreset
import jp.usuiensan.taxijokemeter.domain.model.FarePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TripChargeSettingsStoreTest {
    @Test
    fun newStoreUsesRuralFarePresetByDefault() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base, "default-fare-${UUID.randomUUID()}")

        assertEquals(FarePresets.TAXI_RURAL.rule, FareRuleStore(context).load())

        context.clear()
    }

    @Test
    fun savedSettingsAreLoadedAfterStoreIsRecreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "trip_charge_settings_test"
        context.deleteSharedPreferences(name)
        val settings = TripChargeSettings(pickupFeeYen = 500, reservationFeeYen = 300, surchargePercent = 30, discountPercent = 20)

        TripChargeSettingsStore(context, name).save(settings)

        assertEquals(settings, TripChargeSettingsStore(context, name).load())
        context.deleteSharedPreferences(name)
    }

    @Test
    fun selectingPresetPersistsAllTenPriceMasterValues() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base, "preset-${UUID.randomUUID()}")

        saveFarePreset(context, FarePresets.TAXI_RURAL)

        assertEquals(FarePresets.TAXI_RURAL.rule, FareRuleStore(context).load())
        assertEquals(
            TripChargeSettings(300, 500, 20, 10),
            TripChargeSettingsStore(context).load(),
        )
        context.clear()
    }

    @Test
    fun invalidChargeDoesNotPartiallySaveFareRule() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base, "fare-save-${UUID.randomUUID()}")
        val originalRule = FareRule(1_000, 500, 200, 100, 10, 75)
        val originalCharges = TripChargeSettings(500, 300)
        FareRuleStore(context).save(originalRule)
        TripChargeSettingsStore(context).save(originalCharges)

        try {
            saveFareSettings(context, "1200", "700", "250", "120", "12", "90", "-1", "400")
            fail("negative pickup fee must be rejected")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(originalRule, FareRuleStore(context).load())
        assertEquals(originalCharges, TripChargeSettingsStore(context).load())
        context.clear()
    }
}

private class IsolatedPreferencesContext(base: Context, private val prefix: String) : ContextWrapper(base) {
    private val names = mutableSetOf<String>()

    override fun getSharedPreferences(name: String, mode: Int) =
        baseContext.getSharedPreferences("$prefix-$name", mode).also { names += "$prefix-$name" }

    fun clear() = names.forEach(baseContext::deleteSharedPreferences)
}
