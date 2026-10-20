package com.example.pos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.printer.ThermalReceiptFormatter
import com.example.pos.ui.components.CompanyLogoView

@Composable
fun ReceiptPreviewDialog(
    receiptText: String,
    language: String,
    logoUri: String? = null,
    companyName: String? = null,
    isFullPaid: Boolean = true,
    onDismiss: () -> Unit,
    onPrintBluetooth: () -> Unit,
    onSharePdf: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Exact 100 x 100 px square reserved area
    val logoSizeDp = with(density) { 100.toDp() }

    val is80mm = (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
    val receiptFontSize = if (is80mm) 9.sp else 12.sp
    val receiptLineHeight = if (is80mm) 13.sp else 16.sp
    // Company/Station Name: clearly larger than body text (12sp / 10.5sp) but moderate, balanced and proportionate
    val companyFontSize = if (is80mm) 14.sp else 16.sp

    val (displayCompanyName, remainingReceiptBody) = remember(receiptText, companyName) {
        parseReceiptForPreview(receiptText, companyName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("receipt_preview_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.get(Localization.Key.RECEIPT_TITLE, language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Realistic Thermal Paper Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(350.dp)
                        .background(Color(0xFFFFFDF8), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFDCD6CD), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Logo: Always square 100 x 100 px, centered, aspect-ratio preserved, no "[Logo]" text
                        Box(
                            modifier = Modifier
                                .size(logoSizeDp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1EDE6))
                                .border(1.dp, Color(0xFFD6CFC4), RoundedCornerShape(6.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CompanyLogoView(
                                logoUri = logoUri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Company/Station Name: dynamically from Settings, ~3x larger, center aligned, multi-line preserved
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            displayCompanyName.lines().forEach { line ->
                                if (line.isNotBlank()) {
                                    Text(
                                        text = line.trim(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = companyFontSize,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            lineHeight = companyFontSize * 1.25f
                                        ),
                                        color = Color(0xFF111111),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Body of Receipt (branch, address, phone, metadata, aligned item table, totals, full paid, footer note)
                        ReceiptFormattedBody(
                            bodyText = remainingReceiptBody,
                            is80mm = is80mm,
                            receiptFontSize = receiptFontSize,
                            receiptLineHeight = receiptLineHeight,
                            textColor = Color(0xFF1A1A1A),
                            separatorColor = Color(0xFF555555),
                            language = language
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons: Arranged vertically, one above the other
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: Print to Bluetooth ESC/POS
                    Button(
                        onClick = onPrintBluetooth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_print_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Localization.get(Localization.Key.BTN_PRINT_BLUETOOTH, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Button 2: Close / Dismiss
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_close_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            Localization.get(Localization.Key.BTN_CLOSE, language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Secondary Utility Actions: Copy, Share, PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Receipt", receiptText))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "bn") "কপি করুন" else "Copy", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                                putExtra(Intent.EXTRA_SUBJECT, "Invoice Receipt")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "bn") "শেয়ার করুন" else "Share", fontSize = 12.sp)
                        }

                        if (onSharePdf != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onSharePdf,
                                modifier = Modifier.testTag("preview_dialog_share_pdf_btn")
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == "bn") "পিডিএফ" else "PDF", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts company/station name for enlarged 3x rendering, separating it from the rest of receipt text.
 */
private fun parseReceiptForPreview(receiptText: String, configuredName: String?): Pair<String, String> {
    val cleanConfigName = configuredName?.trim()?.ifBlank { null }
    val lines = receiptText.lines()
    if (lines.isEmpty()) {
        return (cleanConfigName ?: "POS STATION") to receiptText
    }

    val firstSepIdx = lines.indexOfFirst { it.trim().startsWith("=") || it.trim().startsWith("-") }
    val hasLeadingSep = firstSepIdx in 0..1

    val startIdx = if (hasLeadingSep) firstSepIdx + 1 else 0
    val endHeaderIdx = if (hasLeadingSep) {
        val secondSep = lines.drop(startIdx).indexOfFirst { it.trim().startsWith("=") || it.trim().startsWith("-") }
        if (secondSep != -1) startIdx + secondSep else lines.size
    } else {
        val nextSep = lines.indexOfFirst { it.trim().startsWith("-") || it.trim().startsWith("=") }
        if (nextSep != -1) nextSep else lines.size
    }

    val headerLines = lines.subList(startIdx, endHeaderIdx)

    if (cleanConfigName != null) {
        val width = if ((lines.firstOrNull()?.length ?: 32) >= 40) {
            ThermalReceiptFormatter.WIDTH_80MM
        } else {
            ThermalReceiptFormatter.WIDTH_58MM
        }
        val expectedCompanyLines = cleanConfigName.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { ThermalReceiptFormatter.wrapLinesForWidth(it, width) }
        var matchedCount = 0
        for (line in headerLines) {
            val t = line.trim()
            if (t.isBlank()) continue
            if (matchedCount < expectedCompanyLines.size &&
                t.equals(expectedCompanyLines[matchedCount], ignoreCase = true)
            ) {
                matchedCount++
            } else {
                break
            }
        }
        val skipCount = if (matchedCount > 0) {
            matchedCount
        } else {
            minOf(expectedCompanyLines.size.coerceAtLeast(1), headerLines.size)
        }
        val remaining = lines.subList(startIdx + skipCount, lines.size).joinToString("\n")
        return cleanConfigName to remaining
    }

    // Default: find company name line(s) before branch/address/metadata
    val nameCandidates = mutableListOf<String>()
    var splitAt = startIdx
    for (i in startIdx until endHeaderIdx) {
        val l = lines[i].trim()
        if (l.startsWith("Branch") || l.startsWith("শাখা") || l.startsWith("Phone") || l.startsWith("ফোন") ||
            l.startsWith("Date:") || l.startsWith("Order ID:") || l.startsWith("Operator:") || l.startsWith("Customer:")
        ) {
            splitAt = i
            break
        }
        if (l.isNotBlank()) {
            nameCandidates.add(l)
            splitAt = i + 1
        }
    }

    val title = if (nameCandidates.isNotEmpty()) nameCandidates.joinToString("\n") else "POS STATION"
    val remaining = lines.subList(splitAt, lines.size).joinToString("\n")
    return title to remaining
}

internal data class VoucherItemTableRow(
    val itemName: String,
    val qty: String,
    val unit: String,
    val rate: String,
    val total: String
)

internal data class VoucherSummaryRow(
    val label: String,
    val amount: String,
    val isBold: Boolean = false
)

internal data class ParsedVoucherTable(
    val isFiveColumn: Boolean,
    val separatorWidth: Int,
    val rows: List<VoucherItemTableRow>,
    val summaryRows: List<VoucherSummaryRow> = emptyList()
)

internal data class ParsedReceiptBodySections(
    val preTableText: String,
    val table: ParsedVoucherTable,
    val postTableText: String
)

@Composable
internal fun ReceiptFormattedBody(
    bodyText: String,
    is80mm: Boolean,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color,
    explicitLines: List<InvoiceLineEntity> = emptyList(),
    language: String = "en"
) {
    val parsedSections = remember(bodyText, explicitLines, language) {
        parseReceiptSectionsForPreview(bodyText, explicitLines, language)
    }

    if (parsedSections != null) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val sepWidth = parsedSections.table.separatorWidth
        val measuredSlipWidthDp = remember(sepWidth, receiptFontSize, density) {
            val widthPx = textMeasurer.measure(
                text = "=".repeat(sepWidth),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = receiptFontSize
                ),
                softWrap = false,
                maxLines = 1
            ).size.width
            with(density) { widthPx.toDp() }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val tableWidth = if (measuredSlipWidthDp >= 180.dp) {
                minOf(maxWidth, measuredSlipWidthDp)
            } else {
                maxWidth
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (parsedSections.preTableText.isNotBlank()) {
                    ReceiptPreTableSection(
                        preTableText = parsedSections.preTableText,
                        receiptFontSize = receiptFontSize,
                        receiptLineHeight = receiptLineHeight,
                        textColor = textColor,
                        separatorColor = separatorColor
                    )
                }

                VoucherItemTable(
                    table = parsedSections.table,
                    is80mm = is80mm,
                    receiptFontSize = receiptFontSize,
                    receiptLineHeight = receiptLineHeight,
                    textColor = textColor,
                    separatorColor = separatorColor,
                    language = language,
                    modifier = Modifier.width(tableWidth)
                )

                if (parsedSections.postTableText.isNotBlank()) {
                    ReceiptBodyWithFullPaidBadge(
                        text = parsedSections.postTableText.trimStart('\n'),
                        receiptFontSize = receiptFontSize,
                        receiptLineHeight = receiptLineHeight,
                        textColor = textColor,
                        separatorColor = separatorColor,
                        language = language
                    )
                }
            }
        }
    } else {
        ReceiptBodyWithFullPaidBadge(
            text = bodyText,
            receiptFontSize = receiptFontSize,
            receiptLineHeight = receiptLineHeight,
            textColor = textColor,
            separatorColor = separatorColor,
            language = language
        )
    }
}

@Composable
private fun ReceiptPreTableSection(
    preTableText: String,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color
) {
    val allLines = preTableText.trimEnd().lines()
    val metadataLineIdx = allLines.indexOfFirst { line ->
        val t = line.trim()
        t.startsWith("Date:", ignoreCase = true) ||
            t.startsWith("Order ID:", ignoreCase = true) ||
            t.startsWith("Customer:", ignoreCase = true) ||
            t.startsWith("Operator:", ignoreCase = true) ||
            t.startsWith("তারিখ:", ignoreCase = true) ||
            t.startsWith("অর্ডার আইডি:", ignoreCase = true) ||
            t.startsWith("ক্রেতা:", ignoreCase = true) ||
            t.startsWith("অপারেটর:", ignoreCase = true)
    }
    val headerEndIdx = if (metadataLineIdx != -1) {
        if (metadataLineIdx > 0 && isMajorSeparatorLine(allLines[metadataLineIdx - 1])) {
            metadataLineIdx - 1
        } else {
            metadataLineIdx
        }
    } else {
        allLines.size
    }

    val headerRegionLines = allLines.subList(0, headerEndIdx)
    val metadataRegionLines = allLines.subList(headerEndIdx, allLines.size)

    if (headerRegionLines.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voucher_header_details"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (line in headerRegionLines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed)) {
                    Text(
                        text = trimmed,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        softWrap = false,
                        color = separatorColor
                    )
                } else {
                    Text(
                        text = trimmed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        }
    }

    val metadataText = metadataRegionLines.joinToString("\n").trimEnd()
    if (metadataText.isNotBlank()) {
        Text(
            text = metadataText,
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = textColor
        )
    }
}

@Composable
private fun ReceiptBodyWithFullPaidBadge(
    text: String,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color = textColor,
    language: String = "en"
) {
    val allLines = text.trimEnd().lines()

    val fullPaidLineIdx = allLines.indexOfFirst {
        it.contains("FULL PAID") || it.contains("সম্পূর্ণ পরিশোধিত") || it.contains("✓")
    }

    val sepBeforeFullPaidIdx = if (fullPaidLineIdx != -1) {
        (fullPaidLineIdx - 1 downTo 0).firstOrNull { isMajorSeparatorLine(allLines[it]) || isMinorSeparatorLine(allLines[it]) }
    } else null

    val splitIdx = sepBeforeFullPaidIdx ?: if (fullPaidLineIdx != -1) fullPaidLineIdx else null

    if (splitIdx != null) {
        val totalsText = if (sepBeforeFullPaidIdx != null) {
            allLines.subList(0, sepBeforeFullPaidIdx + 1).joinToString("\n").trimEnd()
        } else {
            allLines.subList(0, fullPaidLineIdx).joinToString("\n").trimEnd()
        }
        val footerRegionLines = if (sepBeforeFullPaidIdx != null) {
            allLines.subList(sepBeforeFullPaidIdx + 1, allLines.size)
        } else {
            allLines.subList(fullPaidLineIdx, allLines.size)
        }

        if (totalsText.isNotBlank()) {
            Text(
                text = totalsText,
                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                fontFamily = FontFamily.Monospace,
                fontSize = receiptFontSize,
                lineHeight = receiptLineHeight,
                softWrap = false,
                color = textColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voucher_footer_section"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (line in footerRegionLines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                when {
                    trimmed.contains("FULL PAID") || trimmed.contains("সম্পূর্ণ পরিশোধিত") || trimmed.contains("✓") -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Localization.get(Localization.Key.VOUCHER_FULL_PAID_BADGE, language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed) -> {
                        Text(
                            text = trimmed,
                            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                            fontFamily = FontFamily.Monospace,
                            fontSize = receiptFontSize,
                            lineHeight = receiptLineHeight,
                            softWrap = false,
                            color = separatorColor
                        )
                    }
                    else -> {
                        Text(
                            text = trimmed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = receiptFontSize,
                            lineHeight = receiptLineHeight,
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                }
            }
        }
    } else {
        Text(
            text = text,
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = textColor
        )
    }
}

@Composable
private fun VoucherItemTable(
    table: ParsedVoucherTable,
    is80mm: Boolean,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color,
    language: String = "en",
    modifier: Modifier = Modifier
) {
    val cellFontSize = if (is80mm) receiptFontSize else 10.5.sp
    val cellLineHeight = if (is80mm) receiptLineHeight else 14.sp
    val minorSeparator = "-".repeat(table.separatorWidth)

    Column(
        modifier = modifier.testTag("voucher_item_table"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Table Column Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .testTag("voucher_item_table_header"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (table.isFiveColumn) {
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_DESCRIPTION, language),
                    modifier = Modifier.weight(1.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_QTY, language),
                    modifier = Modifier.weight(0.60f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_UNIT, language),
                    modifier = Modifier.weight(0.60f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_RATE, language),
                    modifier = Modifier.weight(0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_TOTAL, language),
                    modifier = Modifier.weight(0.95f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
            } else {
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_ITEM_NAME, language),
                    modifier = Modifier.weight(1.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_QTY, language),
                    modifier = Modifier.weight(0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_RATE, language),
                    modifier = Modifier.weight(0.90f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = Localization.get(Localization.Key.VOUCHER_TOTAL, language),
                    modifier = Modifier.weight(1.00f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
            }
        }

        // Top Table Minor Separator
        Text(
            text = minorSeparator,
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = separatorColor
        )

        // Item Rows
        table.rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("voucher_item_row_$index"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (table.isFiveColumn) {
                    Text(
                        text = row.itemName,
                        modifier = Modifier
                            .weight(1.85f)
                            .testTag("voucher_item_name_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        color = textColor
                    )
                    Text(
                        text = row.qty,
                        modifier = Modifier
                            .weight(0.60f)
                            .testTag("voucher_item_qty_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.qty.length > 5) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = row.unit,
                        modifier = Modifier
                            .weight(0.60f)
                            .testTag("voucher_item_unit_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.unit.length > 5) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = row.rate,
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("voucher_item_rate_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.rate.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = row.total,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("voucher_item_total_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.total.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                } else {
                    Text(
                        text = row.itemName,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("voucher_item_name_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        color = textColor
                    )
                    Text(
                        text = row.qty,
                        modifier = Modifier
                            .weight(0.75f)
                            .testTag("voucher_item_qty_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.qty.length > 6) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = row.rate,
                        modifier = Modifier
                            .weight(0.90f)
                            .testTag("voucher_item_rate_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.rate.length > 7) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = row.total,
                        modifier = Modifier
                            .weight(1.00f)
                            .testTag("voucher_item_total_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (row.total.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                }
            }
        }

        // Bottom Table Minor Separator
        Text(
            text = minorSeparator,
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = separatorColor
        )

        // Summary Rows (Sub Total & TOTAL) rendered in the item table columns
        table.summaryRows.forEachIndexed { sIdx, sRow ->
            val tag = if (sRow.isBold) "voucher_total_row" else "voucher_subtotal_row"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .testTag(tag),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (table.isFiveColumn) {
                    Text(
                        text = sRow.label,
                        modifier = Modifier
                            .weight(1.85f)
                            .testTag("${tag}_label"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.weight(0.60f))
                    Spacer(modifier = Modifier.weight(0.60f))
                    Spacer(modifier = Modifier.weight(0.85f))
                    Text(
                        text = sRow.amount,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("${tag}_amount"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        color = textColor
                    )
                } else {
                    Text(
                        text = sRow.label,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("${tag}_label"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.weight(0.75f))
                    Spacer(modifier = Modifier.weight(0.90f))
                    Text(
                        text = sRow.amount,
                        modifier = Modifier
                            .weight(1.00f)
                            .testTag("${tag}_amount"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        color = textColor
                    )
                }
            }
        }
    }
}

private val DETAIL_58MM_REGEX = Regex(
    """^\s+(.+?)\s+x\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*(?:BDT)?\s*$"""
)

private val SINGLE_58MM_REGEX = Regex(
    """^(.+?)\s+(\S+)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private val ROW_80MM_REGEX = Regex(
    """^(.+?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+(\S+(?:\s+\S+)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private val ROW_80MM_NO_UNIT_REGEX = Regex(
    """^(.+?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s{2,}([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private fun isMajorSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= 16 && trimmed.all { it == '=' }
}

private fun isMinorSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= 16 && trimmed.all { it == '-' }
}

private fun extractAmountFromSummaryLine(line: String): String {
    val tokens = line.trim().split(Regex("\\s+"))
    return when {
        tokens.isEmpty() -> ""
        tokens.last().equals("BDT", ignoreCase = true) && tokens.size > 1 -> tokens[tokens.size - 2]
        else -> tokens.last()
    }
}

internal fun parseReceiptSectionsForPreview(
    bodyText: String,
    explicitLines: List<InvoiceLineEntity> = emptyList(),
    language: String = "en"
): ParsedReceiptBodySections? {
    val allLines = bodyText.lines()
    val tableHeaderIdx = allLines.indexOfFirst { line ->
        val t = line.trim()
        val hasNameOrDesc = t.startsWith("Item Name") || t.startsWith("Item Description") ||
                t.startsWith("পণ্যের নাম") || t.startsWith("বিবরণ")
        val hasQty = t.contains("Qty") || t.contains("পরিমাণ")
        val hasRate = t.contains("Rate") || t.contains("দর")
        val hasTotal = t.contains("Total") || t.contains("মোট")
        hasNameOrDesc && hasQty && hasRate && hasTotal
    }
    if (tableHeaderIdx == -1) return null

    val headerLine = allLines[tableHeaderIdx].trim()
    val isFiveColumn = headerLine.startsWith("Item Description") || headerLine.startsWith("বিবরণ")
    val topSepIdx = if (tableHeaderIdx + 1 < allLines.size && isMinorSeparatorLine(allLines[tableHeaderIdx + 1])) {
        tableHeaderIdx + 1
    } else {
        tableHeaderIdx
    }
    val bottomSepIdx = ((topSepIdx + 1) until allLines.size).firstOrNull { idx ->
        isMinorSeparatorLine(allLines[idx])
    } ?: return null

    val sepWidth = if (topSepIdx > tableHeaderIdx) {
        allLines[topSepIdx].trim().length
    } else if (isFiveColumn) {
        ThermalReceiptFormatter.WIDTH_80MM
    } else {
        ThermalReceiptFormatter.WIDTH_58MM
    }

    val preTableText = allLines.subList(0, tableHeaderIdx).joinToString("\n")
    val rawItemLines = allLines.subList(topSepIdx + 1, bottomSepIdx)

    // Parse summary rows (Subtotal and TOTAL) following bottom separator
    val summaryRows = mutableListOf<VoucherSummaryRow>()
    var postTableStartIdx = bottomSepIdx + 1

    for (idx in (bottomSepIdx + 1) until allLines.size) {
        val line = allLines[idx]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            postTableStartIdx = idx + 1
            continue
        }

        val isSubTotal = trimmed.startsWith("Sub Total", ignoreCase = true) || trimmed.startsWith("উপমোট")
        val isTotal = trimmed.startsWith("TOTAL", ignoreCase = true) ||
                trimmed.startsWith("NET PAYABLE", ignoreCase = true) ||
                trimmed.startsWith("মোট") ||
                trimmed.startsWith("সর্বমোট")

        if (isSubTotal) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val isBn = language.equals("bn", ignoreCase = true) || trimmed.startsWith("উপমোট")
            val label = if (isBn) Localization.get(Localization.Key.VOUCHER_SUBTOTAL, "bn") else Localization.get(Localization.Key.VOUCHER_SUBTOTAL, "en")
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = false))
            postTableStartIdx = idx + 1
        } else if (isTotal) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val isBn = language.equals("bn", ignoreCase = true) || trimmed.startsWith("মোট") || trimmed.startsWith("সর্বমোট")
            val label = if (isBn) Localization.get(Localization.Key.VOUCHER_TOTAL_SUMMARY, "bn") else Localization.get(Localization.Key.VOUCHER_TOTAL_SUMMARY, "en")
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = true))
            postTableStartIdx = idx + 1
        } else {
            // Reached non-summary line (FULL PAID, separator, or footer)
            break
        }
    }

    val postTableText = allLines.subList(postTableStartIdx, allLines.size).joinToString("\n")

    val rows = if (explicitLines.isNotEmpty()) {
        explicitLines.map { item ->
            val rateStr = FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            val totalStr = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            if (isFiveColumn) {
                VoucherItemTableRow(
                    itemName = item.itemName,
                    qty = item.quantityStr.trim(),
                    unit = item.unit.trim().take(5),
                    rate = rateStr,
                    total = totalStr
                )
            } else {
                VoucherItemTableRow(
                    itemName = item.itemName,
                    qty = "${item.quantityStr.trim()}${item.unit.trim().take(2)}".trim(),
                    unit = "",
                    rate = rateStr,
                    total = totalStr
                )
            }
        }
    } else if (isFiveColumn) {
        parse80mmItemLines(rawItemLines)
    } else {
        parse58mmItemLines(rawItemLines)
    }

    return ParsedReceiptBodySections(
        preTableText = preTableText,
        table = ParsedVoucherTable(
            isFiveColumn = isFiveColumn,
            separatorWidth = sepWidth,
            rows = rows,
            summaryRows = summaryRows
        ),
        postTableText = postTableText
    )
}

private fun parse58mmItemLines(rawItemLines: List<String>): List<VoucherItemTableRow> {
    val rows = mutableListOf<VoucherItemTableRow>()
    val pendingNameLines = mutableListOf<String>()

    for (i in rawItemLines.indices) {
        val line = rawItemLines[i]
        if (line.isBlank()) continue

        val detailMatch = DETAIL_58MM_REGEX.matchEntire(line)
        if (detailMatch != null) {
            val fullName = if (pendingNameLines.isNotEmpty()) {
                pendingNameLines.joinToString(" ") { it.trim() }
            } else {
                "Item"
            }
            pendingNameLines.clear()
            rows.add(
                VoucherItemTableRow(
                    itemName = fullName,
                    qty = detailMatch.groupValues[1].trim(),
                    unit = "",
                    rate = detailMatch.groupValues[2].trim(),
                    total = detailMatch.groupValues[3].trim()
                )
            )
            continue
        }

        val singleMatch = SINGLE_58MM_REGEX.matchEntire(line)
        val nextIsDetail = (i + 1 < rawItemLines.size) && DETAIL_58MM_REGEX.matches(rawItemLines[i + 1])
        if (singleMatch != null && pendingNameLines.isEmpty() && !nextIsDetail) {
            val candidateName = singleMatch.groupValues[1].trim()
            if (ThermalReceiptFormatter.getVisualWidth(candidateName) <= 12) {
                rows.add(
                    VoucherItemTableRow(
                        itemName = candidateName,
                        qty = singleMatch.groupValues[2].trim(),
                        unit = "",
                        rate = singleMatch.groupValues[3].trim(),
                        total = singleMatch.groupValues[4].trim()
                    )
                )
                continue
            }
        }

        pendingNameLines.add(line.trim())
    }

    if (pendingNameLines.isNotEmpty()) {
        for (pending in pendingNameLines) {
            val fallbackMatch = SINGLE_58MM_REGEX.matchEntire(pending)
            if (fallbackMatch != null) {
                rows.add(
                    VoucherItemTableRow(
                        itemName = fallbackMatch.groupValues[1].trim(),
                        qty = fallbackMatch.groupValues[2].trim(),
                        unit = "",
                        rate = fallbackMatch.groupValues[3].trim(),
                        total = fallbackMatch.groupValues[4].trim()
                    )
                )
            } else {
                rows.add(
                    VoucherItemTableRow(
                        itemName = pending,
                        qty = "",
                        unit = "",
                        rate = "",
                        total = ""
                    )
                )
            }
        }
    }

    return rows
}

private fun parse80mmItemLines(rawItemLines: List<String>): List<VoucherItemTableRow> {
    val rows = mutableListOf<VoucherItemTableRow>()

    for (line in rawItemLines) {
        if (line.isBlank()) continue

        val match = ROW_80MM_REGEX.matchEntire(line)
        if (match != null) {
            rows.add(
                VoucherItemTableRow(
                    itemName = match.groupValues[1].trim(),
                    qty = match.groupValues[2].trim(),
                    unit = match.groupValues[3].trim(),
                    rate = match.groupValues[4].trim(),
                    total = match.groupValues[5].trim()
                )
            )
            continue
        }

        val noUnitMatch = ROW_80MM_NO_UNIT_REGEX.matchEntire(line)
        if (noUnitMatch != null) {
            rows.add(
                VoucherItemTableRow(
                    itemName = noUnitMatch.groupValues[1].trim(),
                    qty = noUnitMatch.groupValues[2].trim(),
                    unit = "",
                    rate = noUnitMatch.groupValues[3].trim(),
                    total = noUnitMatch.groupValues[4].trim()
                )
            )
            continue
        }

        if (rows.isNotEmpty()) {
            val lastIdx = rows.lastIndex
            val prev = rows[lastIdx]
            rows[lastIdx] = prev.copy(
                itemName = "${prev.itemName} ${line.trim()}".trim()
            )
        } else {
            rows.add(
                VoucherItemTableRow(
                    itemName = line.trim(),
                    qty = "",
                    unit = "",
                    rate = "",
                    total = ""
                )
            )
        }
    }

    return rows
}

