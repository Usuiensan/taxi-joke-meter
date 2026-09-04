package jp.usuiensan.taxijokemeter.data.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptTextRenderer
import java.io.File
import java.nio.charset.StandardCharsets

class ReceiptArtifactStore(private val context: Context) {
    fun writeText(receipt: ReceiptData): File = file(receipt, "txt").also { it.writeText(ReceiptTextRenderer.render(receipt), StandardCharsets.UTF_8) }
    fun writePng(receipt: ReceiptData): File = file(receipt, "png").also { ReceiptPngRenderer.render(receipt).compress(Bitmap.CompressFormat.PNG, 100, it.outputStream()) }
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
    fun render(receipt: ReceiptData): Bitmap {
        val lines = ReceiptTextRenderer.render(receipt).lines()
        val width = 384
        val lineHeight = 30
        val bitmap = Bitmap.createBitmap(width, (lines.size * lineHeight + 32).coerceAtLeast(128), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        lines.forEachIndexed { index, line ->
            val y = 25f + index * lineHeight
            val x = if (line.contains("円")) (width - 12f - paint.measureText(line)).coerceAtLeast(8f) else 12f
            canvas.drawText(line, x, y, paint)
        }
        return bitmap
    }
}
