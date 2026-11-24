package jp.usuiensan.taxijokemeter.data.receipt

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptTextRenderer
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

class ReceiptArtifactStore(private val context: Context) {
    fun writeText(receipt: ReceiptData): File = file(receipt, "txt").also { it.writeText(ReceiptTextRenderer.render(receipt), StandardCharsets.UTF_8) }
    fun writePng(receipt: ReceiptData): File = file(receipt, "png").also { target ->
        target.outputStream().use { ReceiptPngRenderer.write(receipt, context, it) }
    }
    fun writePdf(receipt: ReceiptData): File = file(receipt, "pdf").also { ReceiptPdfGenerator.write(receipt, it) }
    fun writeSafetyReportPdf(report: SafetyReportData): File = File(File(context.cacheDir, "receipts").apply(File::mkdirs), "safety_report_${report.generatedAt.atZone(java.time.ZoneId.of("Asia/Tokyo")).toLocalDate().toString().replace("-", "")}_${report.trips.firstOrNull()?.tripNumber ?: 0}.pdf").also { SafetyReportPdfGenerator.write(report, it) }
    fun saveReceiptPng(receipt: ReceiptData, displayName: String): Uri =
        savePngToGallery(displayName) { ReceiptPngRenderer.write(receipt, context, it) }

    fun saveSafetyReportPngs(report: SafetyReportData): ArrayList<Uri> {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.of("Asia/Tokyo"))
            .format(report.generatedAt)
        val pages = arrayListOf<Uri>()
        try {
            SafetyReportPngGenerator.renderPages(report) { page, bitmap ->
                val number = page.toString().padStart(2, '0')
                pages += savePngToGallery("safety_report_${stamp}_p$number.png") { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            return pages
        } catch (e: Exception) {
            pages.forEach { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }.onFailure(e::addSuppressed)
            }
            throw e
        }
    }

    private fun savePngToGallery(displayName: String, write: (OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TaxiJokeMeter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = checkNotNull(resolver.insert(collection, values))
            try {
                checkNotNull(resolver.openOutputStream(uri)).use(write)
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                check(resolver.update(uri, published, null, null) == 1)
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }.onFailure(e::addSuppressed)
                throw e
            }
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TaxiJokeMeter")
        dir.mkdirs()
        val target = File(dir, displayName)
        check(target.createNewFile())
        try {
            target.outputStream().use(write)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("image/png"), null)
        return uri(target)
    }
    fun shareSafetyReport(report: SafetyReportData): Intent {
        val pages = saveSafetyReportPngs(report)
        if (pages.size == 1) return shareUri(pages.single(), "image/png")
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, pages)
            clipData = ClipData("安全運転レポート", arrayOf("image/png"), ClipData.Item(pages.first())).apply {
                pages.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun share(file: File, mimeType: String): Intent = shareUri(uri(file), mimeType)

    private fun shareUri(uri: Uri, mimeType: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun view(file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri(file), "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    private fun file(receipt: ReceiptData, extension: String): File {
        val dir = File(context.cacheDir, "receipts").apply(File::mkdirs)
        return File(dir, "receipt_${receipt.serviceDate?.replace("-", "") ?: "unknown"}_${(receipt.tripNumber ?: 0).toString().padStart(3, '0')}.$extension")
    }
}

object ReceiptPngRenderer {
    fun write(receipt: ReceiptData, context: Context, output: OutputStream) {
        val bitmap = render(receipt, context)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
    }

    fun render(receipt: ReceiptData, context: Context? = null): Bitmap {
        val font = context?.let { ResourcesCompat.getFont(it, jp.usuiensan.taxijokemeter.R.font.receipt_biz_udgothic_regular) }
        val layout = receiptLayout(receipt, 20f, font ?: Typeface.MONOSPACE)
        val bitmap = Bitmap.createBitmap(layout.width + 24, layout.height + 24, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE); translate(12f, 12f) }
        layout.draw(canvas)
        return bitmap
    }
}

internal fun receiptLayout(receipt: ReceiptData, size: Float, font: Typeface): StaticLayout {
    val text = ReceiptTextRenderer.render(receipt)
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = size; typeface = font }
    val divider = text.lineSequence().first { it.startsWith("-----") }
    val width = ceil(paint.measureText(divider) + size).toInt()
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setLineSpacing(size / 5, 1f)
        .build()
}
