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
) {
    init {
        require(passengerCount >= 0)
        require(operatingDistanceMeters >= 0)
        require(fareYen >= 0 && additionalChargeYen >= 0)
        require(lowSpeedSeconds >= 0)
    }
}
