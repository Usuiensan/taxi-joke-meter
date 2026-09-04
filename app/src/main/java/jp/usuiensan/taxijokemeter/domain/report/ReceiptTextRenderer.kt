package jp.usuiensan.taxijokemeter.domain.report

import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReceiptTextRenderer {
    private val date = DateTimeFormatter.ofPattern("yyyy年MM月dd日").withZone(ZoneId.of("Asia/Tokyo"))
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Tokyo"))

    fun render(receipt: ReceiptData): String = buildString {
        if (receipt.status.name == "VOIDED") appendLine("取消記録") else appendLine("走行・料金明細")
        appendLine("${receipt.serviceDate ?: date.format(receipt.pickupAt)} No.${receipt.tripNumber ?: "-"}")
        if (receipt.status.name == "VOIDED") appendLine("実車開始 ${time.format(receipt.pickupAt)}\n取消      ${receipt.dropoffAt?.let(time::format) ?: "-"}")
        else appendPlace("乗車", receipt.pickupAt, receipt.pickupPlace)
        receipt.waypoints.forEach { appendPlace("経由", it.recordedAt, it) }
        if (receipt.status.name != "VOIDED") appendPlace("降車", receipt.dropoffAt, receipt.dropoffPlace)
        appendLine("人員 ${receipt.passengerCount}人")
        appendLine("実走行距離 ${"%.2f".format(receipt.actualDistanceMeters / 1000.0)}km")
        appendLine("課金距離   ${"%.2f".format(receipt.chargeableDistanceMeters / 1000.0)}km")
        appendLine("低速時間   ${receipt.lowSpeedSeconds / 60}分${receipt.lowSpeedSeconds % 60}秒")
        appendLine("距離時間制運賃  ${receipt.meteredFareYen}円")
        if (receipt.fareAdjustmentType != jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType.NONE) {
            appendLine("${if (receipt.fareAdjustmentType.name == "SURCHARGE") "割増" else "割引"} ${receipt.fareAdjustmentPercent}%")
            appendLine("調整後運賃      ${receipt.adjustedMeteredFareYen}円")
        }
        receipt.fareCharges.forEach { appendLine("${it.label ?: it.type}        ${it.amountYen}円") }
        appendLine("運賃料金計      ${receipt.fareTotalYen}円")
        appendLine("通行料・立替     ${receipt.tollTotalYen}円")
        appendLine("請求額          ${receipt.totalFareYen}円")
        receipt.fareRuleSnapshot?.let {
            appendLine("適用運賃：")
            appendLine("初乗 ${it.initialDistanceMeters}mまで${it.initialFareYen}円")
            appendLine("以後 ${it.distanceIncrementMeters}mごと+${it.distanceIncrementFareYen}円")
            appendLine("低速 ${it.lowSpeedTimeIncrementSeconds}秒ごと+${it.distanceIncrementFareYen}円")
            appendLine("低速判定 ${it.lowSpeedLimitKmh}km/h以下")
        }
    }.trimEnd()

    private fun StringBuilder.appendPlace(label: String, at: java.time.Instant?, place: ReceiptPlaceData?) {
        appendLine("$label ${at?.let(time::format) ?: "-"}")
        appendLine(place?.address ?: "住所なし")
        place?.landmarkName?.takeIf { it != place.address }?.let { appendLine("（$it）") }
    }
}
