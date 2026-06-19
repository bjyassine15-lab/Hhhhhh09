package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.relation.InvoiceWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceShareHelper {

    fun shareInvoiceAsPdf(context: Context, invoiceWithItems: InvoiceWithItems) {
        try {
            val pdfDocument = PdfDocument()
            // A4 page dimension pixels: 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint()
            val boldPaint = Paint().apply {
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                this.textSize = 14f
                this.color = Color.BLACK
            }
            val titlePaint = Paint().apply {
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                this.textSize = 22f
                this.color = Color.BLACK
                this.textAlign = Paint.Align.CENTER
            }
            val headerPaint = Paint().apply {
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                this.textSize = 12f
                this.color = Color.BLACK
            }
            val normalPaint = Paint().apply {
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                this.textSize = 11f
                this.color = Color.BLACK
            }
            val footerPaint = Paint().apply {
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                this.textSize = 10f
                this.color = Color.GRAY
                this.textAlign = Paint.Align.CENTER
            }

            var currentY = 60f

            // 1. Title Header
            canvas.drawText("الكاشير الذكي - فاتورة مبيعات", 595f / 2f, currentY, titlePaint)
            currentY += 40f

            // Divider Line
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawLine(40f, currentY, 555f, currentY, paint)
            currentY += 30f

            // 2. Invoice Meta data
            val sdfStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            val dateText = sdfStr.format(Date(invoiceWithItems.invoice.timestamp))

            canvas.drawText("رقم الفاتورة: ${invoiceWithItems.invoice.invoiceNumber}", 40f, currentY, boldPaint)
            currentY += 25f
            canvas.drawText("تاريخ المعاملة: $dateText", 40f, currentY, normalPaint)
            currentY += 25f
            val paymentMethod = if (invoiceWithItems.invoice.isDebt) "كريدي (ديون معلقة)" else "نقداً (كاش)"
            canvas.drawText("طريقة الدفع: $paymentMethod", 40f, currentY, boldPaint)
            currentY += 30f

            // Divider Line
            canvas.drawLine(40f, currentY, 555f, currentY, paint)
            currentY += 30f

            // 3. Columns Labels Header
            canvas.drawText("المادة والمنتج", 40f, currentY, headerPaint)
            canvas.drawText("السعر الفردي", 280f, currentY, headerPaint)
            canvas.drawText("الكمية", 400f, currentY, headerPaint)
            canvas.drawText("القيمة الإجمالية", 475f, currentY, headerPaint)
            currentY += 12f
            canvas.drawLine(40f, currentY, 555f, currentY, paint.apply { strokeWidth = 1f; color = Color.LTGRAY })
            currentY += 25f

            // Draw each item row in list
            invoiceWithItems.items.forEach { item ->
                canvas.drawText(item.productName, 40f, currentY, normalPaint)
                canvas.drawText(String.format("%.2f", item.salePrice), 280f, currentY, normalPaint)
                canvas.drawText(item.quantity.toString(), 400f, currentY, normalPaint)
                val rowTotal = item.salePrice * item.quantity
                canvas.drawText(String.format("%.2f", rowTotal), 475f, currentY, boldPaint)
                currentY += 25f
            }

            currentY += 15f
            canvas.drawLine(40f, currentY, 555f, currentY, paint.apply { strokeWidth = 1.5f; color = Color.BLACK })
            currentY += 35f

            // 4. Summaries totals at bottom
            canvas.drawText("المجموع الكلي للفاتورة:", 40f, currentY, boldPaint.apply { textSize = 14f })
            canvas.drawText(String.format("%.2f د.ت", invoiceWithItems.invoice.totalAmount), 450f, currentY, boldPaint.apply { textSize = 14f })
            currentY += 25f

            if (invoiceWithItems.invoice.isDebt) {
                val debtAmt = invoiceWithItems.invoice.totalAmount - invoiceWithItems.invoice.paidAmount
                canvas.drawText("المبلغ المدفوع كاش:", 40f, currentY, normalPaint)
                canvas.drawText(String.format("%.2f د.ت", invoiceWithItems.invoice.paidAmount), 450f, currentY, normalPaint)
                currentY += 25f
                canvas.drawText("الدين المسجل (كريدي):", 40f, currentY, boldPaint.apply { textSize = 12f; color = Color.RED })
                canvas.drawText(String.format("%.2f د.ت", debtAmt), 450f, currentY, boldPaint.apply { textSize = 12f; color = Color.RED })
                currentY += 25f
            }

            // Restore color
            boldPaint.color = Color.BLACK
            boldPaint.textSize = 14f

            // Footer brand signature
            canvas.drawText("شكراً لتسوقكم معنا - الكاشير الذكي ونقاط البيع الفورية", 595f / 2f, 800f, footerPaint)

            pdfDocument.finishPage(page)

            // Save PDF locally in internal cache (matching pdf_cache in file_paths.xml)
            val invoiceFileName = "invoice_${invoiceWithItems.invoice.invoiceNumber}.pdf"
            val pdfFile = File(context.cacheDir, invoiceFileName)
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()

            // Get secure Uri using authority com.example.fileprovider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                pdfFile
            )

            // Grant action send
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "فاتورة مبيعات الكاشير الذكي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كمستند PDF 📤"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطأ في توليد وطباعة وثيقة PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
