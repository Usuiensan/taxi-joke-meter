package jp.usuiensan.taxijokemeter.data.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class LocationSampleMapperTest {
    private val now = Instant.parse("2026-09-03T00:00:10Z")
    private val state = DrivingSampleState("duty-1", RoadMode.GENERAL, OccupancyState.EMPTY)
    private val mapper = LocationSampleMapper(MeasurementQualityPolicy(20.0, Duration.ofSeconds(5)))

    @Test fun mapsLocationFieldsAndState() {
        val location = Location("gps").apply {
            time = now.toEpochMilli()
            latitude = 35.0
            longitude = 139.0
            accuracy = 5.0f
            speed = 3.5f
            bearing = 90.0f
            elapsedRealtimeNanos = 12_345_678_900L
        }

        val sample = mapper.map(location, state, now)

        assertEquals("duty-1", sample.dutySessionId)
        assertEquals(MeasurementQuality.VALID, sample.measurementQuality)
        assertEquals(3.5, sample.speedMps!!, 0.0)
        assertEquals(SpeedSource.REPORTED, sample.speedSource)
        assertEquals(12_345_678_900L, sample.elapsedRealtimeNanos)
        assertEquals(90.0, sample.bearingDegrees!!, 0.0)
    }

    @Test fun keepsMissingSpeedAsUnknown() {
        val location = Location("gps").apply {
            time = now.toEpochMilli()
            latitude = 35.0
            longitude = 139.0
            accuracy = 5.0f
            elapsedRealtimeNanos = 1_000_000L
        }

        val sample = mapper.map(location, state, now)

        assertEquals(null, sample.speedMps)
        assertEquals(SpeedSource.UNAVAILABLE, sample.speedSource)
    }

    @Test fun classifiesUnavailableStaleAndPoorAccuracy() {
        assertEquals(MeasurementQuality.UNAVAILABLE, mapper.map(null, state, now).measurementQuality)
        val stale = Location("gps").apply { time = now.minusSeconds(6).toEpochMilli(); accuracy = 1.0f }
        val poor = Location("gps").apply { time = now.toEpochMilli(); accuracy = 21.0f }
        assertEquals(MeasurementQuality.STALE, mapper.map(stale, state, now).measurementQuality)
        assertEquals(MeasurementQuality.POOR_ACCURACY, mapper.map(poor, state, now).measurementQuality)
    }
}
