package com.example.lib

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareReportHelper {

    /**
     * Creates a simple text bitmap or multi-line document and shares it as an image (PNG).
     */
    fun shareTextReportAsImage(
        context: Context,
        title: String,
        lines: List<String>,
        fileNamePrefix: String = "report"
    ) {
        try {
            val width = 800
            val padding = 40
            val lineHeight = 36
            val height = padding * 2 + 60 + (lines.size * lineHeight) + 60

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 28f
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 20f
                textAlign = Paint.Align.RIGHT
            }

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 16f
                textAlign = Paint.Align.CENTER
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 2f
            }

            var y = padding + 30f
            // Right-aligned for Persian RTL
            canvas.drawText(title, width - padding.toFloat(), y, titlePaint)
            y += 20f
            canvas.drawLine(padding.toFloat(), y, width - padding.toFloat(), y, linePaint)
            y += 30f

            for (line in lines) {
                canvas.drawText(line, width - padding.toFloat(), y, textPaint)
                y += lineHeight
            }

            y += 20f
            canvas.drawLine(padding.toFloat(), y, width - padding.toFloat(), y, linePaint)
            y += 30f
            canvas.drawText("مدیریت مزرعه – تولید شده آفلاین", width / 2f, y, footerPaint)

            // Save to cache
            val file = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصویر"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates a PDF file containing the report and shares it via system share sheet.
     */
    fun shareTextReportAsPdf(
        context: Context,
        title: String,
        lines: List<String>,
        fileNamePrefix: String = "report"
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 20f
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 14f
                textAlign = Paint.Align.RIGHT
            }

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 11f
                textAlign = Paint.Align.CENTER
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            val padding = 40f
            var y = padding + 20f

            canvas.drawText(title, pageWidth - padding, y, titlePaint)
            y += 15f
            canvas.drawLine(padding, y, pageWidth - padding, y, linePaint)
            y += 25f

            for (line in lines) {
                if (y > pageHeight - 60) break
                canvas.drawText(line, pageWidth - padding, y, textPaint)
                y += 22f
            }

            canvas.drawLine(padding, pageHeight - 45f, pageWidth - padding, pageHeight - 45f, linePaint)
            canvas.drawText("مدیریت مزرعه – تولید شده آفلاین", pageWidth / 2f, pageHeight - 25f, footerPaint)

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
