package jp.usuiensan.taxijokemeter.data.measurement

import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Instant

class DrivingSampleRepository(private val database: DutyDatabase) {
    suspend fun save(sample: DrivingSample) {
        database.drivingSampleDao().insert(sample.toEntity())
    }

    suspend fun findAll(): List<DrivingSample> = database.drivingSampleDao().findAll().map(DrivingSampleEntity::toDomain)

    suspend fun findByDutySessionId(dutySessionId: String): List<DrivingSample> =
        database.drivingSampleDao().findByDutySessionId(dutySessionId).map(DrivingSampleEntity::toDomain)

    suspend fun latestValidByDutySessionId(dutySessionId: String): DrivingSample? =
        findByDutySessionId(dutySessionId).lastOrNull(DrivingSample::isValid)
}

private fun DrivingSample.toEntity() = DrivingSampleEntity(
    id = 0, dutySessionId = dutySessionId, timestampEpochSecond = timestamp.epochSecond, timestampNano = timestamp.nano,
    elapsedRealtimeNanos = elapsedRealtimeNanos, latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters,
    speedMps = speedMps, speedSource = speedSource.name, bearingDegrees = bearingDegrees, roadMode = roadMode.name,
    occupancyState = occupancyState.name, measurementQuality = measurementQuality.name,
)

private fun DrivingSampleEntity.toDomain() = DrivingSample(
    dutySessionId = dutySessionId, timestamp = Instant.ofEpochSecond(timestampEpochSecond, timestampNano.toLong()),
    elapsedRealtimeNanos = elapsedRealtimeNanos, latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters,
    speedMps = speedMps, speedSource = SpeedSource.valueOf(speedSource), bearingDegrees = bearingDegrees,
    roadMode = RoadMode.valueOf(roadMode), occupancyState = OccupancyState.valueOf(occupancyState),
    measurementQuality = MeasurementQuality.valueOf(measurementQuality),
)
