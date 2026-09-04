package jp.usuiensan.taxijokemeter.data.receipt

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptTextRenderer
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val JST = ZoneId.of("Asia/Tokyo")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(JST)

object SafetyReportPdfGenerator {
    fun write(data: SafetyReportData, file: File) {
        val pdf = PdfDocument()
        try {
        page(pdf, 842, 595) { canvas, paint ->
            paint.textSize = 22f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("安全運転レポート", 28f, 35f, paint)
            paint.textSize = 10f; paint.typeface = Typeface.DEFAULT
            canvas.drawText("日時 ${DATE_TIME.format(data.generatedAt)}   レポートNo. ${data.dutySessionId}", 28f, 53f, paint)
            canvas.drawText("走行距離 ${"%.3f".format(data.distanceKm())}km   営業件数 ${data.trips.size}件", 28f, 75f, paint)
            drawEvaluation(canvas, paint, data, data.occupied, "実車", 28f)
            drawEvaluation(canvas, paint, data, data.empty, "空車・迎車", 300f)
            drawEvaluation(canvas, paint, data, data.overall, "総合", 572f)
            paint.textSize = 11f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("速度推移", 28f, 205f, paint)
            graph(canvas, paint, data, 28f, 215f, 786f, 120f)
            paint.typeface = Typeface.DEFAULT; paint.textSize = 9f
            canvas.drawText("イベント明細", 28f, 355f, paint)
            data.events.take(12).forEachIndexed { i, event -> canvas.drawText("${DATE_TIME.format(java.time.Instant.ofEpochMilli(event.startedAtEpochMillis ?: 0))}  ${event.type}  ${event.roadMode}  ${"%.1f".format(event.peakValue)}", 28f, 370f + i * 14f, paint) }
        }
        data.trips.chunked(35).forEachIndexed { pageIndex, trips -> page(pdf, 842, 595) { canvas, paint ->
            paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD; canvas.drawText("実車営業明細", 28f, 35f, paint)
            if (pageIndex > 0) canvas.drawText("（続き）", 170f, 35f, paint)
            paint.textSize = 9f; paint.typeface = Typeface.DEFAULT
            canvas.drawText("No. 乗車時刻  降車時刻  乗車地  降車地  人数  営業km  金額", 28f, 55f, paint)
            trips.forEachIndexed { i, trip -> val absolute = pageIndex * 35 + i; canvas.drawText("${absolute + 1}  ${DATE_TIME.format(trip.pickupAt)}  ${trip.dropoffAt?.let(DATE_TIME::format) ?: "-"}  ${trip.pickupPlace?.address ?: "位置情報なし"}  ${trip.dropoffPlace?.address ?: "位置情報なし"}  ${trip.passengerCount}  ${"%.3f".format(trip.actualDistanceMeters / 1000.0)}  ${trip.totalFareYen}円", 28f, 72f + i * 13f, paint) }
            if (pageIndex == 0) { val count = data.trips.size; val people = data.trips.sumOf(ReceiptData::passengerCount); val distance = data.trips.sumOf(ReceiptData::actualDistanceMeters) / 1000.0; val fare = data.trips.sumOf(ReceiptData::totalFareYen); canvas.drawText("実車回数 ${count}回   輸送人員 ${people}人   実車距離 ${"%.3f".format(distance)}km   運賃合計 ${fare}円", 28f, 560f, paint) }
        } }
            pdf.writeTo(file.outputStream())
        } finally { pdf.close() }
    }

    private fun drawEvaluation(canvas: Canvas, paint: Paint, data: SafetyReportData, evaluation: jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluation, title: String, x: Float) {
        paint.style = Paint.Style.STROKE; canvas.drawRect(x, 90f, x + 242f, 190f, paint); paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 13f; canvas.drawText("安全評価（$title）", x + 8, 108f, paint)
        paint.typeface = Typeface.DEFAULT; paint.textSize = 10f
        canvas.drawText("一般 ${data.maxSpeed(jp.usuiensan.taxijokemeter.domain.model.RoadMode.GENERAL)?.let { "${it}km/h" } ?: "－"}  自専道 ${data.maxSpeed(jp.usuiensan.taxijokemeter.domain.model.RoadMode.HIGHWAY1)?.let { "${it}km/h" } ?: "－"}  高速 ${data.maxSpeed(jp.usuiensan.taxijokemeter.domain.model.RoadMode.HIGHWAY2)?.let { "${it}km/h" } ?: "－"}", x + 8, 122f, paint)
        canvas.drawText("点数 ${evaluation.score?.toString() ?: "－"}   評価 ${evaluation.rank}", x + 8, 137f, paint)
        canvas.drawText("急加速 ${evaluation.input.weakAccelCount + evaluation.input.strongAccelCount}回（弱${evaluation.input.weakAccelCount}/強${evaluation.input.strongAccelCount}）", x + 8, 152f, paint)
        canvas.drawText("急減速 ${evaluation.input.weakBrakeCount + evaluation.input.strongBrakeCount}回（弱${evaluation.input.weakBrakeCount}/強${evaluation.input.strongBrakeCount}）", x + 8, 167f, paint)
        canvas.drawText("速度超過 ${evaluation.input.overspeedEventCount}回", x + 8, 182f, paint)
        canvas.drawText("超過時間 ${formatSeconds(evaluation.input.overspeedSecondsByBand.values.sum())}", x + 125f, 182f, paint)
        if (evaluation.rank == jp.usuiensan.taxijokemeter.domain.safety.SafetyRank.D || evaluation.rank == jp.usuiensan.taxijokemeter.domain.safety.SafetyRank.E) canvas.drawText("指導対象です", x + 145, 137f, paint)
    }

    private fun formatSeconds(seconds: Double): String { val total = seconds.toLong(); return "%02d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60) }

    private fun graph(canvas: Canvas, paint: Paint, data: SafetyReportData, x: Float, y: Float, width: Float, height: Float) {
        paint.style = Paint.Style.STROKE; canvas.drawRect(x, y, x + width, y + height, paint); paint.style = Paint.Style.FILL
        val valid = data.samples.filter { it.isValid && it.speedMps != null }; if (valid.size < 2) return
        val start = valid.first().timestamp.toEpochMilli().toDouble(); val end = max(start + 1, valid.last().timestamp.toEpochMilli().toDouble()); val path = android.graphics.Path()
        valid.forEachIndexed { i, sample -> val px = x + ((sample.timestamp.toEpochMilli() - start) / (end - start)).toFloat() * width; val py = y + height - (sample.speedMps!! * 3.6 / 140.0).toFloat().coerceIn(0f, 1f) * height; if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        canvas.drawPath(path, paint)
    }

    private inline fun page(pdf: PdfDocument, width: Int, height: Int, draw: (Canvas, Paint) -> Unit) { val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pdf.pages.size + 1).create()); draw(page.canvas, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }); pdf.finishPage(page) }
}

object ReceiptPdfGenerator {
    fun write(receipt: ReceiptData, file: File) {
        val pdf = PdfDocument()
        try {
        val lines = ReceiptTextRenderer.render(receipt).lines().toMutableList().apply { set(0, "走行レシート") }
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(165, max(180, 32 + lines.size * 16), 1).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 9f; typeface = Typeface.MONOSPACE }
        lines.forEachIndexed { i, line -> page.canvas.drawText(line, 8f, 18f + i * 16f, paint) }
            pdf.finishPage(page); pdf.writeTo(file.outputStream())
        } finally { pdf.close() }
    }
}
