package jp.usuiensan.taxijokemeter.domain.model

data class FarePreset(
    val id: String,
    val label: String,
    val rule: FareRule,
    val pickupFeeYen: Long,
    val reservationFeeYen: Long,
    val surchargePercent: Int,
    val discountPercent: Int,
)

object FarePresets {
    val TEN_YEN_PER_KM = FarePreset(
        "10_yen_per_km", "10円/km",
        FareRule(1_000, 10, 200, 2, 10, 75),
        200, 200, 20, 10,
    )
    val TAXI_URBAN = FarePreset(
        "taxi_urban", "タクシー都市部",
        FareRule(1_000, 500, 232, 100, 10, 85),
        500, 500, 20, 10,
    )
    val TAXI_SUBURBAN = FarePreset(
        "taxi_suburban", "タクシー郊外部",
        FareRule(1_000, 500, 211, 100, 10, 80),
        500, 500, 20, 10,
    )
    val TAXI_RURAL = FarePreset(
        "taxi_rural", "タクシー地方部",
        FareRule(900, 470, 271, 100, 10, 100),
        300, 500, 20, 10,
    )

    val BUILT_IN = listOf(TEN_YEN_PER_KM, TAXI_URBAN, TAXI_SUBURBAN, TAXI_RURAL)
}
