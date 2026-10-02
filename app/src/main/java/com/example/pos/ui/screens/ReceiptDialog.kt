package com.example.pos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.Localization
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
                        // Top Border Separator
                        val sepWidth = if (is80mm) 48 else 32
                        Text(
                            text = "=".repeat(sepWidth),
                            fontFamily = FontFamily.Monospace,
                            fontSize = receiptFontSize,
                            softWrap = false,
                            color = Color(0xFF555555)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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

                        // Body of Receipt (branch, address, phone, metadata, items, totals, full paid, footer note)
                        val fullPaidMarker = "[✓] FULL PAID"
                        val hasFullPaidInBody = remainingReceiptBody.contains(fullPaidMarker)

                        if (hasFullPaidInBody) {
                            val parts = remainingReceiptBody.split(fullPaidMarker)
                            val preText = parts.getOrNull(0) ?: ""
                            val postText = parts.getOrNull(1) ?: ""

                            if (preText.isNotBlank()) {
                                Text(
                                    text = preText.trimEnd(),
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = receiptFontSize,
                                    lineHeight = receiptLineHeight,
                                    softWrap = false,
                                    color = Color(0xFF1A1A1A)
                                )
                            }

                            // Prominent Green Check/Tick & Full Paid Badge
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
                                    text = "Full Paid",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            if (postText.isNotBlank()) {
                                Text(
                                    text = postText.trimStart(),
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = receiptFontSize,
                                    lineHeight = receiptLineHeight,
                                    softWrap = false,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                        } else {
                            Text(
                                text = remainingReceiptBody,
                                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                fontFamily = FontFamily.Monospace,
                                fontSize = receiptFontSize,
                                lineHeight = receiptLineHeight,
                                softWrap = false,
                                color = Color(0xFF1A1A1A)
                            )
                        }
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

    val firstSepIdx = lines.indexOfFirst { it.trim().startsWith("=") }
    if (firstSepIdx == -1) {
        return (cleanConfigName ?: "POS STATION") to receiptText
    }

    val secondSepIdx = lines.drop(firstSepIdx + 1).indexOfFirst { it.trim().startsWith("=") }
    val endHeaderIdx = if (secondSepIdx != -1) firstSepIdx + 1 + secondSepIdx else lines.size

    val headerLines = lines.subList(firstSepIdx + 1, endHeaderIdx)

    if (cleanConfigName != null) {
        val nameLines = cleanConfigName.lines().map { it.trim() }.filter { it.isNotBlank() }
        var matchedCount = 0
        for (line in headerLines) {
            val t = line.trim()
            if (nameLines.any { it.contains(t) || t.contains(it) }) {
                matchedCount++
            } else if (matchedCount > 0) {
                break
            }
        }
        val skipCount = maxOf(matchedCount, 1)
        val remaining = lines.subList(firstSepIdx + 1 + skipCount, lines.size).joinToString("\n")
        return cleanConfigName to remaining
    }

    // Default: find company name line(s) before branch/address
    val nameCandidates = mutableListOf<String>()
    var splitAt = firstSepIdx + 1
    for (i in (firstSepIdx + 1) until endHeaderIdx) {
        val l = lines[i].trim()
        if (l.startsWith("Branch") || l.startsWith("শাখা") || l.startsWith("Phone") || l.startsWith("ফোন")) {
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
