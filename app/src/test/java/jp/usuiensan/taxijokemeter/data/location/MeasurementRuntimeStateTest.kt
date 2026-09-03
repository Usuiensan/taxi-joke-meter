package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MeasurementRuntimeStateTest {
    @Test fun updateStatePreservesPreviousSampleAndSavedCount() {
        val state = MeasurementRuntimeState(DrivingSampleState("duty-1", RoadMode.GENERAL, OccupancyState.EMPTY))

        val updated = state.updateState(RoadMode.HIGHWAY1, OccupancyState.OCCUPIED)

        assertEquals(RoadMode.HIGHWAY1, updated.sampleState.roadMode)
        assertEquals(OccupancyState.OCCUPIED, updated.sampleState.occupancyState)
        assertEquals(state.previousSample, updated.previousSample)
    }

    @Test fun sequentialRoadModeChangesAreReflectedInNextSampleState() {
        val state = MeasurementRuntimeState(DrivingSampleState("duty-1", RoadMode.GENERAL, OccupancyState.EMPTY))

        val highway1 = state.updateState(RoadMode.HIGHWAY1, OccupancyState.EMPTY)
        val highway2 = highway1.updateState(RoadMode.HIGHWAY2, OccupancyState.EMPTY)

        assertEquals(RoadMode.HIGHWAY2, highway2.sampleState.roadMode)
    }

    @Test fun restDistanceRetainsSubMeterSegmentsUntilTheWholeMeterIsReached() {
        val state = MeasurementRuntimeState(DrivingSampleState("duty-1", RoadMode.GENERAL, OccupancyState.REST))

        val accumulated = state.advance(sample(35.0)).advance(sample(35.000004)).advance(sample(35.000008))

        assertTrue(accumulated.restDistanceMeters in 0.8..1.0)
    }

    private fun sample(latitude: Double) = DrivingSample(
        dutySessionId = "duty-1", timestamp = Instant.EPOCH, latitude = latitude, longitude = 139.0,
        accuracyMeters = 5.0, speedMps = 0.0, bearingDegrees = null, roadMode = RoadMode.GENERAL,
        occupancyState = OccupancyState.REST, measurementQuality = MeasurementQuality.VALID,
    )
}
