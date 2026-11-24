package jp.usuiensan.taxijokemeter.data.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptPlaceData
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

object SafetyReportPngGenerator {
    internal fun write(data: SafetyReportData, output: OutputStream) = ReportImage(data).write(output)
}

private const val WIDTH = 1240
private const val LEFT = 48f
private const val RIGHT = WIDTH - LEFT
private const val GRAY = 0xff84919e.toInt()
private const val LINE = 0xffc8c8cb.toInt()
private const val WHITE = 0xffffffff.toInt()
private const val BLACK = 0xff000000.toInt()
private const val BLUE = 0xff005aff.toInt()
private const val PALE_BLUE = 0xffbfe4ff.toInt()
private const val DETAIL_WIDTH = 560
private const val DETAIL_LIMIT = 14

private val JST = ZoneId.of("Asia/Tokyo")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(JST)
private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST)
private val MINUTE = DateTimeFormatter.ofPattern("HH:mm").withZone(JST)
private val GENERAL_LIMIT_DASH = DashPathEffect(floatArrayOf(6f, 6f), 0f)
private val HIGHWAY_LIMIT_DASH = DashPathEffect(floatArrayOf(16f, 6f, 4f, 6f), 0f)
private val ACCELERATION_DOTS = DashPathEffect(floatArrayOf(2f, 8f), 0f)
private fun yen(amount: Long) = "%,d 円".format(Locale.JAPAN, amount)

private enum class Warning(val type: SafetyEventType, val label: String, val color: Int, val unit: String) {
    ACCELERATION(SafetyEventType.HARD_ACCELERATION, "急加速", 0xff03af7a.toInt(), "m/s²"),
    DECELERATION(SafetyEventType.HARD_DECELERATION, "急減速", 0xffff4b00.toInt(), "m/s²"),
    OVERSPEED(SafetyEventType.OVERSPEED, "速度超過", 0xfff6aa00.toInt(), "km/h 超過"),
}

