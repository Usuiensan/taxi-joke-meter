package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripChargeSettingsStoreTest {
    @Test
    fun savedSettingsAreLoadedAfterStoreIsRecreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "trip_charge_settings_test"
        context.deleteSharedPreferences(name)
        val settings = TripChargeSettings(pickupFeeYen = 500, reservationFeeYen = 300)

        TripChargeSettingsStore(context, name).save(settings)

        assertEquals(settings, TripChargeSettingsStore(context, name).load())
        context.deleteSharedPreferences(name)
    }
}
