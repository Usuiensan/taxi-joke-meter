package jp.usuiensan.taxijokemeter.domain.report

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReceiptTextRenderer {
    private val date = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.of("Asia/Tokyo"))
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Tokyo"))
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
        appendRight("低速時間", "%d分%02d秒".format(receipt.lowSpeedSeconds / 60, receipt.lowSpeedSeconds % 60))
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
        appendLine("$label ${at?.let(time::format) ?: "--:--"}  ${address.take(WIDTH - label.length - 9)}")
        place?.landmarkName?.takeIf { it.isNotBlank() && it != place.address && address != it }?.let { appendLine("         ${it.take(WIDTH - 9)}") }
    }

    private fun StringBuilder.appendRight(label: String, value: String) {
        appendLine((label + " ".repeat((WIDTH - label.length - value.length - 1).coerceAtLeast(1)) + value).take(WIDTH))
    }
}
