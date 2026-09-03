package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.withTransaction
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.DutySession
import jp.usuiensan.taxijokemeter.domain.model.LocationPrecision
import jp.usuiensan.taxijokemeter.domain.model.LocationSource
import jp.usuiensan.taxijokemeter.domain.model.RecordedPlace
import jp.usuiensan.taxijokemeter.domain.model.Trip
import jp.usuiensan.taxijokemeter.domain.model.PaymentMethod
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class DutyRecordRepository(private val database: DutyDatabase) {
    suspend fun save(session: DutySession) = database.withTransaction {
        val dao = database.dutyDao()
        dao.insertSession(DutySessionEntity(session.id, session.startedAt, session.endedAt))
        dao.deleteTrips(session.id)
        dao.deleteEvents(session.id)
        dao.insertTrips(session.trips.map { it.toEntity(session.id) })
        dao.insertEvents(session.events.map { it.toEntity(session.id) })
    }

    suspend fun findById(id: String): DutySession? {
        val dao = database.dutyDao()
        val session = dao.findSession(id) ?: return null
        return DutySession(session.id, session.startedAt, session.endedAt, dao.findTrips(id).map(TripEntity::toDomain), dao.findEvents(id).map(DutyEventEntity::toDomain))
    }

    suspend fun appendEvent(sessionId: String, event: DutyEvent) {
        database.dutyDao().insertEvent(event.toEntity(sessionId))
    }

    suspend fun appendTrip(sessionId: String, trip: Trip) {
        database.dutyDao().insertTrip(trip.toEntity(sessionId))
    }

    suspend fun markEnded(id: String, endedAt: Instant) {
        database.dutyDao().updateEndedAt(id, endedAt)
    }

    suspend fun startTrip(sessionId: String, pickupAt: Instant, fareRule: FareRule): Trip = database.withTransaction {
        val serviceDate = pickupAt.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate().toString()
        val tripNumber = (database.dutyDao().maxTripNumber(serviceDate) ?: 0) + 1
        Trip(
            id = UUID.randomUUID().toString(), pickupAt = pickupAt, dropoffAt = null,
            pickupPlace = null, dropoffPlace = null, passengerCount = 1,
            operatingDistanceMeters = 0, fareYen = fareRule.initialFareYen, additionalChargeYen = 0,
            paymentMethod = PaymentMethod.UNKNOWN, note = null, status = TripStatus.ACTIVE,
            serviceDate = serviceDate, tripNumber = tripNumber, fareInitialDistanceMeters = fareRule.initialDistanceMeters,
            fareInitialFareYen = fareRule.initialFareYen, fareDistanceIncrementMeters = fareRule.distanceIncrementMeters,
            fareDistanceIncrementFareYen = fareRule.distanceIncrementFareYen, fareLowSpeedLimitKmh = fareRule.lowSpeedLimitKmh,
            fareLowSpeedTimeIncrementSeconds = fareRule.lowSpeedTimeIncrementSeconds,
        ).also { database.dutyDao().insertTrip(it.toEntity(sessionId)) }
    }

    suspend fun findTrip(id: String): Trip? = database.dutyDao().findTrip(id)?.toDomain()

    suspend fun updateTripMeasurement(id: String, actualDistanceMeters: Long, chargeableDistanceMeters: Long, lowSpeedSeconds: Long, meteredFareYen: Long) {
        database.dutyDao().updateTripMeasurement(id, actualDistanceMeters, chargeableDistanceMeters, lowSpeedSeconds, meteredFareYen, meteredFareYen)
    }

    suspend fun finishTrip(trip: Trip, status: TripStatus, revenueIncluded: Boolean) {
        database.withTransaction {
            database.dutyDao().updateTripMeasurement(trip.id, trip.actualDistanceMeters, trip.chargeableDistanceMeters, trip.lowSpeedSeconds, trip.meteredFareYen, trip.totalFareYen)
            database.dutyDao().finalizeTrip(trip.id, trip.dropoffAt, status.name, revenueIncluded)
        }
    }

    suspend fun addChargeIfAbsent(tripId: String, type: String, amountYen: Long, appliedAt: Instant, label: String? = null) = database.withTransaction {
        if (amountYen == 0L) return@withTransaction
        val dao = database.dutyDao()
        if (dao.findTripCharges(tripId).none { it.type == type }) {
            dao.insertTripCharge(TripChargeEntity(UUID.randomUUID().toString(), tripId, type, amountYen, appliedAt, label))
        }
    }

    suspend fun addWaypoint(tripId: String, place: RecordedPlace?) = database.withTransaction {
        if (place != null) {
            val dao = database.dutyDao()
            val sequence = (dao.maxWaypointSequence(tripId) ?: 0) + 1
            dao.insertTripPlace(place.toEntity(tripId, "WAYPOINT", sequence))
        }
    }

    suspend fun upsertDropoff(tripId: String, place: RecordedPlace?) = database.withTransaction {
        if (place != null) {
            val dao = database.dutyDao()
            val existing = dao.findTripPlace(tripId, "DROPOFF", 0)
            val value = place.toEntity(tripId, "DROPOFF", 0, existing?.id ?: UUID.randomUUID().toString())
            dao.insertTripPlace(value)
        }
    }

    suspend fun savePickupPlace(tripId: String, place: RecordedPlace?) = database.withTransaction {
        if (place != null) database.dutyDao().insertTripPlace(place.toEntity(tripId, "PICKUP", 0))
    }

    suspend fun updatePassengerCount(tripId: String, passengerCount: Int) {
        database.dutyDao().updatePassengerCount(tripId, passengerCount)
    }

    suspend fun startRestInterval(sessionId: String, startedAt: Instant, place: RecordedPlace? = null): String {
        val id = UUID.randomUUID().toString()
        database.dutyDao().insertRestInterval(RestIntervalEntity(id, sessionId, startedAt, null, place?.latitude, place?.longitude, place?.accuracyMeters, 0, null))
        return id
    }

    suspend fun updateRestDistance(id: String, distanceMeters: Long) {
        database.dutyDao().updateRestDistance(id, distanceMeters)
    }

    suspend fun finishRestInterval(id: String, endedAt: Instant, reason: String) {
        database.dutyDao().finishRestInterval(id, endedAt, reason)
    }
}

