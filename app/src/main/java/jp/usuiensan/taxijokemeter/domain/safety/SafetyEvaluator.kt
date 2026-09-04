package jp.usuiensan.taxijokemeter.domain.safety

import kotlin.math.log2

enum class SafetyRank { A, B, C, D, E, INSUFFICIENT_DATA }

data class SafetyEvaluationInput(
    val distanceKm: Double,
    val weakAccelCount: Int = 0,
    val strongAccelCount: Int = 0,
    val weakBrakeCount: Int = 0,
    val strongBrakeCount: Int = 0,
    val overspeedSecondsByBand: Map<Int, Double> = emptyMap(),
    val overspeedEventCount: Int = 0,
)

data class SafetyEvaluation(
    val input: SafetyEvaluationInput,
    val rawPenalty: Double,
    val distanceFactor: Double,
    val finalPenalty: Double,
    val score: Double?,
    val rank: SafetyRank,
) {
    val isScored: Boolean get() = score != null
}

object SafetyEvaluator {
    private val speedPenaltyPerMinute = mapOf(0 to 0.0, 5 to 0.25, 10 to 0.5, 15 to 1.5, 20 to 3.0, 25 to 6.0, 30 to 10.0)

    fun evaluate(input: SafetyEvaluationInput): SafetyEvaluation {
        val raw = input.weakAccelCount * 1.5 + input.strongAccelCount * 3.0 +
            input.weakBrakeCount * 1.5 + input.strongBrakeCount * 3.0 +
            input.overspeedSecondsByBand.entries.sumOf { (band, seconds) -> (speedPenaltyPerMinute[band] ?: 10.0) * seconds / 60.0 } +
            input.overspeedEventCount * 0.25
        val factor = distanceFactor(input.distanceKm)
        val finalPenalty = raw * factor
        val score = if (input.distanceKm < 5.0) null else (100.0 - finalPenalty).coerceIn(0.0, 100.0).let { "%.1f".format(java.util.Locale.US, it).toDouble() }
        return SafetyEvaluation(input, raw, factor, finalPenalty, score, score?.let(::rank) ?: SafetyRank.INSUFFICIENT_DATA)
    }

    fun distanceFactor(distanceKm: Double): Double = (1.0 - 0.05 * log2((distanceKm.coerceAtLeast(0.001)) / 50.0)).coerceIn(0.80, 1.15)

    fun countOverspeedEvents(durationsSeconds: List<Double>): Int = durationsSeconds.count { it >= 5.0 }

    private fun rank(score: Double) = when {
        score >= 91.0 -> SafetyRank.A
        score >= 81.0 -> SafetyRank.B
        score >= 71.0 -> SafetyRank.C
        score >= 61.0 -> SafetyRank.D
        else -> SafetyRank.E
    }
}
