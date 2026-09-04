package jp.usuiensan.taxijokemeter.domain.report

import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import java.time.Instant

data class ReceiptPlaceData(val type: String, val sequence: Int, val recordedAt: Instant, val address: String?, val landmarkName: String?)
data class ReceiptChargeData(val type: String, val amountYen: Long, val label: String?)

data class ReceiptData(
    val tripId: String,
    val serviceDate: String?,
    val tripNumber: Int?,
    val status: TripStatus,
    val pickupAt: Instant,
    val pickupPlace: ReceiptPlaceData?,
    val waypoints: List<ReceiptPlaceData>,
    val dropoffAt: Instant?,
    val dropoffPlace: ReceiptPlaceData?,
    val passengerCount: Int,
    val actualDistanceMeters: Long,
    val chargeableDistanceMeters: Long,
    val lowSpeedSeconds: Long,
    val meteredFareYen: Long,
    val fareAdjustmentType: FareAdjustmentType,
    val fareAdjustmentPercent: Int,
    val adjustedMeteredFareYen: Long,
    val fareCharges: List<ReceiptChargeData>,
    val tollCharges: List<ReceiptChargeData>,
    val fareTotalYen: Long,
    val tollTotalYen: Long,
    val totalFareYen: Long,
    val fareRuleSnapshot: FareRule?,
)
