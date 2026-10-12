package com.example.pos.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Thermal Receipt Formatter for 58mm and 80mm ESC/POS Printers.
 * Implements strict PRD visual hierarchy and hardware command sequences.
 *
 * Distinct layouts are generated for 58mm (32 columns) and 80mm (48 columns),
 * with tailored character widths, multi-line wrapping, and column alignments.
 */
object ThermalReceiptFormatter {

    // Paper width constraints in character columns (Font A)
    const val WIDTH_58MM = 32
    const val WIDTH_80MM = 48

    // Dot print widths for standard thermal heads
    const val DOTS_58MM = 384
    const val DOTS_80MM = 576

    // ESC/POS Commands
    val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @ (Initialize printer)
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2

    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0

    val CMD_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
    val CMD_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01) // GS ! 0x01
    val CMD_TEXT_DOUBLE_WIDTH = byteArrayOf(0x1D, 0x21, 0x10) // GS ! 0x10
    val CMD_TEXT_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (2x width & height)
    val CMD_TEXT_TRIPLE_SIZE = byteArrayOf(0x1D, 0x21, 0x22) // GS ! 0x22 (3x width & height)

    val CMD_FEED_3 = byteArrayOf(0x1B, 0x64, 0x03) // ESC d 3
    val CMD_FEED_4 = byteArrayOf(0x1B, 0x64, 0x04) // ESC d 4
    val CMD_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V B 0 (Cut with feed)
    val CMD_CUT_FULL = byteArrayOf(0x1D, 0x56, 0x00) // GS V 0 (Full cut)

    /**
     * Validates that an invoice is ready for printing (non-empty items).
     */
    fun validateInvoice(lines: List<InvoiceLineEntity>): Result<Unit> {
        return if (lines.isEmpty()) {
            Result.failure(IllegalArgumentException("Cannot print empty invoice. Add at least one item."))
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Formats receipt as plain text adhering to PRD visual hierarchy.
     * Used for preview dialogs, text logs, or plain text sharing.
     */
    fun generateReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true
    ): String {
        return if (paperSize.contains("80")) {
            format80mmReceiptText(header, invoice, lines, footerLine1, footerLine2, isFullPaid)
        } else {
            format58mmReceiptText(header, invoice, lines, footerLine1, footerLine2, isFullPaid)
        }
    }

    /**
     * Dedicated 58mm Receipt Layout (32 Character Columns).
     * Tailored for compact mobile/desktop receipt printers.
     */
    fun format58mmReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true
    ): String {
        val width = WIDTH_58MM
        val sb = StringBuilder()
        val sepMajor = "=".repeat(width)
        val sepMinor = "-".repeat(width)

        // Top border separator
        sb.appendLine(sepMajor)

        // 1. Company Name (Preserves line breaks and center-aligns every line dynamically)
        val rawCompanyName = header?.companyName?.ifBlank { "POS STATION" } ?: "POS STATION"
        rawCompanyName.lines().forEach { line ->
            if (line.isNotBlank()) {
                sb.appendLine(centerText(line.trim(), width))
            }
        }

        // 2. Branch info & Address
        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            if (!header.branchNumber.isNullOrBlank()) "${header.branchName} (${header.branchNumber})" else header.branchName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            sb.appendLine(centerText(branchInfo, width))
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            sb.appendLine(centerText(address, width))
        }

        // 4. Phone
        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) phone else "Phone: $phone"
            sb.appendLine(centerText(phoneText, width))
        }

        // 5. Separator
        sb.appendLine(sepMajor)

        // 6. Date/Time
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
        val dateStr = dateFormat.format(Date(invoice.dateTime))
        sb.appendLine("Date: $dateStr")

        // 7. Order ID
        sb.appendLine("Order ID: #${invoice.orderId}")

        // 8. Operator
        sb.appendLine("Operator: ${invoice.operatorName.take(22)}")

        // 9. Customer
        sb.appendLine("Customer: ${invoice.customerInfo.take(22)}")

        // 10. Separator
        sb.appendLine(sepMajor)

        // 11. Item Header (58mm - 32 columns)
        // Format: Item Name (12) + Qty (4) + Rate (6) + Total (7) + 3 spaces = 32
        sb.appendLine(String.format(Locale.US, "%-12s %4s %6s %7s", "Item Name", "Qty", "Rate", "Total"))
        sb.appendLine(sepMinor)

        // 12. Items
        for (item in lines) {
            val name = item.itemName
            val qtyUnit = "${item.quantityStr}${item.unit.take(2)}".trim()
            val rate = FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            val total = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)

            if (getVisualWidth(name) <= 12) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%-12s %4s %6s %7s",
                        name,
                        qtyUnit.take(4),
                        rate.take(6),
                        total.take(7)
                    )
                )
            } else {
                // Item name on line 1, details indented on line 2
                val wrappedName = wrapText(name, width)
                wrappedName.forEach { sb.appendLine(it) }
                val detailLeft = "  ${qtyUnit} x ${rate}"
                sb.appendLine(padRow(detailLeft, "$total BDT", width))
            }
        }

        // 13. Separator
        sb.appendLine(sepMinor)

        // 14. Subtotal
        val subtotalStr = "${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} BDT"
        sb.appendLine(padRow("Sub Total:", subtotalStr, width))

        // 15. Rounded Total
        val roundedStr = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} BDT"
        sb.appendLine(padRow("TOTAL:", roundedStr, width))

        // 16. Separator
        sb.appendLine(sepMajor)

        // Full Paid status (after invoice content and BEFORE Footer Note)
        if (isFullPaid) {
            sb.appendLine(centerText("[✓] FULL PAID", width))
            sb.appendLine(sepMinor)
        }

        // 17. Footer notes
        if (footerLine1.isNotBlank()) {
            footerLine1.lines().forEach { l ->
                if (l.isNotBlank()) sb.appendLine(centerText(l.trim(), width))
            }
        }
        if (footerLine2.isNotBlank()) {
            footerLine2.lines().forEach { l ->
                if (l.isNotBlank()) sb.appendLine(centerText(l.trim(), width))
            }
        }

        // 18. Ending separator
        sb.appendLine(sepMajor)

        return sb.toString()
    }

    /**
     * Dedicated 80mm Receipt Layout (48 Character Columns).
     * Takes full advantage of wide format with 5-column table and side-by-side metadata.
     */
    fun format80mmReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true
    ): String {
        val width = WIDTH_80MM
        val sb = StringBuilder()
        val sepMajor = "=".repeat(width)
        val sepMinor = "-".repeat(width)

        // Top border separator
        sb.appendLine(sepMajor)

        // 1. Company Name (Preserves line breaks and center-aligns every line dynamically)
        val rawCompanyName = header?.companyName?.ifBlank { "POS & INVOICE STATION" } ?: "POS & INVOICE STATION"
        rawCompanyName.lines().forEach { line ->
            if (line.isNotBlank()) {
                sb.appendLine(centerText(line.trim(), width))
            }
        }

        // 2. Branch & Address
        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            if (!header.branchNumber.isNullOrBlank()) "${header.branchName} (${header.branchNumber})" else header.branchName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            sb.appendLine(centerText(branchInfo, width))
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            sb.appendLine(centerText(address, width))
        }

        // 4. Phone
        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) phone else "Phone: $phone"
            sb.appendLine(centerText(phoneText, width))
        }

        // 5. Separator
        sb.appendLine(sepMajor)

        // 6 & 7. Order ID and Date/Time (Side by side on 80mm)
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
        val dateStr = dateFormat.format(Date(invoice.dateTime))
        val orderText = "Order ID: #${invoice.orderId}"
        val dateText = "Date: $dateStr"
        sb.appendLine(padRow(orderText, dateText, width))

        // 8 & 9. Operator & Customer (Side by side on 80mm)
        val opText = "Operator: ${invoice.operatorName.take(18)}"
        val custText = "Customer: ${invoice.customerInfo.take(18)}"
        sb.appendLine(padRow(opText, custText, width))

        // 10. Separator
        sb.appendLine(sepMajor)

        // 11. Item Header (80mm - 48 columns)
        // Columns: Item Description (18) + Qty (5) + Unit (5) + Rate (8) + Total (8) + 4 spaces = 48
        sb.appendLine(
            String.format(
                Locale.US,
                "%-18s %5s %-5s %8s %8s",
                "Item Description",
                "Qty",
                "Unit",
                "Rate",
                "Total"
            )
        )
        sb.appendLine(sepMinor)

        // 12. Items
        for (item in lines) {
            val name = item.itemName
            val qty = item.quantityStr
            val unit = item.unit.take(5)
            val rate = FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            val total = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)

            if (getVisualWidth(name) <= 18) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%-18s %5s %-5s %8s %8s",
                        name,
                        qty.take(5),
                        unit,
                        rate.take(8),
                        total.take(8)
                    )
                )
            } else {
                // Wrap long item name
                val nameLines = wrapText(name, 18)
                // First line with item values
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%-18s %5s %-5s %8s %8s",
                        nameLines.firstOrNull() ?: name,
                        qty.take(5),
                        unit,
                        rate.take(8),
                        total.take(8)
                    )
                )
                // Subsequent lines for remaining item name
                for (i in 1 until nameLines.size) {
                    sb.appendLine(nameLines[i])
                }
            }
        }

        // 13. Separator
        sb.appendLine(sepMinor)

        // 14. Subtotal
        val subtotalStr = "${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} BDT"
        sb.appendLine(padRow("Sub Total Amount:", subtotalStr, width))

        // 15. Rounded Total
        val roundedStr = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} BDT"
        sb.appendLine(padRow("NET PAYABLE (Rounded):", roundedStr, width))

        // 16. Separator
        sb.appendLine(sepMajor)

        // Full Paid status (after invoice content and BEFORE Footer Note)
        if (isFullPaid) {
            sb.appendLine(centerText("[✓] FULL PAID", width))
            sb.appendLine(sepMinor)
        }

        // 17. Footer notes
        if (footerLine1.isNotBlank()) {
            footerLine1.lines().forEach { l ->
                if (l.isNotBlank()) sb.appendLine(centerText(l.trim(), width))
            }
        }
        if (footerLine2.isNotBlank()) {
            footerLine2.lines().forEach { l ->
                if (l.isNotBlank()) sb.appendLine(centerText(l.trim(), width))
            }
        }

        // 18. Ending separator
        sb.appendLine(sepMajor)

        return sb.toString()
    }

    /**
     * Builds standard ESC/POS binary command sequence.
     * Incorporates hardware styling: alignment, bold, double-sizing, optional raster logo, line feeds, and auto-cut.
     */
    fun buildEscPosReceipt(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        autoCutter: Boolean,
        footerLine1: String,
        footerLine2: String,
        logoBitmap: Bitmap? = null,
        isFullPaid: Boolean = true
    ): ByteArray {
        val is80mm = paperSize.contains("80")
        val width = if (is80mm) WIDTH_80MM else WIDTH_58MM
        val widthDots = if (is80mm) DOTS_80MM else DOTS_58MM
        val maxLogoDots = if (is80mm) 384 else 256

        val out = ByteArrayOutputStream()

        // 1. Initialize
        out.write(CMD_INIT)

        // 2. Logo (Raster Bitmap if available)
        if (logoBitmap != null) {
            val rasterLogo = bitmapToEscPosRaster(logoBitmap, maxLogoDots)
            if (rasterLogo != null && rasterLogo.isNotEmpty()) {
                out.write(CMD_ALIGN_CENTER)
                out.write(rasterLogo)
                out.write(CMD_FEED_lines(1))
            }
        }

        // 3. Company Header (Centered, Bold, 3x Magnification)
        out.write(CMD_ALIGN_CENTER)
        val rawCompanyName = header?.companyName?.ifBlank { "POS STATION" } ?: "POS STATION"
        if (containsBangla(rawCompanyName)) {
            val headerTextSize = if (is80mm) 38f else 32f
            rawCompanyName.lines().forEach { line ->
                if (line.isNotBlank()) {
                    val raster = renderTextToRaster(
                        text = line.trim(),
                        widthDots = widthDots,
                        textSizePx = headerTextSize,
                        isBold = true,
                        alignment = Layout.Alignment.ALIGN_CENTER
                    )
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write(line.trim().toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            }
        } else {
            out.write(CMD_BOLD_ON)
            out.write(CMD_TEXT_TRIPLE_SIZE)
            rawCompanyName.lines().forEach { line ->
                if (line.isNotBlank()) {
                    out.write(line.trim().toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
            out.write(CMD_TEXT_NORMAL)
            out.write(CMD_BOLD_OFF)
        }

        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            if (!header.branchNumber.isNullOrBlank()) "${header.branchName} (${header.branchNumber})" else header.branchName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            if (containsBangla(branchInfo)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = branchInfo.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(branchInfo, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(branchInfo, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            if (containsBangla(address)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = address.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(address, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(address, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) phone else "Phone: $phone"
            if (containsBangla(phoneText)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = phoneText.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(phoneText, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(phoneText, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        // Separator
        val sepMajor = "=".repeat(width)
        val sepMinor = "-".repeat(width)
        out.write(sepMajor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Metadata section (Left aligned)
        out.write(CMD_ALIGN_LEFT)
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
        val dateStr = dateFormat.format(Date(invoice.dateTime))

        if (is80mm) {
            val orderDateLine = padRow("Order ID: #${invoice.orderId}", "Date: $dateStr", width)
            if (containsBangla(orderDateLine)) {
                val raster = renderTextToRaster(orderDateLine, widthDots, textSizePx = 22f, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(orderDateLine.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            } else {
                out.write(orderDateLine.toByteArray(Charsets.UTF_8))
                out.write(0x0A)
            }
            val opCustLine = padRow("Operator: ${invoice.operatorName}", "Customer: ${invoice.customerInfo}", width)
            if (containsBangla(opCustLine)) {
                val raster = renderTextToRaster(opCustLine, widthDots, textSizePx = 22f, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(opCustLine.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            } else {
                val opCustPadded = padRow("Operator: ${invoice.operatorName.take(18)}", "Customer: ${invoice.customerInfo.take(18)}", width)
                out.write(opCustPadded.toByteArray(Charsets.UTF_8))
                out.write(0x0A)
            }
        } else {
            out.write("Date: $dateStr\n".toByteArray(Charsets.UTF_8))
            val orderLine = "Order ID: #${invoice.orderId}"
            if (containsBangla(orderLine)) {
                val raster = renderTextToRaster(orderLine, widthDots, textSizePx = 22f, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write("$orderLine\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                out.write("$orderLine\n".toByteArray(Charsets.UTF_8))
            }
            val opLine = if (containsBangla(invoice.operatorName)) "Operator: ${invoice.operatorName}" else "Operator: ${invoice.operatorName.take(22)}"
            if (containsBangla(opLine)) {
                val raster = renderTextToRaster(opLine, widthDots, textSizePx = 22f, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write("$opLine\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                out.write("$opLine\n".toByteArray(Charsets.UTF_8))
            }
            val custLine = if (containsBangla(invoice.customerInfo)) "Customer: ${invoice.customerInfo}" else "Customer: ${invoice.customerInfo.take(22)}"
            if (containsBangla(custLine)) {
                val raster = renderTextToRaster(custLine, widthDots, textSizePx = 22f, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write("$custLine\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                out.write("$custLine\n".toByteArray(Charsets.UTF_8))
            }
        }

        // Separator
        out.write(sepMajor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Table Header
        out.write(CMD_BOLD_ON)
        if (is80mm) {
            out.write(
                String.format(
                    Locale.US,
                    "%-18s %5s %-5s %8s %8s\n",
                    "Item Description",
                    "Qty",
                    "Unit",
                    "Rate",
                    "Total"
                ).toByteArray(Charsets.UTF_8)
            )
        } else {
            out.write(
                String.format(
                    Locale.US,
                    "%-12s %4s %6s %7s\n",
                    "Item Name",
                    "Qty",
                    "Rate",
                    "Total"
                ).toByteArray(Charsets.UTF_8)
            )
        }
        out.write(CMD_BOLD_OFF)
        out.write(sepMinor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Line Items
        for (item in lines) {
            val name = item.itemName
            val qty = item.quantityStr
            val unit = item.unit
            val rate = FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            val total = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            val itemHasBangla = containsBangla(name) || containsBangla(unit) || containsBangla(qty) || containsBangla(rate) || containsBangla(total)

            if (is80mm) {
                if (itemHasBangla) {
                    val raster = render80mmItemRowToRaster(name, qty, unit, rate, total, widthDots)
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write(format80mmNativeItemLine(name, qty, unit.take(4), rate, total).toByteArray(Charsets.UTF_8))
                    }
                } else {
                    out.write(format80mmNativeItemLine(name, qty, unit.take(4), rate, total).toByteArray(Charsets.UTF_8))
                }
            } else {
                if (itemHasBangla) {
                    val raster = render58mmItemRowToRaster(name, qty, unit, rate, total, widthDots)
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write(format58mmNativeItemLine(name, qty, unit.take(4), rate, total, width).toByteArray(Charsets.UTF_8))
                    }
                } else {
                    out.write(format58mmNativeItemLine(name, qty, unit.take(4), rate, total, width).toByteArray(Charsets.UTF_8))
                }
            }
        }

        // Separator
        out.write(sepMinor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Subtotal
        val subtotalStr = "${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} BDT"
        val subtotalLine = padRow("Sub Total:", subtotalStr, width)
        if (containsBangla(subtotalLine)) {
            val raster = renderTextToRaster(subtotalLine, widthDots, textSizePx = 22f, isBold = false)
            if (raster != null) {
                out.write(raster)
            } else {
                out.write("$subtotalLine\n".toByteArray(Charsets.UTF_8))
            }
        } else {
            out.write("$subtotalLine\n".toByteArray(Charsets.UTF_8))
        }

        // Rounded Total (Bold, Double-Height)
        val roundedStr = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} BDT"
        val totalLine = padRow("TOTAL:", roundedStr, width)
        if (containsBangla(totalLine)) {
            val raster = renderTextToRaster(totalLine, widthDots, textSizePx = 28f, isBold = true)
            if (raster != null) {
                out.write(raster)
            } else {
                out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
            }
        } else {
            out.write(CMD_BOLD_ON)
            out.write(CMD_TEXT_DOUBLE_HEIGHT)
            out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
            out.write(CMD_TEXT_NORMAL)
            out.write(CMD_BOLD_OFF)
        }

        // Separator
        out.write(sepMajor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Full Paid status (after invoice content and BEFORE Footer Note)
        if (isFullPaid) {
            out.write(CMD_ALIGN_CENTER)
            out.write(CMD_BOLD_ON)
            out.write("[✓] FULL PAID\n".toByteArray(Charsets.UTF_8))
            out.write(CMD_BOLD_OFF)
            out.write(sepMinor.toByteArray(Charsets.UTF_8))
            out.write(0x0A)
        }

        // Footer Notes (Centered)
        out.write(CMD_ALIGN_CENTER)
        if (footerLine1.isNotBlank()) {
            if (containsBangla(footerLine1)) {
                val raster = renderTextToRaster(
                    text = footerLine1.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(footerLine1, width).forEach { line ->
                        out.write("$line\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                wrapLinesForWidth(footerLine1, width).forEach { line ->
                    out.write("$line\n".toByteArray(Charsets.UTF_8))
                }
            }
        }
        if (footerLine2.isNotBlank()) {
            if (containsBangla(footerLine2)) {
                val raster = renderTextToRaster(
                    text = footerLine2.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(footerLine2, width).forEach { line ->
                        out.write("$line\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                wrapLinesForWidth(footerLine2, width).forEach { line ->
                    out.write("$line\n".toByteArray(Charsets.UTF_8))
                }
            }
        }
        out.write(sepMajor.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Feed paper so bottom text clears cutter blade
        out.write(CMD_FEED_4)

        // Auto Cut command if enabled
        if (autoCutter) {
            out.write(CMD_CUT_PARTIAL)
        }

        return out.toByteArray()
    }

    private fun format80mmNativeItemLine(name: String, qty: String, unit: String, rate: String, total: String): String {
        val sb = StringBuilder()
        if (getVisualWidth(name) <= 18) {
            sb.append(
                String.format(
                    Locale.US,
                    "%-18s %5s %-5s %8s %8s\n",
                    name,
                    qty.take(5),
                    unit,
                    rate.take(8),
                    total.take(8)
                )
            )
        } else {
            val nameLines = wrapText(name, 18)
            sb.append(
                String.format(
                    Locale.US,
                    "%-18s %5s %-5s %8s %8s\n",
                    nameLines.firstOrNull() ?: name,
                    qty.take(5),
                    unit,
                    rate.take(8),
                    total.take(8)
                )
            )
            for (i in 1 until nameLines.size) {
                sb.append("${nameLines[i]}\n")
            }
        }
        return sb.toString()
    }

    private fun format58mmNativeItemLine(name: String, qty: String, unit: String, rate: String, total: String, width: Int): String {
        val sb = StringBuilder()
        if (getVisualWidth(name) <= 12) {
            sb.append(
                String.format(
                    Locale.US,
                    "%-12s %4s %6s %7s\n",
                    name,
                    qty.take(4),
                    rate.take(6),
                    total.take(7)
                )
            )
        } else {
            val wrapped = wrapText(name, width)
            wrapped.forEach { sb.append("$it\n") }
            val detailLeft = "  ${qty}${unit} x ${rate}"
            sb.append("${padRow(detailLeft, "$total BDT", width)}\n")
        }
        return sb.toString()
    }

    /**
     * Builds ESC/POS bytes for a hardware test slip.
     */
    fun buildEscPosTestSlip(
        companyName: String?,
        paperSize: String,
        autoCutter: Boolean
    ): ByteArray {
        val is80mm = paperSize.contains("80")
        val width = if (is80mm) WIDTH_80MM else WIDTH_58MM
        val widthDots = if (is80mm) DOTS_80MM else DOTS_58MM
        val out = ByteArrayOutputStream()

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_TEXT_DOUBLE_SIZE)
        out.write("*** TEST PRINT ***\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_TEXT_NORMAL)
        out.write(CMD_BOLD_OFF)
        out.write("================================\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_BOLD_ON)
        val name = companyName ?: "POS Station"
        if (containsBangla(name)) {
            val raster = renderTextToRaster(
                text = name,
                widthDots = widthDots,
                textSizePx = 28f,
                isBold = true,
                alignment = Layout.Alignment.ALIGN_CENTER
            )
            raster?.let { out.write(it) }
        } else {
            out.write("$name\n".toByteArray(Charsets.UTF_8))
        }
        out.write(CMD_BOLD_OFF)

        out.write(CMD_ALIGN_LEFT)
        out.write("Paper Format: ${if (is80mm) "80mm (48 Columns)" else "58mm (32 Columns)"}\n".toByteArray(Charsets.UTF_8))
        out.write("Auto-Cutter : ${if (autoCutter) "ENABLED (GS V 66 0)" else "DISABLED"}\n".toByteArray(Charsets.UTF_8))
        out.write("Timestamp   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n".toByteArray(Charsets.UTF_8))
        out.write("Connection  : Bluetooth SPP / RFCOMM OK\n".toByteArray(Charsets.UTF_8))

        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))
        out.write("Font Test: Regular, Bold, Double\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_ALIGN_CENTER)
        out.write("ESC/POS Commands Verified!\n".toByteArray(Charsets.UTF_8))
        out.write("================================\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_FEED_4)
        if (autoCutter) {
            out.write(CMD_CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    /**
     * Plain text representation for test slip preview.
     */
    fun generateTestSlipText(
        companyName: String?,
        paperSize: String,
        autoCutter: Boolean
    ): String {
        val width = if (paperSize.contains("80")) WIDTH_80MM else WIDTH_58MM
        val sep = "=".repeat(width)
        val sepMinor = "-".repeat(width)
        return StringBuilder().apply {
            appendLine(sep)
            appendLine(centerText("*** TEST PRINT SLIP ***", width))
            appendLine(sep)
            appendLine(centerText(companyName ?: "POS Station", width))
            appendLine("Paper Size : ${if (paperSize.contains("80")) "80mm (48 cols)" else "58mm (32 cols)"}")
            appendLine("Auto Cutter: ${if (autoCutter) "ON" else "OFF"}")
            appendLine("Date/Time  : ${SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", Locale.US).format(Date())}")
            appendLine("Status     : Bluetooth Hardware Verified OK")
            appendLine(sepMinor)
            appendLine(centerText("Thermal Printing Ready!", width))
            appendLine(sep)
        }.toString()
    }

    /**
     * Converts an Android Bitmap into standard ESC/POS raster bit image command:
     * `GS v 0 0 xL xH yL yH d1...dk`
     */
    fun bitmapToEscPosRaster(source: Bitmap, maxWidthDots: Int): ByteArray? {
        return try {
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) return null

            // Scale down if larger than max printable width
            val targetWidth = if (width > maxWidthDots) maxWidthDots else width
            // Align to multiple of 8 for whole byte rows
            val alignedWidth = ((targetWidth + 7) / 8) * 8
            val targetHeight = (height.toFloat() * (alignedWidth.toFloat() / width.toFloat())).toInt().coerceAtLeast(1)

            val scaled = Bitmap.createScaledBitmap(source, alignedWidth, targetHeight, true)
            val bytesWidth = alignedWidth / 8
            val totalBytes = bytesWidth * targetHeight

            val out = ByteArrayOutputStream()
            // GS v 0 m (m=0 normal)
            out.write(0x1D)
            out.write(0x76)
            out.write(0x30)
            out.write(0x00)

            // xL, xH
            out.write(bytesWidth and 0xFF)
            out.write((bytesWidth shr 8) and 0xFF)

            // yL, yH
            out.write(targetHeight and 0xFF)
            out.write((targetHeight shr 8) and 0xFF)

            // Raster monochrome data: 1 = black dot, 0 = white dot
            val pixels = IntArray(alignedWidth * targetHeight)
            scaled.getPixels(pixels, 0, alignedWidth, 0, 0, alignedWidth, targetHeight)

            for (y in 0 until targetHeight) {
                for (b in 0 until bytesWidth) {
                    var byteVal = 0
                    for (bit in 0 until 8) {
                        val x = b * 8 + bit
                        val pixel = pixels[y * alignedWidth + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val bVal = Color.blue(pixel)
                        val alpha = Color.alpha(pixel)
                        val luminance = (r * 0.299 + g * 0.587 + bVal * 0.114).toInt()

                        // Dark pixel or non-transparent printed dot
                        if (alpha > 128 && luminance < 160) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                    out.write(byteVal)
                }
            }
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a string contains any Bengali Unicode character (\u0980..\u09FF).
     */
    fun containsBangla(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
    }

    /**
     * Renders a text string into an ESC/POS monochrome raster command (GS v 0)
     * using Android's native StaticLayout and TextPaint.
     * This guarantees HarfBuzz complex text shaping for Bengali conjuncts, ligatures,
     * vowel signs, and numbers without splitting or reordering characters manually.
     */
    fun renderTextToRaster(
        text: String,
        widthDots: Int,
        textSizePx: Float = 24f,
        isBold: Boolean = false,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): ByteArray? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = textSizePx
                typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }

            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(trimmed, 0, trimmed.length, textPaint, widthDots)
                    .setAlignment(alignment)
                    .setLineSpacing(2f, 1.1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(trimmed, textPaint, widthDots, alignment, 1.1f, 2f, true)
            }

            val height = staticLayout.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            staticLayout.draw(canvas)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders a 5-column 80mm item line containing Bangla characters to ESC/POS raster.
     * Columns: Item Description (up to 240 dots), Qty, Unit, Rate, Total.
     */
    fun render80mmItemRowToRaster(
        name: String,
        qty: String,
        unit: String,
        rate: String,
        total: String,
        widthDots: Int = DOTS_80MM
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.DEFAULT
            }

            // Column 1: Item description (up to 240 dots)
            val nameWidth = 240
            val nameLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(name, 0, name.length, textPaint, nameWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(name, textPaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
            }

            val rowHeight = maxOf(nameLayout.height, 28) + 4
            val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw name
            canvas.save()
            canvas.translate(2f, 2f)
            nameLayout.draw(canvas)
            canvas.restore()

            // Baseline for columns on the first line
            val baseline = 22f

            // Draw Qty (right-aligned at 305)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(qty, 305f, baseline, textPaint)

            // Draw Unit (left-aligned at 318)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(unit, 318f, baseline, textPaint)

            // Draw Rate (right-aligned at 460)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(rate, 460f, baseline, textPaint)

            // Draw Total (right-aligned at 572)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(total, 572f, baseline, textPaint)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders a 58mm item line containing Bangla characters to ESC/POS raster.
     * Line 1: Item Name (full width up to 384 dots)
     * Line 2: Details: "  ${qty}${unit} x ${rate}        ${total} BDT"
     */
    fun render58mmItemRowToRaster(
        name: String,
        qty: String,
        unit: String,
        rate: String,
        total: String,
        widthDots: Int = DOTS_58MM
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.DEFAULT
            }

            // Line 1: Item Name (full width up to 384 dots)
            val nameWidth = widthDots - 8
            val nameLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(name, 0, name.length, textPaint, nameWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(name, textPaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
            }

            val line2Height = 26
            val rowHeight = nameLayout.height + line2Height + 4
            val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw name
            canvas.save()
            canvas.translate(4f, 2f)
            nameLayout.draw(canvas)
            canvas.restore()

            // Draw details line: "  ${qty}${unit} x ${rate}        ${total} BDT"
            val line2Baseline = nameLayout.height + 20f
            val detailLeft = "  ${qty}${unit} x $rate"
            val totalRight = "$total BDT"

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(detailLeft, 4f, line2Baseline, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(totalRight, (widthDots - 4).toFloat(), line2Baseline, textPaint)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    private fun CMD_FEED_lines(n: Int): ByteArray {
        val count = n.coerceIn(1, 255)
        return byteArrayOf(0x1B, 0x64, count.toByte())
    }

    /**
     * Calculates visual monospace terminal width accounting for Indic/Bengali
     * combining marks, diacritics, and ligatures.
     */
    fun getVisualWidth(text: String): Int {
        var count = 0
        var prevWasVirama = false
        for (i in text.indices) {
            val c = text[i]
            val type = Character.getType(c.code)
            val isCombining = type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    c == '\u200C' || c == '\u200D'
            if (isCombining) {
                if (c == '\u09CD') {
                    prevWasVirama = true
                }
                continue
            }
            if (prevWasVirama) {
                prevWasVirama = false
                continue
            }
            prevWasVirama = false
            count++
        }
        return count
    }

    fun centerSingleLine(line: String, width: Int): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ""
        val vWidth = getVisualWidth(trimmed)
        if (vWidth >= width) return trimmed
        val pad = (width - vWidth) / 2
        return " ".repeat(maxOf(0, pad)) + trimmed
    }

    fun wrapLinesForWidth(text: String, width: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            var currentLine = ""
            for (word in words) {
                if (getVisualWidth(word) > width) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = ""
                    }
                    var chunk = ""
                    for (ch in word) {
                        if (getVisualWidth(chunk + ch) > width && chunk.isNotEmpty()) {
                            lines.add(chunk)
                            chunk = ch.toString()
                        } else {
                            chunk += ch
                        }
                    }
                    currentLine = chunk
                } else if (currentLine.isEmpty()) {
                    currentLine = word
                } else {
                    val candidate = "$currentLine $word"
                    if (getVisualWidth(candidate) <= width) {
                        currentLine = candidate
                    } else {
                        lines.add(currentLine)
                        currentLine = word
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        return lines
    }

    fun centerText(text: String, width: Int): String {
        if (text.isBlank()) return ""
        return wrapLinesForWidth(text, width).joinToString("\n") { centerSingleLine(it, width) }
    }

    fun wrapText(text: String, width: Int): List<String> {
        val result = mutableListOf<String>()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var current = ""
        for (w in words) {
            if (current.isEmpty()) {
                current = w
            } else {
                val candidate = "$current $w"
                if (getVisualWidth(candidate) <= width) {
                    current = candidate
                } else {
                    result.add(current)
                    current = w
                }
            }
        }
        if (current.isNotEmpty()) {
            if (getVisualWidth(current) > width) {
                var chunk = ""
                for (ch in current) {
                    if (getVisualWidth(chunk + ch) > width) {
                        result.add(chunk)
                        chunk = ch.toString()
                    } else {
                        chunk += ch
                    }
                }
                if (chunk.isNotEmpty()) result.add(chunk)
            } else {
                result.add(current)
            }
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    fun padRow(left: String, right: String, width: Int): String {
        val spacesNeeded = width - getVisualWidth(left) - getVisualWidth(right)
        return if (spacesNeeded > 0) {
            left + " ".repeat(spacesNeeded) + right
        } else {
            "$left $right"
        }
    }
}
