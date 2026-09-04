package jp.usuiensan.taxijokemeter.data.receipt

import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.TripChargeEntity
import jp.usuiensan.taxijokemeter.data.duty.TripEntity
import jp.usuiensan.taxijokemeter.data.duty.TripPlaceEntity
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.report.ReceiptChargeData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptPlaceData

class ReceiptRepository(private val database: DutyDatabase) {
    suspend fun findForServiceDate(serviceDate: String): List<ReceiptData> = database.dutyDao().findCompletedTrips(serviceDate).map { toReceipt(it) }
    suspend fun findForSession(sessionId: String): List<ReceiptData> = database.dutyDao().findTrips(sessionId).filter { it.status in setOf("COMPLETED", "VOIDED") }.map { toReceipt(it) }
    suspend fun findById(id: String): ReceiptData? {
        val trip = database.dutyDao().findTrip(id) ?: return null
        return toReceipt(trip)
    }

    private suspend fun toReceipt(trip: TripEntity): ReceiptData {
        val places = database.dutyDao().findTripPlaces(trip.id)
        val charges = database.dutyDao().findTripCharges(trip.id).map(::charge)
        val fareCharges = charges.filterNot { it.type in TOLL_TYPES }
        val tollCharges = charges.filter { it.type in TOLL_TYPES }
        val adjusted = (trip.totalFareYen - charges.sumOf(ReceiptChargeData::amountYen)).coerceAtLeast(0)
        return ReceiptData(
            tripId = trip.id, serviceDate = trip.serviceDate, tripNumber = trip.tripNumber,
            status = TripStatus.valueOf(trip.status), pickupAt = trip.pickupAt,
            pickupPlace = places.firstOrNull { it.type == "PICKUP" }?.toReceiptPlace(),
            waypoints = places.filter { it.type == "WAYPOINT" }.sortedBy(TripPlaceEntity::sequence).map { it.toReceiptPlace() },
            dropoffAt = trip.dropoffAt, dropoffPlace = places.firstOrNull { it.type == "DROPOFF" }?.toReceiptPlace(),
            passengerCount = trip.passengerCount, actualDistanceMeters = trip.actualDistanceMeters,
            chargeableDistanceMeters = trip.chargeableDistanceMeters, lowSpeedSeconds = trip.lowSpeedSeconds,
            meteredFareYen = trip.meteredFareYen, fareAdjustmentType = FareAdjustmentType.valueOf(trip.fareAdjustmentType),
            fareAdjustmentPercent = trip.fareAdjustmentPercent, adjustedMeteredFareYen = adjusted,
            fareCharges = fareCharges, tollCharges = tollCharges,
            fareTotalYen = adjusted + fareCharges.sumOf(ReceiptChargeData::amountYen),
            tollTotalYen = tollCharges.sumOf(ReceiptChargeData::amountYen), totalFareYen = trip.totalFareYen,
            fareRuleSnapshot = fareRule(trip),
            dutySessionId = trip.sessionId,
        )
    }

    private fun fareRule(trip: TripEntity) = listOf(trip.fareInitialDistanceMeters, trip.fareInitialFareYen, trip.fareDistanceIncrementMeters, trip.fareDistanceIncrementFareYen, trip.fareLowSpeedLimitKmh?.toLong(), trip.fareLowSpeedTimeIncrementSeconds?.toLong()).takeIf { it.all { value -> value != null } }?.let { FareRule(it[0]!!, it[1]!!, it[2]!!, it[3]!!, it[4]!!.toInt(), it[5]!!.toInt()) }
    private fun charge(value: TripChargeEntity) = ReceiptChargeData(value.type, value.amountYen, value.label)
    private fun TripPlaceEntity.toReceiptPlace() = ReceiptPlaceData(type, sequence, recordedAt, reportAddress ?: canonicalAddress, landmarkName)
    private companion object { val TOLL_TYPES = setOf("TOLL", "PARKING", "REIMBURSEMENT") }
}
