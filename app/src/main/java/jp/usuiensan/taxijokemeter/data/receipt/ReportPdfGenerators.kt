package jp.usuiensan.taxijokemeter.data.receipt

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluation
import jp.usuiensan.taxijokemeter.domain.safety.SafetyRank
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val JST = ZoneId.of("Asia/Tokyo")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 H時mm分").withZone(JST)
private const val PAGE_W = 595f
private const val PAGE_H = 842f
private const val MARGIN = 28f

object SafetyReportPdfGenerator {
    fun write(data: SafetyReportData, file: File) {
        val pdf = PdfDocument()
        try {
            val report = ReportCanvas(pdf, data)
            report.section("安全運転レポート")
            report.twoColumn(listOf("レポートNo." to data.dutySessionId.take(16), "日時" to DATE_TIME.format(data.generatedAt), "総走行距離" to "%.3f km".format(data.distanceKm()), "営業件数" to "${data.trips.size}件", "輸送人員" to "${data.trips.sumOf(ReceiptData::passengerCount)}人", "運賃合計" to "${data.trips.sumOf(ReceiptData::totalFareYen)}円"))
            report.section("安全評価")
            report.table(listOf("項目", "実車", "空車・迎車", "総合"), listOf(
                listOf("点数", score(data.occupied), score(data.empty), score(data.overall)),
                listOf("評価", rank(data.occupied), rank(data.empty), rank(data.overall)),
                listOf("急加速", operation(data.occupied, true), operation(data.empty, true), operation(data.overall, true)),
                listOf("急減速", operation(data.occupied, false), operation(data.empty, false), operation(data.overall, false)),
                listOf("速度超過", "${data.occupied.input.overspeedEventCount}回", "${data.empty.input.overspeedEventCount}回", "${data.overall.input.overspeedEventCount}回"),
                listOf("超過時間", seconds(data.occupied), seconds(data.empty), seconds(data.overall)),
            ), floatArrayOf(100f, 145f, 145f, 145f))
            report.section("道路区分別最高速度")
            report.table(listOf("区分", "一般", "高速"), listOf(
                listOf("実車", speed(data, RoadMode.GENERAL, true), speed(data, RoadMode.HIGHWAY2, true)),
                listOf("空車・迎車", speed(data, RoadMode.GENERAL, false), speed(data, RoadMode.HIGHWAY2, false)),
            ), floatArrayOf(130f, 195f, 210f))
            report.section("速度推移")
            if (data.samples.count { it.graphSpeedMps != null } < 2) report.line("データなし") else report.graph(data)
            report.section("安全イベント明細")
            if (data.events.isEmpty()) report.line("該当なし") else report.table(listOf("No.", "時刻", "種別", "値"), data.events.mapIndexed { i, e -> listOf("${i + 1}", e.startedAtEpochMillis?.let { DATE_TIME.format(java.time.Instant.ofEpochMilli(it)) } ?: "-", eventName(e.type.name), "%.1f".format(e.peakValue)) }, floatArrayOf(40f, 160f, 230f, 105f))
            report.section("実車営業明細")
            if (data.trips.isEmpty()) report.line("該当なし") else { val widths = floatArrayOf(24f, 42f, 42f, 130f, 130f, 28f, 55f, 88f); report.tableRow(listOf("No.", "乗車", "降車", "乗車地", "降車地", "人", "実車km", "運賃"), widths, true); data.trips.forEachIndexed { i, trip -> report.tableRow(listOf("${i + 1}", shortTime(trip.pickupAt), shortTime(trip.dropoffAt), place(trip.pickupPlace?.address), place(trip.dropoffPlace?.address), "${trip.passengerCount}", "%.3f".format(trip.actualDistanceMeters / 1000.0), "${trip.totalFareYen}円"), widths, false, true) } }
            report.line("集計  実車${data.trips.size}回  人員${data.trips.sumOf(ReceiptData::passengerCount)}人  実車%.3fkm  ${data.trips.sumOf(ReceiptData::totalFareYen)}円".format(data.trips.sumOf(ReceiptData::actualDistanceMeters) / 1000.0))
            report.finish()
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
    }

    private fun score(e: SafetyEvaluation) = e.score?.let { "%.1f".format(it) } ?: "－"
    private fun rank(e: SafetyEvaluation) = when (e.rank) { SafetyRank.INSUFFICIENT_DATA -> "データ不足"; else -> e.rank.name }
    private fun operation(e: SafetyEvaluation, accel: Boolean): String { val i = e.input; return if (accel) "${i.weakAccelCount + i.strongAccelCount}回（${i.weakAccelCount}/${i.strongAccelCount}）" else "${i.weakBrakeCount + i.strongBrakeCount}回（${i.weakBrakeCount}/${i.strongBrakeCount}）" }
    private fun seconds(e: SafetyEvaluation): String = e.input.overspeedSecondsByBand.values.sum().toLong().let { total -> buildString { if (total >= 3600) append(total / 3600).append("時間"); if (total >= 60) append(total / 60 % 60).append("分"); append(total % 60).append("秒") } }
    private fun speed(data: SafetyReportData, mode: RoadMode, occupied: Boolean) = data.maxSpeed(mode, if (occupied) jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState.OCCUPIED else jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState.EMPTY)?.let { "${it}km/h" } ?: "－"
    private fun eventName(name: String) = when (name) { "HARD_ACCELERATION" -> "急加速"; "HARD_DECELERATION" -> "急減速"; "OVERSPEED" -> "速度超過"; else -> name }
    private fun shortTime(value: java.time.Instant?) = value?.let { DateTimeFormatter.ofPattern("H時mm分").withZone(JST).format(it) } ?: "-"
    private fun place(value: String?) = value?.takeIf(String::isNotBlank) ?: "位置情報なし"
}

private class ReportCanvas(private val pdf: PdfDocument, private val data: SafetyReportData) {
    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    private var canvas = Canvas()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
    private var y = MARGIN
    private fun start() { pageNo++; page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create()); canvas = page!!.canvas; y = MARGIN; paint.style = Paint.Style.FILL; paint.textSize = 8f; canvas.drawText("安全運転レポート  Page $pageNo", MARGIN, 18f, paint) }
    fun finish() { page?.let(pdf::finishPage) }
    fun section(title: String) { ensure(22f); paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 11f; canvas.drawText(title, MARGIN, y + 13f, paint); y += 18f; paint.typeface = Typeface.DEFAULT }
    fun line(text: String) { ensure(16f); paint.textSize = 8f; canvas.drawText(text.take(105), MARGIN + 3f, y + 11f, paint); y += 15f }
    fun twoColumn(rows: List<Pair<String, String>>) { rows.chunked(2).forEach { pair -> ensure(16f); val h = 16f; val w = (PAGE_W - MARGIN * 2) / 2; pair.forEachIndexed { i, (key, value) -> val x = MARGIN + i * w; cell(x, y, w, h, "$key  $value", 8f, false) }; y += h } }
    fun table(headers: List<String>, rows: List<List<String>>, widths: FloatArray) { tableRow(headers, widths, true); rows.forEach { tableRow(it, widths) } }
    fun tableRow(values: List<String>, widths: FloatArray, header: Boolean = false, wrap: Boolean = false) { val height = if (wrap) 28f else 16f; ensure(height); var x = MARGIN; values.forEachIndexed { i, value -> val w = widths.getOrElse(i) { 50f }; cell(x, y, w, height, value, if (header) 8f else 7f, header, wrap); x += w }; y += height }
    fun graph(data: SafetyReportData) { ensure(82f); val top = y; val h = 72f; paint.style = Paint.Style.STROKE; canvas.drawRect(MARGIN, top, PAGE_W - MARGIN, top + h, paint); val samples = data.samples.filter { it.graphSpeedMps != null }; val start = samples.first().timestamp.toEpochMilli().toDouble(); val end = max(start + 1, samples.last().timestamp.toEpochMilli().toDouble()); val path = Path(); samples.forEachIndexed { i, s -> val px = MARGIN + ((s.timestamp.toEpochMilli() - start) / (end - start)).toFloat() * (PAGE_W - MARGIN * 2); val py = top + h - (s.graphSpeedMps!! * 3.6 / 140.0).toFloat().coerceIn(0f, 1f) * h; if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }; canvas.drawPath(path, paint); paint.style = Paint.Style.FILL; y += h + 8f }
    private fun cell(x: Float, top: Float, width: Float, height: Float, value: String, size: Float, header: Boolean, wrap: Boolean = false) { paint.style = Paint.Style.FILL; if (header) { paint.color = 0xffeeeeee.toInt(); canvas.drawRect(x, top, x + width, top + height, paint); paint.color = android.graphics.Color.BLACK }; paint.style = Paint.Style.STROKE; canvas.drawRect(x, top, x + width, top + height, paint); paint.style = Paint.Style.FILL; paint.textSize = size; val lines = if (wrap) value.chunked(20).take(2) else listOf(value); canvas.save(); canvas.clipRect(x + 3f, top, x + width - 3f, top + height); lines.forEachIndexed { i, line -> canvas.drawText(line, x + 3f, top + 11f + i * 10f, paint) }; canvas.restore() }
    private fun ensure(height: Float) { if (page == null) start() else if (y + height > PAGE_H - MARGIN) { pdf.finishPage(page!!); start() } }
}

object ReceiptPdfGenerator {
    fun write(receipt: ReceiptData, file: File) {
        val layout = receiptLayout(receipt, 9f, Typeface.MONOSPACE)
        val width = layout.width + 16
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, max(width + 1, layout.height + 16), 1).create())
            page.canvas.translate(8f, 8f)
            layout.draw(page.canvas)
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }
}