private class ReportImage(private val data: SafetyReportData) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var canvas: Canvas
    private val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 20f }
    private val start = data.samples.firstOrNull()?.timestamp?.toEpochMilli() ?: 0L
    private val end = data.samples.lastOrNull()?.timestamp?.toEpochMilli() ?: start
    private val detailTop = if (end > start) 945f else 564f

    private val eventRows = data.events.take(DETAIL_LIMIT).map { event ->
        val warning = Warning.entries.first { it.type == event.type }
        val range = listOf(event.startedAtEpochMillis, event.endedAtEpochMillis)
            .joinToString(" – ") { it?.let { millis -> TIME.format(Instant.ofEpochMilli(millis)) } ?: "—" }
        detailLayout("%s   %s   %.1f %s".format(warning.label, range, event.peakValue, warning.unit)) to warning.color
    }
    private val tripRows = data.trips.take(DETAIL_LIMIT).map { trip ->
        val times = MINUTE.format(trip.pickupAt) + " – " + (trip.dropoffAt?.let(MINUTE::format) ?: "—")
        detailLayout("No.%s   %s    %d 人    %s\n乗車  %s\n降車  %s".format(
            trip.tripNumber ?: "—", times, trip.passengerCount, yen(trip.totalFareYen),
            place(trip.pickupPlace), place(trip.dropoffPlace),
        )) to null
    }

    fun write(output: OutputStream) {
        val details = maxOf(detailHeight(eventRows, data.events.size), detailHeight(tripRows, data.trips.size))
        val bitmap = Bitmap.createBitmap(WIDTH, detailTop.toInt() + details + 40, Bitmap.Config.ARGB_8888)
        try {
            canvas = Canvas(bitmap).apply { drawColor(WHITE) }
            drawSummary()
            drawGraph()
            drawDetails(LEFT, "安全イベント明細", eventRows, data.events.size)
            drawDetails(RIGHT - DETAIL_WIDTH, "実車営業明細", tripRows, data.trips.size)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawSummary() {
        rect(LEFT, 34f, RIGHT, 40f, BLUE)
        text("安全運転レポート", LEFT, 94f, 42f, bold = true)
        text("作成  " + DATE_TIME.format(data.generatedAt), RIGHT, 89f, 21f, align = Paint.Align.RIGHT)
        val metrics = listOf(
            "総走行距離" to "%.3f km".format(data.distanceKm()),
            "営業件数" to "%d 件".format(data.trips.size),
            "輸送人員" to "%d 人".format(data.trips.sumOf(ReceiptData::passengerCount)),
            "運賃合計" to yen(data.trips.sumOf(ReceiptData::totalFareYen)),
        )
        val width = (RIGHT - LEFT) / metrics.size
        metrics.forEachIndexed { index, (label, value) ->
            val x = LEFT + width * index
            rect(x, 122f, x + width - 2f, 210f, PALE_BLUE)
            text(label, x + 16f, 151f, 21f)
            text(value, x + 16f, 190f, 31f, bold = true)
        }

        text("安全評価・道路区分別最高速度", LEFT, 255f, 28f, bold = true)
        text("回数は評価に用いる集計値", RIGHT, 255f, 18f, align = Paint.Align.RIGHT)
        val columns = listOf("区分", "評価 / 点数", "急加速", "急減速", "速度超過", "一般道", "高速道路")
        val positions = listOf(64f, 242f, 486f, 617f, 748f, 879f, 1034f)
        rect(LEFT, 273f, RIGHT, 311f, BLACK)
        columns.forEachIndexed { i, column -> text(column, positions[i], 300f, 20f, color = WHITE) }
        listOf(
            Triple("総合", data.overall, null),
            Triple("実車", data.occupied, OccupancyState.OCCUPIED),
            Triple("空車など", data.empty, OccupancyState.EMPTY),
        ).forEachIndexed { i, (label, evaluation, occupancy) ->
            val top = 311f + i * 42f
            val input = evaluation.input
            val values = listOf(
                label, evaluation.score?.let { "%s / %.1f 点".format(evaluation.rank.name, it) } ?: "データ不足",
                "%d 回".format(input.weakAccelCount + input.strongAccelCount),
                "%d 回".format(input.weakBrakeCount + input.strongBrakeCount),
                "%d 回".format(input.overspeedEventCount),
                data.maxSpeed(RoadMode.GENERAL, occupancy)?.let { "$it km/h" } ?: "—",
                data.maxSpeed(RoadMode.HIGHWAY2, occupancy)?.let { "$it km/h" } ?: "—",
            )
            if (i == 0) rect(LEFT, top, RIGHT, top + 42f, PALE_BLUE)
            values.forEachIndexed { column, value -> text(value, positions[column], top + 28f, 21f, bold = i == 0) }
            rect(LEFT, top + 41f, RIGHT, top + 42f, LINE)
        }
    }

    private fun drawGraph() {
        val left = 160f
        val right = RIGHT - 16f
        val top = 552f
        val bottom = 782f
        text("速度・警告の推移", LEFT, 490f, 28f, bold = true)
        if (end <= start) {
            text("速度を表示できる計測データがありません", LEFT, 537f, 22f)
            return
        }

        val formatter = if (end - start < 300_000L) TIME else MINUTE
        fun x(millis: Long) = left + ((millis - start).toDouble() / (end - start)).toFloat() * (right - left)
        val maxSpeed = data.samples.maxOf { (it.graphSpeedMps?.takeIf(Double::isFinite) ?: 0.0) * 3.6 }
        val topSpeed = maxOf(120, ceil(maxSpeed / 30.0).toInt() * 30)
        fun y(kmh: Double) = bottom - (kmh / topSpeed).toFloat().coerceIn(0f, 1f) * (bottom - top)

        text(DATE_TIME.format(Instant.ofEpochMilli(start)) + " – " + DATE_TIME.format(Instant.ofEpochMilli(end)),
            RIGHT, 489f, 20f, align = Paint.Align.RIGHT)
        text("km/h", LEFT, 530f, 18f)
        rect(left, 521f, left + 42f, 526f, BLUE)
        text("速度", left + 50f, 530f, 19f)
        referenceLine(left + 116f, 523f, left + 158f, GENERAL_LIMIT_DASH)
        text("一般道 60", left + 166f, 530f, 19f)
        referenceLine(left + 284f, 523f, left + 326f, HIGHWAY_LIMIT_DASH)
        text("高速道路 90", left + 334f, 530f, 19f)

        val gridStep = if (topSpeed <= 180) 30 else 60
        ((0..topSpeed step gridStep).toList() + listOf(90, topSpeed)).distinct().sorted().forEach { kmh ->
            val py = y(kmh.toDouble())
            when (kmh) {
                60 -> referenceLine(left, py, right, GENERAL_LIMIT_DASH)
                90 -> referenceLine(left, py, right, HIGHWAY_LIMIT_DASH)
                else -> rect(left, py, right, py + 1f, LINE)
            }
            text("$kmh", left - 14f, py + 7f, 18f, align = Paint.Align.RIGHT)
        }
        for (i in 0..5) {
            val millis = start + (end - start) * i / 5
            val px = x(millis)
            rect(px, top, px + 1f, bottom, LINE)
            text(formatter.format(Instant.ofEpochMilli(millis)), px, bottom + 26f, 18f, align = when (i) {
                0 -> Paint.Align.LEFT
                5 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            })
        }

        var connected = false
        val path = Path()
        data.samples.forEach { sample ->
            val speed = sample.graphSpeedMps?.takeIf(Double::isFinite)
            if (speed == null) {
                connected = false
            } else {
                val px = x(sample.timestamp.toEpochMilli())
                val py = y(speed * 3.6)
                if (connected) path.lineTo(px, py) else path.moveTo(px, py)
                connected = true
            }
        }
        paint.color = BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        if (path.isEmpty) text("速度データなし", (left + right) / 2, (top + bottom) / 2, 22f, align = Paint.Align.CENTER)

        Warning.entries.forEachIndexed { i, warning ->
            val row = 822f + i * 32f
            warningBand(warning, LEFT, LEFT + 18f, row)
            text(warning.label, LEFT + 25f, row + 19f, 18f)
            rect(left, row, right, row + 22f, GRAY)
            rect(left + 1f, row + 1f, right - 1f, row + 21f, WHITE)
            data.events.filter { it.type == warning.type }.forEach { event ->
                val from = maxOf(start, event.startedAtEpochMillis ?: return@forEach)
                val to = minOf(end, event.endedAtEpochMillis ?: return@forEach)
                if (to > from) {
                    val center = (x(from) + x(to)) / 2
                    warningBand(warning, minOf(x(from), center - 1f).coerceAtLeast(left),
                        maxOf(x(to), center + 1f).coerceAtMost(right), row)
                }
            }
        }
    }

    private fun warningBand(warning: Warning, left: Float, right: Float, top: Float) {
        val bottom = top + 22f
        val mid = top + 11f
        rect(left, top, right, bottom, warning.color)
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        paint.color = BLACK
        when (warning) {
            Warning.ACCELERATION -> {
                paint.strokeWidth = 3f
                paint.strokeCap = Paint.Cap.ROUND
                paint.pathEffect = ACCELERATION_DOTS
                canvas.drawLine(left, mid, right, mid, paint)
                paint.pathEffect = null
                paint.strokeCap = Paint.Cap.BUTT
            }
            Warning.DECELERATION -> {
                paint.strokeWidth = 5f
                canvas.drawLine(left, mid, right, mid, paint)
            }
            Warning.OVERSPEED -> {
                paint.strokeWidth = 3f
                var x = left - 12f
                while (x < right) {
                    canvas.drawLine(x, bottom, x + 22f, top, paint)
                    x += 12f
                }
            }
        }
        canvas.restore()
    }

    private fun referenceLine(left: Float, y: Float, right: Float, dash: DashPathEffect) {
        paint.color = GRAY
        paint.strokeWidth = 2.5f
        paint.pathEffect = dash
        canvas.drawLine(left, y, right, y, paint)
        paint.pathEffect = null
    }

    private fun drawDetails(x: Float, title: String, rows: List<Pair<StaticLayout, Int?>>, total: Int) {
        val right = x + DETAIL_WIDTH
        rect(x, detailTop, right, detailTop + 43f, BLACK)
        text(title, x + 14f, detailTop + 30f, 23f, bold = true, color = WHITE)
        text("$total 件", right - 14f, detailTop + 30f, 21f, color = WHITE, align = Paint.Align.RIGHT)
        var top = detailTop + 43f
        rows.forEach { (layout, color) ->
            if (color != null) rect(x, top + 1f, x + 4f, top + layout.height + 17f, color)
            canvas.save()
            canvas.translate(x + 14f, top + 8f)
            layout.draw(canvas)
            canvas.restore()
            top += layout.height + 18f
            rect(x, top - 1f, right, top, LINE)
        }
        if (rows.isEmpty()) text("該当なし", x + 14f, top + 30f, 20f)
        if (total > rows.size) text("ほか %d 件".format(total - rows.size), x + 14f, top + 30f, 20f)
    }

    private fun detailHeight(rows: List<Pair<StaticLayout, Int?>>, total: Int) =
        43 + rows.sumOf { (layout, _) -> layout.height + 18 } + (if (rows.isEmpty() || total > rows.size) 38 else 0)

    private fun detailLayout(value: String): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, detailPaint, DETAIL_WIDTH - 28).build()

    private fun place(place: ReceiptPlaceData?) =
        place?.address?.takeIf(String::isNotBlank) ?: place?.landmarkName?.takeIf(String::isNotBlank) ?: "位置情報なし"

    private fun rect(x: Float, y: Float, right: Float, bottom: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y, right, bottom, paint)
    }

    private fun text(
        value: String, x: Float, y: Float, size: Float, bold: Boolean = false,
        color: Int = BLACK, align: Paint.Align = Paint.Align.LEFT,
    ) {
        paint.color = color
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = size
        paint.textAlign = align
        canvas.drawText(value, x, y, paint)
    }
}
