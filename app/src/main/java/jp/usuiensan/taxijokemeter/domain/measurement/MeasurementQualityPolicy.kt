package jp.usuiensan.taxijokemeter.domain.measurement

import java.time.Duration

data class MeasurementQualityPolicy(
    val accuracyThresholdMeters: Double,
    val staleAfter: Duration,
)
