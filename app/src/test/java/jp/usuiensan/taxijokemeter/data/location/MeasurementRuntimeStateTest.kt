package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
