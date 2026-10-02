package com.example.pos.modules.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional Native Android PDF Invoice Generation Engine.
 * Produces crisp, multi-page vector-quality standard A4 invoice documents 100% offline
 * using native Android PdfDocument without external network services or bloated dependencies.
 * Fully supports Android save and share mechanisms via FileProvider.
 */
object PdfInvoiceEngine {

    const val PAGE_WIDTH = 595 // Standard A4 width in points (72 DPI)
    const val PAGE_HEIGHT = 842 // Standard A4 height in points (72 DPI)

    const val MARGIN_LEFT = 40f
    const val MARGIN_RIGHT = 555f
    const val MARGIN_TOP = 40f
    const val MARGIN_BOTTOM = 800f
    const val FOOTER_Y = 815f

    const val ROW_HEIGHT = 22f
    const val PAGE1_START_Y = 210f
    const val CONTINUATION_START_Y = 90f
    const val MAX_TABLE_Y = 750f
    const val SUMMARY_HEIGHT = 110f

    // Color palette
    private const val COLOR_PRIMARY = 0xFF0F172A.toInt() // Deep Slate 900
    private const val COLOR_SECONDARY = 0xFF334155.toInt() // Slate 700
    private const val COLOR_MUTED = 0xFF64748B.toInt() // Slate 500
    private const val COLOR_BORDER = 0xFFCBD5E1.toInt() // Slate 300
    private const val COLOR_BG_LIGHT = 0xFFF8FAFC.toInt() // Slate 50
    private const val COLOR_BG_ALT_ROW = 0xFFF1F5F9.toInt() // Slate 100
    private const val COLOR_ACCENT_GREEN = 0xFF15803D.toInt() // Green 700

    /**
     * Calculates the exact total page count based on line item count and layout constraints.
     */
    fun calculatePageCount(linesCount: Int): Int {
        var page = 1
        var currentY = PAGE1_START_Y

        for (i in 0 until linesCount) {
            if (currentY + ROW_HEIGHT > MAX_TABLE_Y) {
                page++
                currentY = CONTINUATION_START_Y
            }
            currentY += ROW_HEIGHT
        }

        if (currentY + SUMMARY_HEIGHT > MAX_TABLE_Y) {
            page++
        }
        return page
    }

    /**
     * Generates a complete, multi-page, professional PDF invoice document offline.
     * Saves the resulting PDF to cacheDir/exports/Invoice_<orderId>.pdf.
     */
    fun generateInvoicePdf(
        context: Context,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): PosResult<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outputFile = File(exportDir, "Invoice_${invoice.orderId}.pdf")

            FileOutputStream(outputFile).use { out ->
                val writeResult = writeInvoicePdfToStream(context, company, invoice, lines, out)
                if (writeResult is PosResult.Error) {
                    return writeResult
                }
            }

