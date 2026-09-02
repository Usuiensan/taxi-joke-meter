package jp.usuiensan.taxijokemeter.data.measurement

import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

class DrivingSampleRepository(private val database: DutyDatabase) {
    suspend fun save(sample: DrivingSample) {
        database.drivingSampleDao().insert(sample.toEntity())
    }

    suspend fun findAll(): List<DrivingSample> = database.drivingSampleDao().findAll().map(DrivingSampleEntity::toDomain)

    suspend fun findByDutySessionId(dutySessionId: String): List<DrivingSample> =
        database.drivingSampleDao().findByDutySessionId(dutySessionId).map(DrivingSampleEntity::toDomain)
}

private fun DrivingSample.toEntity() = DrivingSampleEntity(
    0, dutySessionId, timestamp.epochSecond, timestamp.nano, latitude, longitude, accuracyMeters, speedMps, bearingDegrees,
    roadMode.name, occupancyState.name, measurementQuality.name,
)

private fun DrivingSampleEntity.toDomain() = DrivingSample(
    dutySessionId, Instant.ofEpochSecond(timestampEpochSecond, timestampNano.toLong()), latitude, longitude, accuracyMeters, speedMps, bearingDegrees,
    RoadMode.valueOf(roadMode), OccupancyState.valueOf(occupancyState), MeasurementQuality.valueOf(measurementQuality),
)
