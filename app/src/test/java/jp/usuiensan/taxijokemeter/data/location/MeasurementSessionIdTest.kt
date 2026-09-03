package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementSessionIdTest {
    @Test
    fun recreatedControllerCanReuseSessionIdPublishedByRunningService() {
        MeasurementDebugStore.update(MeasurementDebugSnapshot(running = true, sessionId = "session-1"))

        val recreatedControllerView = resolveMeasurementSessionId(null, MeasurementDebugStore.snapshot.value)

        assertEquals("session-1", recreatedControllerView)
    }
}
