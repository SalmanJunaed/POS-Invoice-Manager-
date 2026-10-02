package com.example.pos.printer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
        isFullPaid: Boolean = true
    ): String {
        return ThermalReceiptFormatter.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = paperSize,
            footerLine1 = footerLine1,
            footerLine2 = footerLine2,
            isFullPaid = isFullPaid
        )
    }

    /**
     * Converts plain text receipt to ESC/POS binary packet.
     */
    fun buildEscPosBytes(
        receiptText: String,
        autoCut: Boolean
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(ThermalReceiptFormatter.CMD_INIT)
        out.write(ThermalReceiptFormatter.CMD_ALIGN_LEFT)
        out.write(receiptText.toByteArray(Charsets.UTF_8))
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
        isFullPaid: Boolean = true
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
            isFullPaid = isFullPaid
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
