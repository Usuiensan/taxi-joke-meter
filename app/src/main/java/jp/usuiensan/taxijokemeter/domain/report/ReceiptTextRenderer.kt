package jp.usuiensan.taxijokemeter.domain.report

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReceiptTextRenderer {
    private val date = DateTimeFormatter.ofPattern("yyyy年M月d日").withZone(ZoneId.of("Asia/Tokyo"))
    private val time = DateTimeFormatter.ofPattern("H時mm分").withZone(ZoneId.of("Asia/Tokyo"))
    private const val WIDTH = 40

    fun render(receipt: ReceiptData): String = buildString {
        appendLine(if (receipt.status.name == "VOIDED") "取消記録" else "走行・料金明細")
        appendLine("${date.format(receipt.pickupAt)}  No.${receipt.tripNumber ?: "-"}")
        appendLine("-".repeat(WIDTH))
        if (receipt.status.name == "VOIDED") {
            appendLine("実車開始 ${time.format(receipt.pickupAt)}")
            appendLine("取消     ${receipt.dropoffAt?.let(time::format) ?: "-"}")
        } else {
            appendPlace("乗車", receipt.pickupAt, receipt.pickupPlace)
            receipt.waypoints.forEachIndexed { index, place -> appendPlace("経由${index + 1}", place.recordedAt, place) }
            appendPlace("降車", receipt.dropoffAt, receipt.dropoffPlace)
        }
        appendLine("人員 ${receipt.passengerCount}人")
        appendLine("-".repeat(WIDTH))
        appendRight("実走行距離", "%.3f km".format(Locale.US, receipt.actualDistanceMeters / 1000.0))
        appendRight("課金距離", "%.3f km".format(Locale.US, receipt.chargeableDistanceMeters / 1000.0))
        appendRight("低速時間", duration(receipt.lowSpeedSeconds))
        appendLine("-".repeat(WIDTH))
        appendRight("距離時間制運賃", "${receipt.meteredFareYen}円")
        if (receipt.fareAdjustmentType != jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType.NONE) appendRight(if (receipt.fareAdjustmentType.name == "SURCHARGE") "割増${receipt.fareAdjustmentPercent}%" else "割引${receipt.fareAdjustmentPercent}%", "${receipt.adjustedMeteredFareYen}円")
        receipt.fareCharges.forEach { appendRight(it.label ?: it.type, "${it.amountYen}円") }
        appendRight("運賃料金計", "${receipt.fareTotalYen}円")
        receipt.tollCharges.forEach { appendRight(it.label ?: it.type, "${it.amountYen}円") }
        appendRight("通行料・立替", "${receipt.tollTotalYen}円")
        appendLine("-".repeat(WIDTH))
        appendRight("請求額", "${receipt.totalFareYen}円")
        receipt.fareRuleSnapshot?.let {
            appendLine("=".repeat(WIDTH))
            appendLine("適用運賃")
            appendRight("初乗", "${it.initialDistanceMeters}mまで ${it.initialFareYen}円")
            appendRight("距離加算", "${it.distanceIncrementMeters}mごと +${it.distanceIncrementFareYen}円")
            appendRight("時間加算", "${it.lowSpeedTimeIncrementSeconds}秒ごと +${it.distanceIncrementFareYen}円")
            appendRight("低速判定", "${it.lowSpeedLimitKmh}km/h以下")
        }
    }.trimEnd()

    private fun StringBuilder.appendPlace(label: String, at: Instant?, place: ReceiptPlaceData?) {
        val address = place?.address?.takeIf(String::isNotBlank) ?: place?.landmarkName?.takeIf(String::isNotBlank) ?: "-"
        appendLine("$label ${at?.let(time::format) ?: "時刻不明"}  $address")
        place?.landmarkName?.takeIf { it.isNotBlank() && it != address }?.let { appendLine("         $it") }
    }

    private fun StringBuilder.appendRight(label: String, value: String) {
        appendLine(label + " ".repeat((WIDTH - label.displayWidth() - value.displayWidth()).coerceAtLeast(1)) + value)
    }

    private fun String.displayWidth() = sumOf { if (it.code in 0x1100..0x11ff || it.code in 0x2e80..0xa4cf || it.code in 0xac00..0xd7a3 || it.code in 0xf900..0xfaff || it.code in 0xff01..0xff60 || it.code in 0xffe0..0xffe6) 2 else 1 }

    private fun duration(seconds: Long): String = buildString {
        if (seconds >= 3600) append(seconds / 3600).append("時間")
        if (seconds >= 60) append((seconds / 60) % 60).append("分")
        append(seconds % 60).append("秒")
    }
}
