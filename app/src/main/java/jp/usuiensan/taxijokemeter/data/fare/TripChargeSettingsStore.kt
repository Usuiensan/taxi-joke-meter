package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context

data class TripChargeSettings(
    val pickupFeeYen: Long = 0,
    val reservationFeeYen: Long = 0,
) {
    init {
        require(pickupFeeYen >= 0 && reservationFeeYen >= 0)
    }
}

class TripChargeSettingsStore(context: Context, name: String = PREFERENCES_NAME) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun load() = TripChargeSettings(
        pickupFeeYen = preferences.getLong(KEY_PICKUP_FEE_YEN, 0),
        reservationFeeYen = preferences.getLong(KEY_RESERVATION_FEE_YEN, 0),
    )

    fun save(settings: TripChargeSettings) {
        preferences.edit()
            .putLong(KEY_PICKUP_FEE_YEN, settings.pickupFeeYen)
            .putLong(KEY_RESERVATION_FEE_YEN, settings.reservationFeeYen)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "trip_charge_settings"
        private const val KEY_PICKUP_FEE_YEN = "pickup_fee_yen"
        private const val KEY_RESERVATION_FEE_YEN = "reservation_fee_yen"
    }
}
