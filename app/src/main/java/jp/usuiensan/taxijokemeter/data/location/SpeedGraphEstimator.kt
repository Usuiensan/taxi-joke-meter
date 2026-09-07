package jp.usuiensan.taxijokemeter.data.location

import kotlin.math.abs
import kotlin.math.round

data class SpeedGraphEstimate(val speedMps: Double?, val graphSpeedMps: Double?)

class SpeedGraphEstimator {
    private val gpsSpeeds = ArrayDeque<Double>(3)
    private var filteredSpeedMps: Double? = null
    private var quantizedKmh: Int? = null
    private var lastGpsNanos: Long? = null
    private var lastUpdateNanos: Long? = null
    private var imuSpeedMps: Double? = null
    private var inImuRecovery = false

    fun reset() {
        gpsSpeeds.clear(); filteredSpeedMps = null; quantizedKmh = null
        lastGpsNanos = null; lastUpdateNanos = null; imuSpeedMps = null; inImuRecovery = false
    }

    fun update(timestampNanos: Long, gpsSpeedMps: Double?, imuAccelerationMps2: Double?, imuStable: Boolean): SpeedGraphEstimate {
        val result = if (gpsSpeedMps != null && gpsSpeedMps.isFinite() && gpsSpeedMps >= 0.0) {
            gpsSpeeds.addLast(gpsSpeedMps)
            if (gpsSpeeds.size > 3) gpsSpeeds.removeFirst()
            val median = gpsSpeeds.sorted()[gpsSpeeds.size / 2]
            val previous = filteredSpeedMps
            val alpha = adaptiveAlpha(imuAccelerationMps2)
            val filtered = if (previous == null) median else previous + alpha * (median - previous)
            val recovering = inImuRecovery
            filteredSpeedMps = when {
                !recovering || previous == null -> filtered
                abs(gpsSpeedMps - (imuSpeedMps ?: gpsSpeedMps)) > 15.0 / 3.6 -> median
                else -> {
                    val recoverySeconds = ((timestampNanos - (lastUpdateNanos ?: timestampNanos)).coerceAtLeast(0L)) / 1e9
                    previous + (filtered - previous) * (recoverySeconds / 2.0).coerceIn(0.0, 1.0)
                }
            }
            lastGpsNanos = timestampNanos
            lastUpdateNanos = timestampNanos
            imuSpeedMps = filteredSpeedMps
            inImuRecovery = false
            estimate(filteredSpeedMps)
        } else {
            val gpsAge = lastGpsNanos?.let { timestampNanos - it } ?: Long.MAX_VALUE
            if (!imuStable) {
                inImuRecovery = false
                lastUpdateNanos = timestampNanos
                estimate(null)
            } else if (gpsAge < 1_500_000_000L || filteredSpeedMps == null) {
                lastUpdateNanos = timestampNanos
                estimate(null)
            } else {
                if (!inImuRecovery) {
                    imuSpeedMps = filteredSpeedMps
                    inImuRecovery = true
                    lastUpdateNanos = timestampNanos
                } else {
                    val dt = ((timestampNanos - (lastUpdateNanos ?: timestampNanos)).coerceAtLeast(0L)) / 1e9
                    val acceleration = imuAccelerationMps2?.takeIf { it.isFinite() && abs(it) <= 8.0 } ?: 0.0
                    imuSpeedMps = (imuSpeedMps!! + acceleration.takeIf { abs(it) >= 0.08 }?.times(dt).orZero()).coerceAtLeast(0.0)
                    lastUpdateNanos = timestampNanos
                }
                if (gpsAge > 61_500_000_000L) SpeedGraphEstimate(null, null) else estimate(imuSpeedMps)
            }
        }
        return result
    }

    private fun estimate(speedMps: Double?): SpeedGraphEstimate = SpeedGraphEstimate(speedMps, speedMps?.let { quantize(it * 3.6) / 3.6 })

    private fun quantize(filteredKmh: Double): Int {
        val current = quantizedKmh ?: return round(filteredKmh).toInt().also { quantizedKmh = it }
        quantizedKmh = when {
            filteredKmh >= current + 0.8 -> current + 1
            filteredKmh <= current - 0.8 -> current - 1
            else -> current
        }
        return quantizedKmh!!
    }

    private fun adaptiveAlpha(acceleration: Double?): Double = when {
        acceleration == null -> 0.25
        abs(acceleration) <= 0.15 -> 0.15
        abs(acceleration) >= 0.50 -> 0.55
        else -> 0.15 + (abs(acceleration) - 0.15) / 0.35 * 0.40
    }

    private fun Double?.orZero() = this ?: 0.0
}
