package com.example.pos.printer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.text.Layout
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity

/**
 * High-level POS ESC/POS Thermal Printing Coordinator.
 * Interfaces between POS ViewModels and underlying hardware/formatter engines.
 */
object EscPosPrinterEngine {

    /**
     * Formats receipt plain text following PRD Section 4 visual hierarchy.
     */
    fun generateReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): String {
        return ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = paperSize,
            footerLine1 = footerLine1,
            footerLine2 = footerLine2,
            isFullPaid = isFullPaid,
            lang = lang
        )
    }

    /**
     * Converts plain text receipt to ESC/POS binary packet.
     * When Bangla Unicode text is detected, lines containing Bangla are converted
     * to ESC/POS monochrome raster data (GS v 0) to avoid garbled printer output.
     */
    fun buildEscPosBytes(
        receiptText: String,
        autoCut: Boolean,
        paperSize: String = "58mm"
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(ThermalReceiptFormatter.CMD_INIT)
        out.write(ThermalReceiptFormatter.CMD_ALIGN_LEFT)

        if (ThermalReceiptFormatter.containsBangla(receiptText) || receiptText.contains("✓")) {
            val is80mm = paperSize.contains("80") || (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
            val widthDots = if (is80mm) ThermalReceiptFormatter.DOTS_80MM else ThermalReceiptFormatter.DOTS_58MM
            val lines = receiptText.lines()
            for ((index, line) in lines.withIndex()) {
                val hasBangla = ThermalReceiptFormatter.containsBangla(line)
                val hasTick = line.contains("✓")
                if (hasBangla || hasTick) {
                    val trimmed = line.trim()
                    val leadingSpaces = line.takeWhile { it == ' ' }.length
                    val isFullPaid = line.contains("FULL PAID") || line.contains("সম্পূর্ণ পরিশোধিত") || hasTick

                    // Summary line check (Subtotal / Total with Bangla)
                    val isSummary = trimmed.startsWith("Sub Total", ignoreCase = true) || trimmed.startsWith("উপমোট") ||
                            trimmed.startsWith("TOTAL", ignoreCase = true) || trimmed.startsWith("মোট")
                    if (isSummary && hasBangla) {
                        val tokens = trimmed.split(Regex("\\s+"))
                        val amount = if (tokens.last().equals("BDT", ignoreCase = true) && tokens.size > 1) tokens[tokens.size - 2] else tokens.last()
                        val label = if (tokens.last().equals("BDT", ignoreCase = true) && tokens.size > 2) tokens.dropLast(2).joinToString(" ") else tokens.dropLast(1).joinToString(" ")
                        val isBold = trimmed.startsWith("TOTAL", ignoreCase = true) || trimmed.startsWith("মোট")
                        val raster = if (is80mm) {
                            ThermalReceiptFormatter.render80mmSummaryRowToRaster(label, amount, widthDots, isBold)
                        } else {
                            ThermalReceiptFormatter.render58mmSummaryRowToRaster(label, amount, widthDots, isBold)
                        }
                        if (raster != null) {
                            out.write(raster)
                            continue
                        }
                    }

                    val isCompanyHeader = index in 0..4 && !line.contains("=") && !line.contains("-") && leadingSpaces >= 4
                    val alignment = if (isFullPaid || leadingSpaces >= 4) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
                    val raster = ThermalReceiptFormatter.renderTextToRaster(
                        text = trimmed,
                        widthDots = widthDots,
                        textSizePx = if (isCompanyHeader) 30f else if (isFullPaid) 24f else 22f,
                        isBold = isCompanyHeader || isFullPaid,
                        alignment = alignment
                    )
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write("$line\n".toByteArray(Charsets.UTF_8))
                    }
                } else {
                    out.write("$line\n".toByteArray(Charsets.UTF_8))
                }
            }
        } else {
            out.write(receiptText.toByteArray(Charsets.UTF_8))
        }

        out.write(ThermalReceiptFormatter.CMD_FEED_4)
        if (autoCut) {
            out.write(ThermalReceiptFormatter.CMD_CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    /**
     * Generates a fully formatted ESC/POS binary receipt with hardware styling,
     * optional bitmap logo, double sizing, and cutting.
     */
    fun buildFullEscPosReceipt(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        autoCutter: Boolean,
        footerLine1: String,
        footerLine2: String,
        logoBitmap: Bitmap? = null,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): ByteArray {
        return ThermalReceiptFormatter.buildEscPosReceipt(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = paperSize,
            autoCutter = autoCutter,
            footerLine1 = footerLine1,
            footerLine2 = footerLine2,
            logoBitmap = logoBitmap,
            isFullPaid = isFullPaid,
            lang = lang
        )
    }

    /**
     * Sends ESC/POS byte sequence over Bluetooth SPP.
     * Utilizes [BluetoothPrinterManager] with timeout and connection fallback.
     */
    @SuppressLint("MissingPermission")
    suspend fun printOverBluetooth(
        deviceAddress: String,
        payload: ByteArray,
        deviceName: String? = null
    ): Result<Unit> {
        return BluetoothPrinterManager.print(
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            payload = payload
        )
    }

    /**
     * Returns the list of currently bonded / paired devices as (name, address) pairs.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<Pair<String, String>> {
        BluetoothPrinterManager.refreshPairedDevices()
        return BluetoothPrinterManager.pairedDevices.value.map { it.name to it.address }
    }

    /**
     * Loads the company logo bitmap safely for thermal printing.
     */
    fun loadLogoBitmapForThermal(context: Context, logoUri: String?, paperSize: String): Bitmap? {
        val maxDots = if (paperSize.contains("80")) 384 else 256
        return CompanyLogoManager.loadScaledBitmap(context, logoUri, maxDots, maxDots)
    }

    // Helper functions for visual calculation delegation
    fun getVisualWidth(text: String): Int = ThermalReceiptFormatter.getVisualWidth(text)
    fun centerSingleLine(line: String, width: Int): String = ThermalReceiptFormatter.centerSingleLine(line, width)
    fun centerText(text: String, width: Int): String = ThermalReceiptFormatter.centerText(text, width)
}
