package jp.usuiensan.taxijokemeter.domain.model

sealed interface EnergyEfficiency {
    data class KmPerLiter(val value: Double) : EnergyEfficiency
    data class KwhPer100Km(val value: Double) : EnergyEfficiency
}