private fun Trip.toEntity(sessionId: String) = TripEntity(id, sessionId, pickupAt, dropoffAt, pickupPlace.latitude(), pickupPlace.longitude(), pickupPlace.accuracy(), pickupPlace.recordedAt(), pickupPlace.address(), pickupPlace.precision(), pickupPlace.sourceName(), dropoffPlace.latitude(), dropoffPlace.longitude(), dropoffPlace.accuracy(), dropoffPlace.recordedAt(), dropoffPlace.address(), dropoffPlace.precision(), dropoffPlace.sourceName(), passengerCount, operatingDistanceMeters, fareYen, additionalChargeYen, paymentMethod.name, note, status.name, lowSpeedSeconds, revenueIncluded, serviceDate, tripNumber, actualDistanceMeters, chargeableDistanceMeters, meteredFareYen, totalFareYen, fareInitialDistanceMeters, fareInitialFareYen, fareDistanceIncrementMeters, fareDistanceIncrementFareYen, fareLowSpeedLimitKmh, fareLowSpeedTimeIncrementSeconds)

private fun TripEntity.toDomain() = Trip(id = id, pickupAt = pickupAt, dropoffAt = dropoffAt, pickupPlace = pickupPlace(), dropoffPlace = dropoffPlace(), passengerCount = passengerCount, operatingDistanceMeters = operatingDistanceMeters, fareYen = fareYen, additionalChargeYen = additionalChargeYen, paymentMethod = PaymentMethod.valueOf(paymentMethod), note = note, status = TripStatus.valueOf(status), lowSpeedSeconds = lowSpeedSeconds, revenueIncluded = revenueIncluded, serviceDate = serviceDate, tripNumber = tripNumber, actualDistanceMeters = actualDistanceMeters, chargeableDistanceMeters = chargeableDistanceMeters, meteredFareYen = meteredFareYen, totalFareYen = totalFareYen, fareInitialDistanceMeters = fareInitialDistanceMeters, fareInitialFareYen = fareInitialFareYen, fareDistanceIncrementMeters = fareDistanceIncrementMeters, fareDistanceIncrementFareYen = fareDistanceIncrementFareYen, fareLowSpeedLimitKmh = fareLowSpeedLimitKmh, fareLowSpeedTimeIncrementSeconds = fareLowSpeedTimeIncrementSeconds)

private fun DutyEvent.toEntity(sessionId: String) = DutyEventEntity(id, sessionId, type.name, occurredAt, place?.latitude, place?.longitude, place?.accuracyMeters, place?.recordedAt, place?.addressText, place?.locationPrecision?.name, place?.source?.name, safetyRoadModeChange?.from?.name, safetyRoadModeChange?.to?.name, note)

private fun DutyEventEntity.toDomain() = DutyEvent(id, DutyEventType.valueOf(type), occurredAt, place(), note, modeChange())

private fun RecordedPlace?.latitude() = this?.latitude
private fun RecordedPlace?.longitude() = this?.longitude
private fun RecordedPlace?.accuracy() = this?.accuracyMeters
private fun RecordedPlace?.recordedAt() = this?.recordedAt
private fun RecordedPlace?.address() = this?.addressText
private fun RecordedPlace?.precision() = this?.locationPrecision?.name
private fun RecordedPlace?.sourceName() = this?.source?.name

private fun RecordedPlace.toEntity(tripId: String, type: String, sequence: Int, id: String = UUID.randomUUID().toString()) = TripPlaceEntity(id, tripId, type, sequence, recordedAt, latitude, longitude, accuracyMeters, addressText, addressText, null, null, locationPrecision.name, source.name)

private fun TripEntity.pickupPlace() = placeOrNull(pickupLatitude, pickupLongitude, pickupAccuracyMeters, pickupRecordedAt, pickupAddressText, pickupLocationPrecision, pickupSource)
private fun TripEntity.dropoffPlace() = placeOrNull(dropoffLatitude, dropoffLongitude, dropoffAccuracyMeters, dropoffRecordedAt, dropoffAddressText, dropoffLocationPrecision, dropoffSource)
private fun DutyEventEntity.place() = placeOrNull(latitude, longitude, accuracyMeters, recordedAt, addressText, locationPrecision, source)
private fun DutyEventEntity.modeChange() = if (safetyRoadModeFrom == null || safetyRoadModeTo == null) null else SafetyRoadModeChange(RoadMode.valueOf(safetyRoadModeFrom), RoadMode.valueOf(safetyRoadModeTo))
private fun placeOrNull(latitude: Double?, longitude: Double?, accuracy: Double?, recordedAt: java.time.Instant?, address: String?, precision: String?, source: String?): RecordedPlace? = if (latitude == null || longitude == null || recordedAt == null) null else RecordedPlace(latitude, longitude, accuracy, recordedAt, address, precision?.let(LocationPrecision::valueOf) ?: LocationPrecision.UNKNOWN, source?.let(LocationSource::valueOf) ?: LocationSource.UNKNOWN)
