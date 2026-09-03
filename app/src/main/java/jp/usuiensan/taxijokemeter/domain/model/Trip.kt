package jp.usuiensan.taxijokemeter.domain.model

import java.time.Instant

enum class PaymentMethod { CASH, CARD, QR, CORPORATE, OTHER, UNKNOWN }

data class Trip(
    val id: String,
    val pickupAt: Instant,
    val dropoffAt: Instant?,
    val pickupPlace: RecordedPlace?,
    val dropoffPlace: RecordedPlace?,
    val passengerCount: Int,
    val operatingDistanceMeters: Long,
    val fareYen: Long,
    val additionalChargeYen: Long,
    val paymentMethod: PaymentMethod,
    val note: String?,
    val status: TripStatus = TripStatus.COMPLETED,
    val lowSpeedSeconds: Long = 0,
    val revenueIncluded: Boolean = true,
    val serviceDate: String? = null,
    val tripNumber: Int? = null,
    val actualDistanceMeters: Long = operatingDistanceMeters,
    val chargeableDistanceMeters: Long = operatingDistanceMeters,
    val meteredFareYen: Long = fareYen,
    val totalFareYen: Long = fareYen + additionalChargeYen,
    val fareInitialDistanceMeters: Long? = null,
    val fareInitialFareYen: Long? = null,
    val fareDistanceIncrementMeters: Long? = null,
    val fareDistanceIncrementFareYen: Long? = null,
    val fareLowSpeedLimitKmh: Int? = null,
    val fareLowSpeedTimeIncrementSeconds: Int? = null,
) {
    init {
        require(passengerCount >= 0)
        require(operatingDistanceMeters >= 0)
        require(fareYen >= 0 && additionalChargeYen >= 0)
        require(lowSpeedSeconds >= 0)
        require(actualDistanceMeters >= 0 && chargeableDistanceMeters >= 0)
        require(meteredFareYen >= 0 && totalFareYen >= 0)
        if (status == TripStatus.ACTIVE) require(serviceDate != null && tripNumber != null)
    }
}