            PosResult.Success(outputFile)
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to generate invoice PDF", e))
        }
    }

    /**
     * Generates the PDF document directly into any writable OutputStream (e.g. Storage Access Framework Uri).
     */
    fun writeInvoicePdfToStream(
        context: Context,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        outputStream: OutputStream
    ): PosResult<Unit> {
        return try {
            val totalPages = calculatePageCount(lines.size)
            val pdfDoc = PdfDocument()

            // Paint objects
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = COLOR_PRIMARY
            }
            val linePaint = Paint().apply {
                isAntiAlias = true
                color = COLOR_BORDER
                strokeWidth = 1f
            }
            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            // Attempt to load company logo bitmap
            val logoBitmap = loadLogoBitmap(context, company?.logoUri)

            var currentPageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            // Draw Page 1 Full Header & Metadata
            drawPage1Header(canvas, company, invoice, logoBitmap, textPaint, linePaint, fillPaint)
            drawTableHeader(canvas, textPaint, fillPaint, PAGE1_START_Y - 24f)

            var currentY = PAGE1_START_Y
            var itemIndex = 0

            while (itemIndex < lines.size) {
                if (currentY + ROW_HEIGHT > MAX_TABLE_Y) {
                    // Draw bottom footer on current page
                    drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
                    pdfDoc.finishPage(page)

                    // Start next page
                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas

                    // Draw continuation header
                    drawContinuationHeader(canvas, company, invoice, textPaint, linePaint)
                    drawTableHeader(canvas, textPaint, fillPaint, CONTINUATION_START_Y - 24f)
                    currentY = CONTINUATION_START_Y
                }

                val line = lines[itemIndex]
                val isAltRow = (itemIndex % 2 == 1)
                drawTableRow(canvas, itemIndex + 1, line, currentY, isAltRow, textPaint, fillPaint, linePaint)

                currentY += ROW_HEIGHT
                itemIndex++
            }

            // Check if Summary and Footer Notes fit on current page
            if (currentY + SUMMARY_HEIGHT > MAX_TABLE_Y) {
                drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
                pdfDoc.finishPage(page)

                currentPageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                page = pdfDoc.startPage(pageInfo)
                canvas = page.canvas

                drawContinuationHeader(canvas, company, invoice, textPaint, linePaint)
                currentY = CONTINUATION_START_Y
            }

            // Draw Summary and Footer notes
            drawInvoiceSummary(canvas, invoice, currentY, textPaint, fillPaint, linePaint)

            // Draw bottom page footer on final page
            drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
            pdfDoc.finishPage(page)

            pdfDoc.writeTo(outputStream)
            pdfDoc.close()

            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to write invoice PDF to stream", e))
        }
    }

    /**
     * Exports the invoice PDF to a user-selected Storage Access Framework Uri.
     */
    fun saveInvoicePdfToUri(
        context: Context,
        uri: Uri,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): PosResult<Unit> {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return PosResult.Error(PosError.UnexpectedError("Unable to open output stream for destination Uri"))
            outputStream.use { out ->
                writeInvoicePdfToStream(context, company, invoice, lines, out)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to save PDF to selected location", e))
        }
    }

    /**
     * Constructs a system Intent to share the PDF via Android Share Sheet.
     */
    fun getSharePdfIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice #${pdfFile.nameWithoutExtension.removePrefix("Invoice_")}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Constructs a system Intent to view / open the PDF with any installed PDF viewer app.
     */
    fun getViewPdfIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // -------------------------------------------------------------------------
    // Internal Drawing Routines
    // -------------------------------------------------------------------------

    private fun loadLogoBitmap(context: Context, logoUri: String?): Bitmap? {
        return try {
            CompanyLogoManager.loadScaledBitmap(context, logoUri, 180, 180)
                ?: run {
                    // Fallback to built-in logo if available
                    BitmapFactory.decodeResource(context.resources, R.drawable.logo_original)
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawPage1Header(
        canvas: Canvas,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        logoBitmap: Bitmap?,
        textPaint: Paint,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        val logoLeft = MARGIN_LEFT
        val logoTop = MARGIN_TOP
        val logoWidth = 70f
        val logoHeight = 46f

        // 1. Logo area
        val logoRect = RectF(logoLeft, logoTop, logoLeft + logoWidth, logoTop + logoHeight)
        if (logoBitmap != null) {
            val srcRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
            canvas.drawBitmap(logoBitmap, srcRect, logoRect, null)
        } else {
            // Stylized reserved placeholder badge without literal LOGO text
            fillPaint.color = COLOR_BG_ALT_ROW
            canvas.drawRoundRect(logoRect, 4f, 4f, fillPaint)
            linePaint.color = COLOR_BORDER
            linePaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(logoRect, 4f, 4f, linePaint)
            linePaint.style = Paint.Style.FILL
        }

        // 2. Company Details (beside logo)
        val detailsLeft = logoLeft + logoWidth + 14f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 15f
        val compName = company?.companyName?.ifBlank { "PETROL PUMP & SERVICE STATION" }
            ?: "PETROL PUMP & SERVICE STATION"
        canvas.drawText(compName, detailsLeft, logoTop + 14f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = COLOR_SECONDARY

        val branchText = if (!company?.branchName.isNullOrBlank()) {
            if (!company?.branchNumber.isNullOrBlank()) {
                "${company.branchName} (শাখা #${company.branchNumber})"
            } else {
                company.branchName
            }
        } else if (!company?.branchNumber.isNullOrBlank()) {
            "শাখা #${company.branchNumber}"
        } else {
            "প্রধান শাখা (Main Branch)"
        }
        canvas.drawText(branchText, detailsLeft, logoTop + 27f, textPaint)

        val addressText = company?.address?.ifBlank { "Dhaka, Bangladesh" } ?: "Dhaka, Bangladesh"
        canvas.drawText(addressText, detailsLeft, logoTop + 38f, textPaint)

        val phone = company?.phoneNumber?.ifBlank { "01700000000" } ?: "01700000000"
        val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) phone else "ফোন / Phone: $phone"
        canvas.drawText(phoneText, detailsLeft, logoTop + 49f, textPaint)

        // 3. Invoice Title Badge (Top-Right)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 16f
        textPaint.color = COLOR_PRIMARY
        canvas.drawText("INVOICE / ক্যাশ মেমো", MARGIN_RIGHT, logoTop + 16f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = COLOR_MUTED
        val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(invoice.dateTime))
        canvas.drawText("তারিখ / Date: $dateStr", MARGIN_RIGHT, logoTop + 32f, textPaint)

        textPaint.color = COLOR_ACCENT_GREEN
        canvas.drawText("● PAID / পরিশোধিত", MARGIN_RIGHT, logoTop + 46f, textPaint)

        // 4. Header Divider
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1.2f
        canvas.drawLine(MARGIN_LEFT, 102f, MARGIN_RIGHT, 102f, linePaint)

        // 5. Invoice Metadata Card (Order ID, Customer, Operator)
        val metaTop = 112f
        val metaBottom = 175f
        val metaRect = RectF(MARGIN_LEFT, metaTop, MARGIN_RIGHT, metaBottom)
        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(metaRect, 6f, 6f, fillPaint)
        linePaint.color = COLOR_BORDER
        linePaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(metaRect, 6f, 6f, linePaint)
        linePaint.style = Paint.Style.FILL

        // Metadata Left Column
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_SECONDARY
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("ইনভয়েস নং / Order ID:", MARGIN_LEFT + 12f, metaTop + 18f, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = COLOR_PRIMARY
        textPaint.textSize = 11f
        canvas.drawText("#${invoice.orderId}", MARGIN_LEFT + 12f, metaTop + 34f, textPaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = COLOR_SECONDARY
        val custText = if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else "Walk-in Customer"
        canvas.drawText("গ্রাহক / Customer: $custText", MARGIN_LEFT + 12f, metaTop + 50f, textPaint)

        // Metadata Right Column
        val col2X = 350f
        canvas.drawText("বিক্রয়কারী / Operator: ${invoice.operatorName}", col2X, metaTop + 18f, textPaint)
        val branchCode = company?.branchNumber?.ifBlank { "01" } ?: "01"
        canvas.drawText("শাখা কোড / Branch: $branchCode", col2X, metaTop + 34f, textPaint)
        canvas.drawText("বিল স্ট্যাটাস: নগদ পরিশোধ (Cash)", col2X, metaTop + 50f, textPaint)
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        textPaint: Paint,
        linePaint: Paint
    ) {
        val headerY = MARGIN_TOP + 12f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 11f
        textPaint.color = COLOR_PRIMARY
        val compName = company?.companyName?.ifBlank { "PETROL PUMP & SERVICE STATION" }
            ?: "PETROL PUMP & SERVICE STATION"
        canvas.drawText(compName, MARGIN_LEFT, headerY, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = COLOR_MUTED
        canvas.drawText("— Invoice #${invoice.orderId} (Continued)", MARGIN_LEFT + textPaint.measureText(compName) + 8f, headerY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(invoice.dateTime))
        canvas.drawText("Date: $dateStr", MARGIN_RIGHT, headerY, textPaint)

        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT, headerY + 8f, MARGIN_RIGHT, headerY + 8f, linePaint)
    }

    private fun drawTableHeader(canvas: Canvas, textPaint: Paint, fillPaint: Paint, yTop: Float) {
        val height = 22f
        val rect = RectF(MARGIN_LEFT, yTop, MARGIN_RIGHT, yTop + height)

        fillPaint.color = COLOR_SECONDARY
        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val textY = yTop + 14.5f
        // SL
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SL", MARGIN_LEFT + 15f, textY, textPaint)

        // Item / Description
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("বিবরণ / Item & Description", MARGIN_LEFT + 36f, textY, textPaint)

        // Rate
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("দর / Rate (BDT)", 340f, textY, textPaint)

        // Qty
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("পরিমাণ / Qty", 430f, textY, textPaint)

        // Total
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("মোট / Total (BDT)", MARGIN_RIGHT - 10f, textY, textPaint)
    }

    private fun drawTableRow(
        canvas: Canvas,
        sl: Int,
        line: InvoiceLineEntity,
        yTop: Float,
        isAltRow: Boolean,
        textPaint: Paint,
        fillPaint: Paint,
        linePaint: Paint
    ) {
        val rect = RectF(MARGIN_LEFT, yTop, MARGIN_RIGHT, yTop + ROW_HEIGHT)

        // Alternate row background
        if (isAltRow) {
            fillPaint.color = COLOR_BG_ALT_ROW
            canvas.drawRect(rect, fillPaint)
        }

        // Bottom row divider
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN_LEFT, yTop + ROW_HEIGHT, MARGIN_RIGHT, yTop + ROW_HEIGHT, linePaint)

        val textY = yTop + 15f
        textPaint.textSize = 9f
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // 1. SL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_MUTED
        canvas.drawText(sl.toString(), MARGIN_LEFT + 15f, textY, textPaint)

        // 2. Item Name
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val itemName = line.itemName.take(38)
        canvas.drawText(itemName, MARGIN_LEFT + 36f, textY, textPaint)

        // 3. Unit Rate
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val rateFormatted = FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa)
        canvas.drawText(rateFormatted, 340f, textY, textPaint)

        // 4. Quantity & Unit
        textPaint.textAlign = Paint.Align.RIGHT
        val qtyText = "${line.quantityStr} ${line.unit}"
        canvas.drawText(qtyText, 430f, textY, textPaint)

        // 5. Total
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val totalFormatted = FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa)
        canvas.drawText(totalFormatted, MARGIN_RIGHT - 10f, textY, textPaint)
    }

    private fun drawInvoiceSummary(
        canvas: Canvas,
        invoice: InvoiceEntity,
        yStart: Float,
        textPaint: Paint,
        fillPaint: Paint,
        linePaint: Paint
    ) {
        val dividerY = yStart + 8f
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT, dividerY, MARGIN_RIGHT, dividerY, linePaint)

        val summaryTop = dividerY + 10f

        // Footer Note Box (Left Side)
        val noteWidth = 310f
        val noteHeight = 65f
        val noteRect = RectF(MARGIN_LEFT, summaryTop, MARGIN_LEFT + noteWidth, summaryTop + noteHeight)

        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(noteRect, 4f, 4f, fillPaint)
        linePaint.color = COLOR_BORDER
        linePaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(noteRect, 4f, 4f, linePaint)
        linePaint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_MUTED
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("মন্তব্য ও শর্তাবলী / Notes & Terms:", MARGIN_LEFT + 8f, summaryTop + 14f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 8.5f
        textPaint.color = COLOR_SECONDARY

        val note = if (invoice.footerNote.isNotBlank()) {
            invoice.footerNote
        } else {
            "আমাদের সাথে থাকার জন্য ধন্যবাদ! আবার আসবেন।\nThank you for choosing us! Please come again."
        }

        var noteLineY = summaryTop + 27f
        note.lines().take(3).forEach { lineText ->
            canvas.drawText(lineText.take(50), MARGIN_LEFT + 8f, noteLineY, textPaint)
            noteLineY += 12f
        }

        // Totals Card (Right Side)
        val totalsLeft = 370f
        val subtotalFormatted = "BDT ${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)}"

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10f
        textPaint.color = COLOR_SECONDARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("উপ-মোট / Subtotal:", totalsLeft, summaryTop + 16f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = COLOR_PRIMARY
        canvas.drawText(subtotalFormatted, MARGIN_RIGHT - 10f, summaryTop + 16f, textPaint)

        // Rounded Total Card
        val roundedTop = summaryTop + 26f
        val roundedRect = RectF(totalsLeft - 6f, roundedTop, MARGIN_RIGHT, roundedTop + 36f)
        fillPaint.color = COLOR_PRIMARY
        canvas.drawRoundRect(roundedRect, 4f, 4f, fillPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 10.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("পরিশোধযোগ্য / Total:", totalsLeft, roundedTop + 22f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 13f
        val roundedTotalFormatted = "৳ ${invoice.roundedTotalBdt}.00"
        canvas.drawText(roundedTotalFormatted, MARGIN_RIGHT - 10f, roundedTop + 23f, textPaint)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNum: Int,
        totalPages: Int,
        textPaint: Paint,
        linePaint: Paint
    ) {
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 0.8f
        canvas.drawLine(MARGIN_LEFT, FOOTER_Y - 14f, MARGIN_RIGHT, FOOTER_Y - 14f, linePaint)

        textPaint.textSize = 8.5f
        textPaint.color = COLOR_MUTED
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Left note
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Computer Generated Offline Invoice • POS & Inventory System", MARGIN_LEFT, FOOTER_Y, textPaint)

        // Right Page number
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("পৃষ্ঠা / Page $pageNum of $totalPages", MARGIN_RIGHT, FOOTER_Y, textPaint)
    }
}
