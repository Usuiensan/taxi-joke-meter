package jp.usuiensan.taxijokemeter.data.receipt

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.model.PaymentMethod
import jp.usuiensan.taxijokemeter.domain.model.TripStatus
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvaluator
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportPdfGeneratorTest {
    @Test fun emptyReportAndReceiptProduceReadablePdfSizes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receipt = ReceiptData("trip", "2026-09-04", 1, TripStatus.COMPLETED, Instant.parse("2026-09-04T04:00:00Z"), null, emptyList(), Instant.parse("2026-09-04T04:01:00Z"), null, 1, 0, 0, 0, 10, FareAdjustmentType.NONE, 0, 10, emptyList(), emptyList(), 10, 0, 10, null)
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
