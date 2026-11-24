package jp.usuiensan.taxijokemeter.data.receipt

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptPlaceData
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvent
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventSource
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportPdfGeneratorTest {
    private val at = Instant.parse("2026-09-04T04:00:00Z")
    private val receipt = ReceiptData("trip", "2026-09-04", 1, TripStatus.COMPLETED, at, null,
        emptyList(), at.plusSeconds(60), null, 1, 0, 0, 0, 10, FareAdjustmentType.NONE, 0,
        10, emptyList(), emptyList(), 10, 0, 10, null)

    @Test fun safetyPngKeepsA4PortraitAndABottomMarginWithLongDetails() {
        val place = ReceiptPlaceData("pickup", 0, at, "京都府城陽市寺田正道５４－１０".repeat(10), null)
        val trip = receipt.copy(pickupPlace = place, dropoffPlace = place)
        for (trips in listOf(emptyList(), List(14) { trip.copy(tripNumber = it + 1) })) {
            val report = SafetyReportData.from(at, emptyList(), emptyList(), trips)
            val png = ByteArrayOutputStream().apply { SafetyReportPngGenerator.write(report, this) }.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
            try {
                assertEquals(1240, bitmap.width)
                assertEquals(1754, bitmap.height)
                val margin = IntArray(bitmap.width * 40)
                bitmap.getPixels(margin, 0, bitmap.width, 0, bitmap.height - 40, bitmap.width, 40)
                assertTrue(margin.all { it == Color.WHITE })
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun receiptPngWrapsLongAddressesAndKeepsTheRightMarginClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val short = ReceiptPngRenderer.render(receipt, context)
        val long = ReceiptPngRenderer.render(receipt.copy(pickupPlace = ReceiptPlaceData(
            "pickup", 0, at, "京都府城陽市寺田正道５４－１０".repeat(5), null,
        )), context)
        try {
            assertEquals(short.width, long.width)
            assertTrue(long.height > short.height)
            for (y in 0 until long.height) {
                assertEquals(Color.WHITE, long.getPixel(long.width - 1, y))
            }
        } finally {
            short.recycle()
            long.recycle()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun safetyShareUsesThePublishedPhotoWithColoredWarningIntervals() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val start = at.toEpochMilli()
        val samples = (0L..100L step 10).map { second ->
            DrivingSample("duty", at.plusSeconds(second), 35.0, 135.0 + second / 100_000.0, 5.0, 10 + second % 40 / 2.0, null,
                RoadMode.GENERAL, OccupancyState.OCCUPIED, MeasurementQuality.VALID)
        }
        val warning = SafetyEvent(0, SafetyEventType.HARD_DECELERATION, 20_000, 60_000,
            start + 20_000, start + 60_000, 3.5, RoadMode.GENERAL, SafetyEventSource.IMU)
        val acceleration = warning.copy(type = SafetyEventType.HARD_ACCELERATION, peakValue = 2.8)
        val overspeed = warning.copy(type = SafetyEventType.OVERSPEED, startedAtEpochMillis = start + 40_000,
            endedAtEpochMillis = start + 80_000, peakValue = 10.0, source = SafetyEventSource.GPS)
        val place = ReceiptPlaceData("pickup", 0, at, "京都府城陽市寺田正道５４－１０  交差点東側の停車スペース", null)
        val report = SafetyReportData.from(at.plusSeconds(100), samples, listOf(acceleration, warning, overspeed),
            listOf(receipt.copy(pickupPlace = place, dropoffPlace = place)))
        val intent = ReceiptArtifactStore(context).shareSafetyReport(report)
        val uri = checkNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        val resolver = context.contentResolver
        try {
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val columns = arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING)
            checkNotNull(resolver.query(uri, columns, null, null, null)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pictures/TaxiJokeMeter/", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            val bitmap = checkNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) })
            try {
                val red = 0xffff4b00.toInt()
                val orange = 0xfff6aa00.toInt()
                val green = 0xff03af7a.toInt()
                val accelRows = (0 until bitmap.height).filter { y ->
                    bitmap.getPixel(400, y) == green && bitmap.getPixel(650, y) == green
                }
                val brakeRows = (0 until bitmap.height).filter { y ->
                    bitmap.getPixel(400, y) == red && bitmap.getPixel(650, y) == red
                }
                val speedRows = (0 until bitmap.height).filter { y ->
                    bitmap.getPixel(650, y) == orange && bitmap.getPixel(850, y) == orange
                }
                assertTrue(accelRows.isNotEmpty() && brakeRows.isNotEmpty() && speedRows.isNotEmpty())
                assertTrue(accelRows.last() < brakeRows.first())
                assertTrue(brakeRows.last() < speedRows.first())
                brakeRows.forEach { y ->
                    assertTrue(bitmap.getPixel(250, y) != red)
                    assertTrue(bitmap.getPixel(850, y) != red)
                }
                speedRows.forEach { y ->
                    assertTrue(bitmap.getPixel(400, y) != orange)
                    assertTrue(bitmap.getPixel(1050, y) != orange)
                }
                // Black marks have the same contrast after grayscale conversion.
                fun ink(xs: IntRange, y: Int) = xs.count { x -> bitmap.getPixel(x, y) == Color.BLACK }
                val accelCenter = (accelRows.first() + accelRows.last()) / 2
                val brakeCenter = (brakeRows.first() + brakeRows.last()) / 2
                val speedCenter = (speedRows.first() + speedRows.last()) / 2
                assertTrue(ink(450..540, accelCenter) in 1..90)
                assertEquals(0, ink(450..540, accelCenter + 6))
                assertEquals(91, ink(450..540, brakeCenter))
                assertTrue(ink(650..740, speedCenter) in 1..90 && ink(650..740, speedCenter + 6) in 1..90)
            } finally {
                bitmap.recycle()
            }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test fun emptyReportAndReceiptProduceReadablePdfSizes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = SafetyReportData.from(Instant.parse("2026-09-04T04:02:00Z"), emptyList(), emptyList(), listOf(receipt))
        val dir = context.getExternalFilesDir(null)!!
        val reportFile = File(dir, "report-test.pdf"); val receiptFile = File(dir, "receipt-test.pdf")
        SafetyReportPdfGenerator.write(report, reportFile); ReceiptPdfGenerator.write(receipt, receiptFile)
        assertTrue(reportFile.length() > 0); assertTrue(receiptFile.length() > 0)
        PdfRenderer(ParcelFileDescriptor.open(reportFile, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> assertEquals(1, renderer.pageCount); renderer.openPage(0).use { assertTrue(it.width < it.height) } }
        PdfRenderer(ParcelFileDescriptor.open(receiptFile, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> assertEquals(1, renderer.pageCount); renderer.openPage(0).use { assertTrue(it.width < it.height) } }
    }

    @Test fun receiptPngFontLoadsFromBundledBIZUdGothicTtf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(androidx.core.content.res.ResourcesCompat.getFont(context, jp.usuiensan.taxijokemeter.R.font.receipt_biz_udgothic_regular) != null)
    }
}
