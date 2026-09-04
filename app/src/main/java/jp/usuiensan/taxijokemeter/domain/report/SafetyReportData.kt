package jp.usuiensan.taxijokemeter.domain.report

import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluation
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluationInput
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluator
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvent
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import java.time.Duration
import java.time.Instant

data class SafetyReportData(
    val generatedAt: Instant,
    val dutySessionId: String,
    val samples: List<DrivingSample>,
    val events: List<SafetyEvent>,
    val trips: List<ReceiptData>,
    val occupied: SafetyEvaluation,
    val empty: SafetyEvaluation,
    val overall: SafetyEvaluation,
) {
    companion object {
        fun from(generatedAt: Instant, samples: List<DrivingSample>, events: List<SafetyEvent>, trips: List<ReceiptData>): SafetyReportData {
            val ordered = samples.sortedBy(DrivingSample::timestamp)
            val occupied = ordered.filter { it.occupancyState == OccupancyState.OCCUPIED }
            val empty = ordered.filter { it.occupancyState != OccupancyState.OCCUPIED }
            return SafetyReportData(generatedAt, ordered.firstOrNull()?.dutySessionId ?: trips.firstOrNull()?.dutySessionId.orEmpty(), ordered, events, trips,
                evaluate(occupied, events), evaluate(empty, events), evaluate(ordered, events))
        }

        private fun evaluate(samples: List<DrivingSample>, events: List<SafetyEvent>): SafetyEvaluation {
            val valid = samples.filter { it.isValid }
            val distance = valid.zipWithNext().sumOf { (a, b) -> jp.usuiensan.taxijokemeter.domain.measurement.RestDistanceAccumulator.add(a, b) } / 1000.0
            val relevant = events.filter { event -> samples.any { it.timestamp.toEpochMilli() == event.startedAtEpochMillis } }
            val accel = relevant.filter { it.type == SafetyEventType.HARD_ACCELERATION }
            val brake = relevant.filter { it.type == SafetyEventType.HARD_DECELERATION }
            val input = SafetyEvaluationInput(distanceKm = distance,
                weakAccelCount = accel.count { it.peakValue < 4.0 }, strongAccelCount = accel.count { it.peakValue >= 4.0 },
                weakBrakeCount = brake.count { it.peakValue < 4.5 }, strongBrakeCount = brake.count { it.peakValue >= 4.5 },
                overspeedSecondsByBand = overspeedSeconds(valid),
                overspeedEventCount = SafetyEvaluator.countOverspeedEvents(overspeedDurations(valid)))
            return SafetyEvaluator.evaluate(input)
        }

        private fun overspeedDurations(samples: List<DrivingSample>): List<Double> = buildList {
            var started: DrivingSample? = null
            samples.zipWithNext().forEach { (a, b) ->
                val over = a.speedMps != null && a.speedMps * 3.6 - a.roadMode.safety.limitKmh > 5.0
                if (over && started == null) started = a
                if (!over && started != null) { add(Duration.between(started!!.timestamp, a.timestamp).toMillis() / 1000.0); started = null }
                if (b == samples.lastOrNull() && started != null) add(Duration.between(started!!.timestamp, b.timestamp).toMillis() / 1000.0)
            }
        }

        private fun overspeedSeconds(samples: List<DrivingSample>): Map<Int, Double> = samples.zipWithNext().filter { (a, b) -> a.speedMps != null && a.isValid && b.isValid }.fold(mutableMapOf()) { result, (a, b) ->
            val seconds = Duration.between(a.timestamp, b.timestamp).toMillis().coerceAtLeast(0) / 1000.0
            val excess = a.speedMps!! * 3.6 - a.roadMode.safety.limitKmh
            val band = when { excess <= 5 -> 0; excess <= 10 -> 5; excess <= 15 -> 10; excess <= 20 -> 15; excess <= 25 -> 20; excess <= 30 -> 25; else -> 30 }
            result[band] = (result[band] ?: 0.0) + seconds
            result
        }
    }

    fun maxSpeed(mode: RoadMode): Int? = samples.filter { it.isValid && it.roadMode == mode }.mapNotNull { it.speedMps?.times(3.6)?.toInt() }.maxOrNull()
    fun distanceKm(): Double = samples.filter { it.isValid }.zipWithNext().sumOf { (a, b) -> jp.usuiensan.taxijokemeter.domain.measurement.RestDistanceAccumulator.add(a, b) } / 1000.0
}
