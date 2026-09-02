package jp.usuiensan.taxijokemeter.data.location

import java.time.Duration

enum class LocationHealth { VALID, STALE, UNAVAILABLE }

class LocationHealthTracker(
    private val staleAfter: Duration = Duration.ofSeconds(5),
    private val unavailableAfter: Duration = Duration.ofSeconds(15),
) {
    private var lastFixElapsedRealtimeNanos: Long? = null

    init {
        require(!staleAfter.isNegative && !staleAfter.isZero)
        require(unavailableAfter > staleAfter)
    }

    fun recordFix(elapsedRealtimeNanos: Long) {
        if (elapsedRealtimeNanos >= 0) lastFixElapsedRealtimeNanos = elapsedRealtimeNanos
    }

    fun healthAt(nowElapsedRealtimeNanos: Long): LocationHealth {
        val lastFix = lastFixElapsedRealtimeNanos ?: return LocationHealth.UNAVAILABLE
        val gap = (nowElapsedRealtimeNanos - lastFix).coerceAtLeast(0L)
        return when {
            gap < staleAfter.toNanos() -> LocationHealth.VALID
            gap < unavailableAfter.toNanos() -> LocationHealth.STALE
            else -> LocationHealth.UNAVAILABLE
        }
    }
}
