package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context

data class TripChargeSettings(
    val pickupFeeYen: Long = 0,
    val reservationFeeYen: Long = 0,
    val surchargePercent: Int = 0,
    val discountPercent: Int = 0,
) {
    init {
        require(pickupFeeYen >= 0 && reservationFeeYen >= 0 && surchargePercent in 0..999 && discountPercent in 0..999)
    }
}

class TripChargeSettingsStore(context: Context, name: String = PREFERENCES_NAME) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun load() = TripChargeSettings(
        pickupFeeYen = preferences.getLong(KEY_PICKUP_FEE_YEN, 0),
        reservationFeeYen = preferences.getLong(KEY_RESERVATION_FEE_YEN, 0),
        surchargePercent = preferences.getInt(KEY_SURCHARGE_PERCENT, 0),
        discountPercent = preferences.getInt(KEY_DISCOUNT_PERCENT, 0),
    )

    fun save(settings: TripChargeSettings) {
        preferences.edit()
            .putLong(KEY_PICKUP_FEE_YEN, settings.pickupFeeYen)
            .putLong(KEY_RESERVATION_FEE_YEN, settings.reservationFeeYen)
            .putInt(KEY_SURCHARGE_PERCENT, settings.surchargePercent)
            .putInt(KEY_DISCOUNT_PERCENT, settings.discountPercent)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "trip_charge_settings"
        private const val KEY_PICKUP_FEE_YEN = "pickup_fee_yen"
        private const val KEY_RESERVATION_FEE_YEN = "reservation_fee_yen"
        private const val KEY_SURCHARGE_PERCENT = "surcharge_percent"
        private const val KEY_DISCOUNT_PERCENT = "discount_percent"
    }
}
