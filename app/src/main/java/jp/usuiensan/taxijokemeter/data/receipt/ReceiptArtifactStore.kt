package jp.usuiensan.taxijokemeter.data.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptTextRenderer
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import java.io.File
import java.nio.charset.StandardCharsets

class ReceiptArtifactStore(private val context: Context) {
    fun writeText(receipt: ReceiptData): File = file(receipt, "txt").also { it.writeText(ReceiptTextRenderer.render(receipt), StandardCharsets.UTF_8) }
    fun writePng(receipt: ReceiptData): File = file(receipt, "png").also { ReceiptPngRenderer.render(receipt, context).compress(Bitmap.CompressFormat.PNG, 100, it.outputStream()) }
    fun writePdf(receipt: ReceiptData): File = file(receipt, "pdf").also { ReceiptPdfGenerator.write(receipt, it) }
    fun writeSafetyReportPdf(report: SafetyReportData): File = File(File(context.cacheDir, "receipts").apply(File::mkdirs), "safety_report_${report.generatedAt.atZone(java.time.ZoneId.of("Asia/Tokyo")).toLocalDate().toString().replace("-", "")}_${report.trips.firstOrNull()?.tripNumber ?: 0}.pdf").also { SafetyReportPdfGenerator.write(report, it) }
    fun writeSafetyReportPng(report: SafetyReportData): File = File(File(context.cacheDir, "receipts").apply(File::mkdirs), "safety_report_${report.generatedAt.atZone(java.time.ZoneId.of("Asia/Tokyo")).toLocalDate().toString().replace("-", "")}_${report.trips.firstOrNull()?.tripNumber ?: 0}.png").also { SafetyReportPngGenerator.write(report, it) }
    fun savePngToGallery(file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TaxiJokeMeter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            try { resolver.openOutputStream(uri).use { output -> file.inputStream().use { it.copyTo(requireNotNull(output)) } }; values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null); return uri }
            catch (e: Exception) { resolver.delete(uri, null, null); throw e }
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TaxiJokeMeter").apply(File::mkdirs)
        val target = File(dir, displayName); file.copyTo(target, overwrite = false)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
    fun shareUri(uri: Uri, mimeType: String): Intent = Intent(Intent.ACTION_SEND).apply { type = mimeType; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    fun share(file: File, mimeType: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri(file))
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
    fun render(receipt: ReceiptData, context: Context? = null): Bitmap {
        val lines = ReceiptTextRenderer.render(receipt).lines()
        val width = 384
        val lineHeight = 30
        val bitmap = Bitmap.createBitmap(width, (lines.size * lineHeight + 32).coerceAtLeast(128), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 20f; typeface = context?.let { ResourcesCompat.getFont(it, jp.usuiensan.taxijokemeter.R.font.receipt_biz_udgothic_regular) } ?: Typeface.MONOSPACE }
        lines.forEachIndexed { index, line ->
            val y = 25f + index * lineHeight
            val x = if (line.contains("円")) (width - 12f - paint.measureText(line)).coerceAtLeast(8f) else 12f
            canvas.drawText(line, x, y, paint)
        }
        return bitmap
    }
}
