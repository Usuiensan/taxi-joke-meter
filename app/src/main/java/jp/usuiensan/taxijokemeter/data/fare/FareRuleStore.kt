package jp.usuiensan.taxijokemeter.data.fare

import android.content.Context
import jp.usuiensan.taxijokemeter.domain.fare.FuelCostFareFactory
import jp.usuiensan.taxijokemeter.domain.model.EnergyCostProfile
import jp.usuiensan.taxijokemeter.domain.model.EnergyEfficiency
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.FuelType
import jp.usuiensan.taxijokemeter.domain.model.Powertrain

class FareRuleStore(context: Context, name: String = PREFERENCES_NAME) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun load(): FareRule {
        if (!KEYS.all(preferences::contains)) return defaultRule()
        return FareRule(
            preferences.getLong(KEY_INITIAL_DISTANCE, 0),
            preferences.getLong(KEY_INITIAL_FARE, 0),
            preferences.getLong(KEY_DISTANCE_INCREMENT, 0),
            preferences.getLong(KEY_INCREMENT_FARE, 0),
            preferences.getInt(KEY_LOW_SPEED_LIMIT, 0),
            preferences.getInt(KEY_LOW_SPEED_SECONDS, 0),
        )
    }

    fun save(rule: FareRule) {
        preferences.edit()
            .putLong(KEY_INITIAL_DISTANCE, rule.initialDistanceMeters)
            .putLong(KEY_INITIAL_FARE, rule.initialFareYen)
            .putLong(KEY_DISTANCE_INCREMENT, rule.distanceIncrementMeters)
            .putLong(KEY_INCREMENT_FARE, rule.distanceIncrementFareYen)
            .putInt(KEY_LOW_SPEED_LIMIT, rule.lowSpeedLimitKmh)
            .putInt(KEY_LOW_SPEED_SECONDS, rule.lowSpeedTimeIncrementSeconds)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "fare_rule"
        private const val KEY_INITIAL_DISTANCE = "initial_distance_meters"
        private const val KEY_INITIAL_FARE = "initial_fare_yen"
        private const val KEY_DISTANCE_INCREMENT = "distance_increment_meters"
        private const val KEY_INCREMENT_FARE = "distance_increment_fare_yen"
        private const val KEY_LOW_SPEED_LIMIT = "low_speed_limit_kmh"
        private const val KEY_LOW_SPEED_SECONDS = "low_speed_time_increment_seconds"
        private val KEYS = listOf(KEY_INITIAL_DISTANCE, KEY_INITIAL_FARE, KEY_DISTANCE_INCREMENT, KEY_INCREMENT_FARE, KEY_LOW_SPEED_LIMIT, KEY_LOW_SPEED_SECONDS)

        fun defaultRule() = FuelCostFareFactory.create(
            EnergyCostProfile(FuelType.REGULAR, Powertrain.ICE, 180.0, EnergyEfficiency.KmPerLiter(15.0)),
            1_000,
            200,
            10,
        )
    }
}
