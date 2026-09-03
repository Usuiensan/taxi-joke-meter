package jp.usuiensan.taxijokemeter.domain.model

data class FarePreset(val id: String, val label: String, val rule: FareRule)

object FarePresets {
    val TEN_YEN_PER_KM = FarePreset(
        "10_yen_per_km", "10円/km",
        FareRule(1_000, 10, 1_000, 10, 10, 360),
    )

    val BUILT_IN = listOf(TEN_YEN_PER_KM)
}
