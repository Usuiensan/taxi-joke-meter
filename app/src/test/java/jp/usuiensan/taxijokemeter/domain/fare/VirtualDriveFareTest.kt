package jp.usuiensan.taxijokemeter.domain.fare

import java.io.File
import java.time.Instant
import jp.usuiensan.taxijokemeter.data.location.FareMeasurementSession
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import jp.usuiensan.taxijokemeter.domain.measurement.FareMeasurementMode

class VirtualDriveFareTest {
    @Test
    fun replaysTheFullBundleWithAnExplicitFareRule() {
        val root = System.getenv("VIRTUAL_DRIVE_BUNDLE") ?: "C:/Users/miwam/Downloads/virtual_drive_test_bundle"
        val rows = File(root, "virtual_drive_1hz.csv").readLines().drop(1).map { it.split(',', limit = 24) }
        val rule = FareRule(1_000, 10, 1_000, 10, 10, 360)
        val session = FareMeasurementSession(rule).also { it.begin(OccupancyState.OCCUPIED) }
        var result = session.currentResult()

        rows.forEach { row ->
            session.add(row.toSample())?.let { next ->
                assertEquals(
                    if (row[20] == "1") FareMeasurementMode.TIME else FareMeasurementMode.DISTANCE,
                    next.mode,
                )
                result = next
            }
        }

        val accumulation = result ?: error("fixture produced no measurement")
        val breakdown = FareCalculator.breakdown(rule, accumulation.chargeableDistanceMeters, accumulation.lowSpeedSeconds)
        val truthDistance = rows.last()[18].toDouble()
        val rawObservedDistance = rows.last()[19].toDouble()

        assertEquals(20_586L, accumulation.actualDistanceMeters)
        assertEquals(19_315L, accumulation.chargeableDistanceMeters)
        assertEquals(697L, accumulation.lowSpeedSeconds)
        assertEquals(20_609.028, truthDistance, 0.001)
        assertTrue(rawObservedDistance - truthDistance > 2_000.0)
        assertEquals(1_936.111, breakdown.timeEquivalentDistanceMeters, 0.001)
        assertEquals(21_251.111, breakdown.effectiveDistanceMeters, 0.001)
        assertEquals(1_000L, breakdown.initialDistanceMeters)
        assertEquals(21L, breakdown.subsequentChargeCount)
        assertEquals(220L, breakdown.meteredFareYen)
    }

    private fun List<String>.toSample() = DrivingSample(
        dutySessionId = "virtual-drive",
        timestamp = Instant.parse(this[0]),
        latitude = this[13].toDoubleOrNull() ?: 0.0,
        longitude = this[14].toDoubleOrNull() ?: 0.0,
        accuracyMeters = 5.0,
        speedMps = this[7].toDoubleOrNull()?.div(3.6),
        bearingDegrees = this[9].toDoubleOrNull(),
        roadMode = when (this[5].toDouble()) {
            100.0 -> RoadMode.HIGHWAY2
            80.0 -> RoadMode.HIGHWAY1
            else -> RoadMode.GENERAL
        },
        occupancyState = OccupancyState.OCCUPIED,
        measurementQuality = if (this[11] == "1") MeasurementQuality.VALID else MeasurementQuality.UNAVAILABLE,
        elapsedRealtimeNanos = this[1].toLong() * 1_000_000_000L,
        speedSource = if (this[7].isBlank()) SpeedSource.UNAVAILABLE else SpeedSource.REPORTED,
    )
}
