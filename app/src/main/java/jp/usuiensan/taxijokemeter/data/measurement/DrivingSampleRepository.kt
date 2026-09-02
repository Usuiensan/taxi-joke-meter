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
}

private fun DrivingSample.toEntity() = DrivingSampleEntity(
    timestamp.toEpochMilli(), latitude, longitude, accuracyMeters, speedMps, bearingDegrees,
    roadMode.name, occupancyState.name, measurementQuality.name,
)

private fun DrivingSampleEntity.toDomain() = DrivingSample(
    Instant.ofEpochMilli(timestamp), latitude, longitude, accuracyMeters, speedMps, bearingDegrees,
    RoadMode.valueOf(roadMode), OccupancyState.valueOf(occupancyState), MeasurementQuality.valueOf(measurementQuality),
)
