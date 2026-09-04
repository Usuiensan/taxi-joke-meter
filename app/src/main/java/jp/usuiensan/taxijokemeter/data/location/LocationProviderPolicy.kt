package jp.usuiensan.taxijokemeter.data.location

internal const val GPS_FRESH_TIMEOUT_MILLIS = 5_000L
internal const val GOOD_ACCURACY_METERS = 30.0
internal const val USABLE_ACCURACY_METERS = 100.0

internal fun shouldIgnoreNetworkFix(
    gpsElapsedRealtimeNanos: Long?,
    gpsAccuracyMeters: Double?,
    nowElapsedRealtimeNanos: Long,
): Boolean = gpsElapsedRealtimeNanos != null &&
    nowElapsedRealtimeNanos - gpsElapsedRealtimeNanos < GPS_FRESH_TIMEOUT_MILLIS * 1_000_000L &&
    gpsAccuracyMeters != null && gpsAccuracyMeters <= USABLE_ACCURACY_METERS
