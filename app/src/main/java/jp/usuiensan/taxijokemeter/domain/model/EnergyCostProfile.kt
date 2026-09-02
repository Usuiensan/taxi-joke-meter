package jp.usuiensan.taxijokemeter.domain.model

data class EnergyCostProfile(
    val fuelType: FuelType,
    val powertrain: Powertrain,
    val unitPriceYen: Double,
    val efficiency: EnergyEfficiency,
) {
    init { require(unitPriceYen > 0); require(efficiency.value > 0) }
}

private val EnergyEfficiency.value: Double
    get() = when (this) {
        is EnergyEfficiency.KmPerLiter -> value
        is EnergyEfficiency.KwhPer100Km -> value
    }
