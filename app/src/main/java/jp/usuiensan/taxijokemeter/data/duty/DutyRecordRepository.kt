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
}

private fun Trip.toEntity(sessionId: String) = TripEntity(id, sessionId, pickupAt, dropoffAt, pickupPlace.latitude(), pickupPlace.longitude(), pickupPlace.accuracy(), pickupPlace.recordedAt(), pickupPlace.address(), pickupPlace.precision(), pickupPlace.sourceName(), dropoffPlace.latitude(), dropoffPlace.longitude(), dropoffPlace.accuracy(), dropoffPlace.recordedAt(), dropoffPlace.address(), dropoffPlace.precision(), dropoffPlace.sourceName(), passengerCount, operatingDistanceMeters, fareYen, additionalChargeYen, paymentMethod.name, note)

private fun TripEntity.toDomain() = Trip(id, pickupAt, dropoffAt, pickupPlace(), dropoffPlace(), passengerCount, operatingDistanceMeters, fareYen, additionalChargeYen, PaymentMethod.valueOf(paymentMethod), note)

private fun DutyEvent.toEntity(sessionId: String) = DutyEventEntity(id, sessionId, type.name, occurredAt, place?.latitude, place?.longitude, place?.accuracyMeters, place?.recordedAt, place?.addressText, place?.locationPrecision?.name, place?.source?.name, safetyRoadModeChange?.from?.name, safetyRoadModeChange?.to?.name, note)

private fun DutyEventEntity.toDomain() = DutyEvent(id, DutyEventType.valueOf(type), occurredAt, place(), note, modeChange())

private fun RecordedPlace?.latitude() = this?.latitude
private fun RecordedPlace?.longitude() = this?.longitude
private fun RecordedPlace?.accuracy() = this?.accuracyMeters
private fun RecordedPlace?.recordedAt() = this?.recordedAt
private fun RecordedPlace?.address() = this?.addressText
private fun RecordedPlace?.precision() = this?.locationPrecision?.name
private fun RecordedPlace?.sourceName() = this?.source?.name

private fun TripEntity.pickupPlace() = placeOrNull(pickupLatitude, pickupLongitude, pickupAccuracyMeters, pickupRecordedAt, pickupAddressText, pickupLocationPrecision, pickupSource)
private fun TripEntity.dropoffPlace() = placeOrNull(dropoffLatitude, dropoffLongitude, dropoffAccuracyMeters, dropoffRecordedAt, dropoffAddressText, dropoffLocationPrecision, dropoffSource)
private fun DutyEventEntity.place() = placeOrNull(latitude, longitude, accuracyMeters, recordedAt, addressText, locationPrecision, source)
private fun DutyEventEntity.modeChange() = if (safetyRoadModeFrom == null || safetyRoadModeTo == null) null else SafetyRoadModeChange(RoadMode.valueOf(safetyRoadModeFrom), RoadMode.valueOf(safetyRoadModeTo))
private fun placeOrNull(latitude: Double?, longitude: Double?, accuracy: Double?, recordedAt: java.time.Instant?, address: String?, precision: String?, source: String?): RecordedPlace? = if (latitude == null || longitude == null || recordedAt == null) null else RecordedPlace(latitude, longitude, accuracy, recordedAt, address, precision?.let(LocationPrecision::valueOf) ?: LocationPrecision.UNKNOWN, source?.let(LocationSource::valueOf) ?: LocationSource.UNKNOWN)
